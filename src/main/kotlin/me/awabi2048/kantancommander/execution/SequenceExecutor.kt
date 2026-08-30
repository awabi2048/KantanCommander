package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.PlacementStore
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.MAX_BLOCK_OPERATION_VOLUME
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.SavedPosition
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.WorldVariableValue
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.DisplayTextTiming
import me.awabi2048.kantancommander.model.TICKS_PER_SECOND
import me.awabi2048.kantancommander.model.effectiveContextSource
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.SoundCategory
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Duration
import java.util.UUID
import java.util.logging.Level
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.kantancommander.item.ItemStackCodec
import org.bukkit.inventory.EquipmentSlot

class SequenceExecutor(private val plugin: KantanCommanderPlugin) {
    private val timedActionBar = TimedActionBarService(plugin)

    fun execute(scriptId: UUID, origin: Location, actor: Player? = null, callback: (Boolean) -> Unit = {}) {
        val worldData = if (plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) {
            MyWorldManagerApi.getWorldRepository()?.findByWorldName(origin.world.name)
        } else null
        if (worldData == null) {
            plugin.logger.warning("[KantanCommander] rejected disk=$scriptId reason=outside_myworld world=${origin.world.name}")
            return callback(false)
        }
        val script = plugin.scripts.load(scriptId) ?: return callback(false)
        val validationErrors = ExecutableScriptValidator.validate(script, plugin.graphLimits())
        if (validationErrors.isNotEmpty()) {
            plugin.logger.warning(
                "[KantanCommander] rejected disk=$scriptId reason=invalid_script errors=${validationErrors.joinToString(" | ")}"
            )
            return callback(false)
        }
        // 同一ディスクの再トリガー（タイマーとレッドストーンの同時到来、WAIT中の再起動など）は
        // 実行可能な範囲で並行許可する。各起動は独立したExecutionSessionを持つため干渉しない。
        val session = ExecutionSession(
            rootId = scriptId,
            origin = origin.clone(),
            actor = actor,
            budget = plugin.config.getInt("execution.max-nodes-per-activation"),
            maxDepth = plugin.config.getInt("execution.max-disk-call-depth"),
            worldId = worldData.uuid,
        )
        plugin.logger.info("[KantanCommander] start disk=$scriptId world=${origin.world.name} location=${origin.blockX},${origin.blockY},${origin.blockZ}")
        runGraph(script, script.graph, session, 0) { success ->
            plugin.logger.info("[KantanCommander] finish disk=$scriptId success=$success executed=${session.executed}/${session.budget}")
            callback(success)
        }
    }

    private fun runGraph(
        script: DiskScript,
        graph: CommandGraph,
        session: ExecutionSession,
        depth: Int,
        done: (Boolean) -> Unit,
    ) {
        runNode(script, graph, graph.entryNodeId, session, depth, done)
    }

    private fun runNode(
        script: DiskScript,
        graph: CommandGraph,
        nodeId: UUID?,
        session: ExecutionSession,
        depth: Int,
        done: (Boolean) -> Unit,
    ) {
        if (nodeId == null) return done(true)
        val node = graph.nodes[nodeId] ?: return stop(session, script, nodeId, depth, "missing_node", done)
        if (node.type == CommandType.FOR_START && node.trueNext == node.pairedNodeId) {
            val after = node.pairedNodeId?.let(graph.nodes::get)?.next
            return runNode(script, graph, after, session, depth, done)
        }
        if (!ExecutionSemantics.withinBudget(session.executed, session.budget)) {
            return stop(session, script, nodeId, depth, "command_limit", done)
        }
        session.executed++
        plugin.logger.info("[KantanCommander] execute root=${session.rootId} disk=${script.id} node=${node.id} type=${node.type} count=${session.executed}/${session.budget}")

        val next: (UUID?, Boolean) -> Unit = { target, success ->
            if (success) runNode(script, graph, target, session, depth, done)
            else stop(session, script, node.id, depth, "node_failed", done)
        }
        when (node.type) {
            CommandType.WAIT -> {
                // 保存・入力値は秒を正本とし、Minecraftのスケジューラ境界でだけtickへ変換します。
                val seconds = node.int("seconds", 1).coerceAtLeast(1).toLong()
                val waitTicks = seconds * TICKS_PER_SECOND.toLong()
                plugin.server.scheduler.runTaskLater(
                    plugin,
                    Runnable { next(node.next, true) },
                    waitTicks,
                )
            }
            CommandType.CONDITION -> {
                val effectiveContext = effectiveContext(node, session)
                val rawResult = evaluateCondition(
                    node,
                    session,
                    effectiveContext,
                )
                val result = ExecutionSemantics.conditionResult(rawResult, node.boolean("inverted"))
                session.previousContext = effectiveContext
                plugin.logger.info("[KantanCommander] condition disk=${script.id} node=${node.id} result=$result")
                next(if (result) node.trueNext else node.falseNext, true)
            }
            CommandType.CONTEXT -> {
                session.context = effectiveContext(node, session)
                session.previousContext = session.context
                next(node.next, true)
            }
            CommandType.DISK_CALL -> {
                val callerContext = session.context
                val callerPrevious = session.previousContext
                session.context = effectiveContext(node, session)
                runDiskCall(node, session, depth) { success ->
                    session.context = callerContext
                    session.previousContext = callerPrevious
                    next(node.next, success)
                }
            }
            CommandType.VARIABLE -> {
                // VARIABLEはノード自身のコンテキスト上書きを持たず、現在の実行文脈だけを使います。
                // 対象・位置の取得を値操作へ直接追加すると、CONTEXTコマンドとの責務境界が崩れ、
                // GUIだけでなくエクスポート時の実行順序も別仕様になってしまいます。
                val inheritedContext = session.context
                val success = executeVariable(
                    node,
                    session,
                    inheritedContext,
                )
                if (success) session.previousContext = inheritedContext
                next(node.next, success)
            }
            CommandType.MERGE -> next(node.next, true)
            CommandType.FOR_START -> beginFor(script, graph, node, session, depth, done)
            CommandType.FOR_END -> finishForIteration(script, graph, node, session, depth, done)
            CommandType.BREAK -> breakFor(script, graph, node, session, depth, done)
            CommandType.CONTINUE -> continueFor(script, graph, node, session, depth, done)
            else -> next(node.next, executeImmediate(node, session))
        }
    }

    private fun beginFor(
        script: DiskScript,
        graph: CommandGraph,
        node: CommandNode,
        session: ExecutionSession,
        depth: Int,
        done: (Boolean) -> Unit,
    ) {
        val endId = node.pairedNodeId ?: return stop(session, script, node.id, depth, "missing_for_end", done)
        val start = resolveForValue(node, "start", session) ?: return stop(session, script, node.id, depth, "invalid_for_start", done)
        val end = resolveForValue(node, "end", session) ?: return stop(session, script, node.id, depth, "invalid_for_end", done)
        val step = resolveForValue(node, "step", session) ?: return stop(session, script, node.id, depth, "invalid_for_step", done)
        if (step == 0L) return stop(session, script, node.id, depth, "zero_for_step", done)
        if (!ExecutionSemantics.withinForRange(start, end, step, node.boolean("inclusiveEnd", true))) {
            plugin.logger.info("[KantanCommander] for-finish root=${session.rootId} disk=${script.id} node=${node.id} reason=zero_iterations")
            return runNode(script, graph, graph.nodes[endId]?.next, session, depth, done)
        }
        session.loops += LoopFrame(node.id, endId, start, end, step, 1, session.context)
        session.currentIterationValue = start
        session.currentLoopCount = 1
        runNode(script, graph, node.trueNext, session, depth, done)
    }

    private fun finishForIteration(
        script: DiskScript,
        graph: CommandGraph,
        node: CommandNode,
        session: ExecutionSession,
        depth: Int,
        done: (Boolean) -> Unit,
    ) {
        val frame = session.loops.lastOrNull { it.endId == node.id }
            ?: return stop(session, script, node.id, depth, "for_frame_missing", done)
        val startNode = graph.nodes[frame.startId]
            ?: return stop(session, script, node.id, depth, "for_start_missing", done)
        val currentEnd = resolveForValue(startNode, "end", session)
            ?: return stop(session, script, node.id, depth, "invalid_for_end", done)
        val currentStep = resolveForValue(startNode, "step", session)
            ?: return stop(session, script, node.id, depth, "invalid_for_step", done)
        if (currentStep == 0L) return stop(session, script, node.id, depth, "zero_for_step", done)
        val nextValue = ExecutionSemantics.nextForValue(frame.value, currentStep)
            ?: return stop(session, script, node.id, depth, "for_overflow", done)
        session.context = frame.startContext
        if (ExecutionSemantics.withinForRange(
                nextValue,
                currentEnd,
                currentStep,
                startNode.boolean("inclusiveEnd", true),
            )
        ) {
            frame.value = nextValue
            frame.end = currentEnd
            frame.step = currentStep
            frame.count++
            session.currentIterationValue = frame.value
            session.currentLoopCount = frame.count
            runNode(script, graph, startNode.trueNext, session, depth, done)
        } else {
            plugin.logger.info("[KantanCommander] for-finish root=${session.rootId} disk=${script.id} node=${frame.startId} reason=range_complete iterations=${frame.count}")
            session.loops.remove(frame)
            session.currentIterationValue = session.loops.lastOrNull()?.value
            session.currentLoopCount = session.loops.lastOrNull()?.count
            runNode(script, graph, node.next, session, depth, done)
        }
    }

    private fun breakFor(
        script: DiskScript,
        graph: CommandGraph,
        node: CommandNode,
        session: ExecutionSession,
        depth: Int,
        done: (Boolean) -> Unit,
    ) {
        val frame = session.loops.removeLastOrNull()
            ?: return stop(session, script, node.id, depth, "break_outside_for", done)
        plugin.logger.info("[KantanCommander] for-finish root=${session.rootId} disk=${script.id} node=${frame.startId} reason=break iterations=${frame.count}")
        session.context = frame.startContext
        session.currentIterationValue = session.loops.lastOrNull()?.value
        session.currentLoopCount = session.loops.lastOrNull()?.count
        runNode(script, graph, graph.nodes[frame.endId]?.next, session, depth, done)
    }

    private fun continueFor(
        script: DiskScript,
        graph: CommandGraph,
        node: CommandNode,
        session: ExecutionSession,
        depth: Int,
        done: (Boolean) -> Unit,
    ) {
        val frame = session.loops.lastOrNull()
            ?: return stop(session, script, node.id, depth, "continue_outside_for", done)
        session.context = frame.startContext
        runNode(script, graph, frame.endId, session, depth, done)
    }

    private fun resolveForValue(node: CommandNode, prefix: String, session: ExecutionSession): Long? {
        val source = node.string("${prefix}Source", "FIXED")
        val value = node.string("${prefix}Value", if (prefix == "step") "1" else "0")
        return when (source) {
            "FIXED" -> value.toLongOrNull()
            "TEMPORARY" -> session.temporaryVariables[value]?.integerValue
            // ワールド内変数も参照元として使える（仕様10.2）。未定義・型不一致は整数値なし扱いで強制停止へ。
            "WORLD" -> plugin.variables.get(session.worldId, value)?.integerValue
            else -> null
        }
    }

    private fun runDiskCall(node: CommandNode, session: ExecutionSession, depth: Int, done: (Boolean) -> Unit) {
        if (!ExecutionSemantics.withinCallDepth(depth, session.maxDepth)) {
            plugin.logger.warning("[KantanCommander] disk-call-depth root=${session.rootId} node=${node.id} depth=$depth max=${session.maxDepth}")
            return done(false)
        }
        val snapshot = node.snapshot ?: return done(false)
        val synthetic = DiskScript(name = "snapshot", owner = session.actor?.uniqueId ?: UUID(0, 0), graph = snapshot)
        runGraph(synthetic, snapshot, session, depth + 1, done)
    }

    private fun executeImmediate(node: CommandNode, session: ExecutionSession): Boolean = runCatching {
        val effectiveContext = effectiveContext(node, session)
        val targets = resolveTargets(effectiveContext, node.targetSpec, session)
        val effectiveOrigin = if (effectiveContext?.position != null) {
            resolvePosition(effectiveContext.position, session, effectiveContext) ?: return false
        } else {
            session.origin
        }
        val success = when (node.type) {
            CommandType.TELEPORT -> {
                if (targets.isEmpty()) return false
                val destination = node.destinationTargetSpec
                    ?.let { resolveTargetSpec(it, session, effectiveContext)?.location }
                    ?: node.destinationSpec?.let { resolvePosition(it, session, effectiveContext) }
                    ?: return false
                effectiveContext?.facing?.let { applyFacing(destination, it, effectiveContext, session) }
                targets.all { it.teleport(destination.clone()) }
            }
            CommandType.GIVE_ITEM -> {
                val players = targets.filterIsInstance<Player>()
                if (players.isEmpty()) return false
                val material = Material.matchMaterial(node.string("item")) ?: return false
                val template = ItemStackCodec.decode(node.string("itemData"))
                    ?: ItemStack(material)
                val count = node.int("count", 1)
                if (count < 1) return false
                players.all { player -> giveItem(player, template, count) }
            }
            CommandType.ENTITY_ACTION -> {
                if (targets.isEmpty()) return false
                when (node.string("action", "ride")) {
                    "ride" -> {
                        val vehicle = resolveTarget(effectiveContext, node.secondaryTargetSpec, session)
                            ?: return false
                        targets.all(vehicle::addPassenger)
                    }
                    "dismount" -> targets.all(Entity::leaveVehicle)
                    else -> false
                }
            }
            CommandType.DISPLAY_TEXT -> {
                val players = targets.filterIsInstance<Player>()
                if (players.isEmpty()) return false
                val text = Component.text(node.string("text"))
                when (node.string("mode", "tellraw")) {
                    "title" -> players.forEach { player ->
                        timedActionBar.cancel(player, clear = true)
                        val timing = DisplayTextTiming.from(node)
                        player.showTitle(Title.title(
                                text,
                                Component.empty(),
                                Title.Times.times(
                                    Duration.ofSeconds(timing.fadeInSeconds.coerceAtLeast(0).toLong()),
                                    Duration.ofSeconds(timing.staySeconds.coerceAtLeast(0).toLong()),
                                    Duration.ofSeconds(timing.fadeOutSeconds.coerceAtLeast(0).toLong()),
                                ),
                            ))
                    }
                    "actionbar" -> players.forEach { player ->
                        timedActionBar.show(player, text, DisplayTextTiming.from(node))
                    }
                    else -> players.forEach { player ->
                        timedActionBar.cancel(player, clear = true)
                        player.sendMessage(text)
                    }
                }
                true
            }
            CommandType.SUMMON_ENTITY -> {
                if (!plugin.summonedEntities.canSummon(effectiveOrigin.world.uid)) return false
                val key = NamespacedKey.fromString(node.string("entity")) ?: return false
                val type = Registry.ENTITY_TYPE.get(key) ?: return false
                val spawn = effectiveOrigin.clone()
                effectiveContext?.facing?.let { applyFacing(spawn, it, effectiveContext, session) }
                val entity = effectiveOrigin.world.spawnEntity(spawn, type)
                node.string("tags").split(',').map(String::trim).filter(String::isNotEmpty)
                    .forEach(entity::addScoreboardTag)
                try {
                    plugin.summonedEntities.register(entity, session.rootId)
                } catch (failure: Throwable) {
                    // 台帳へ登録できない召喚体をワールドへ残すと、制限をすり抜けた
                    // 孤児Entityになります。登録と実体生成を一組として扱います。
                    entity.remove()
                    throw failure
                }
                true
            }
            CommandType.PLAY_SOUND -> {
                val sound = node.string("sound")
                if (NamespacedKey.fromString(sound) == null) return false
                val volume = node.double("volume", 1.0).toFloat()
                val pitch = node.double("pitch", 1.0).toFloat()
                // 完全一括再生は各プレイヤー位置から鳴らし、距離減衰で聞こえない参加者を作りません。
                effectiveOrigin.world.players.forEach {
                    it.playSound(it.location, sound, SoundCategory.MASTER, volume, pitch)
                }
                true
            }
            CommandType.APPLY_EFFECT -> {
                val key = NamespacedKey.fromString(node.string("effect")) ?: return false
                val effect = Registry.EFFECT.get(key) ?: return false
                val applicable = targets.filterIsInstance<LivingEntity>()
                if (applicable.isEmpty()) return false
                applicable.forEach {
                    it.addPotionEffect(org.bukkit.potion.PotionEffect(
                        effect,
                        node.int("seconds", 30) * 20,
                        node.int("level", 1) - 1,
                    ))
                }
                true
            }
            CommandType.CAMERA_SHAKE -> {
                targets.filterIsInstance<Player>().forEach {
                    CameraShakeService.apply(
                        plugin,
                        it,
                        node.double("intensity", 1.0).toFloat(),
                        node.double("seconds", 5.0).toFloat(),
                        node.string("shakeType", "positional"),
                    )
                }
                true
            }
            CommandType.EQUIP_ITEM -> {
                val material = Material.matchMaterial(node.string("item")) ?: return false
                val template = ItemStackCodec.decode(node.string("itemData")) ?: ItemStack(material)
                val slot = runCatching { EquipmentSlot.valueOf(node.string("slot")) }.getOrNull() ?: return false
                val applicable = targets.filterIsInstance<LivingEntity>().mapNotNull { it.equipment }
                if (applicable.isEmpty()) return false
                applicable.forEach { it.setItem(slot, template.clone()) }
                true
            }
            CommandType.BLOCK_OPERATION -> executeBlockOperation(node, session, effectiveContext)
            CommandType.ENTITY_DELETE -> {
                val removable = targets.filterNot { PlacementStore.DISPLAY_TAG in it.scoreboardTags }
                if (removable.isEmpty()) return false
                removable.forEach(Entity::remove)
                true
            }
            else -> true
        }
        if (success) session.previousContext = effectiveContext
        success
    }.onFailure {
        plugin.logger.log(Level.WARNING, "[KantanCommander] node failed root=${session.rootId} node=${node.id}", it)
    }.getOrDefault(false)

    private fun giveItem(player: Player, template: ItemStack, count: Int): Boolean {
        var remaining = count
        while (remaining > 0) {
            val stack = template.clone()
            val batch = minOf(remaining, stack.maxStackSize)
            stack.amount = batch
            val overflow = player.inventory.addItem(stack)
            remaining -= batch
            if (overflow.isNotEmpty()) {
                // 付与量が容量を超えた場合は、Minecraftの通常の付与と同じく
                // 空きスロットへ入った分だけを残し、超過分は破棄します。
                // 既に入った分まで巻き戻すと、複数対象時に結果が不自然になります。
                return true
            }
        }
        return true
    }

    /**
     * ブロック操作をBukkitのブロック更新へ一元化します。
     *
     * setblock/fill相当の入力は、座標解決・ワールド一致・件数上限を同じ境界で
     * 検証してから更新します。fillを無制限に受け付けると、1ノードだけでサーバー
     * メインスレッドを長時間占有できるため、実行時にも上限を再確認します。
     */
    private fun executeBlockOperation(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
    ): Boolean {
        val material = Material.matchMaterial(node.string("block"))
            ?.takeIf { it != Material.AIR }
            ?: return false
        return when (BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value))) {
            BlockOperationMode.SETBLOCK -> {
                val location = node.blockPositionSpec?.let { resolvePosition(it, session, context) } ?: return false
                location.block.setType(material, false)
                true
            }
            BlockOperationMode.FILL -> {
                val from = node.blockFromSpec?.let { resolvePosition(it, session, context) } ?: return false
                val to = node.blockToSpec?.let { resolvePosition(it, session, context) } ?: return false
                if (from.world != to.world) return false
                val minX = minOf(from.blockX, to.blockX)
                val maxX = maxOf(from.blockX, to.blockX)
                val minY = minOf(from.blockY, to.blockY)
                val maxY = maxOf(from.blockY, to.blockY)
                val minZ = minOf(from.blockZ, to.blockZ)
                val maxZ = maxOf(from.blockZ, to.blockZ)
                val volume = (maxX.toLong() - minX.toLong() + 1L) *
                    (maxY.toLong() - minY.toLong() + 1L) *
                    (maxZ.toLong() - minZ.toLong() + 1L)
                if (volume !in 1L..MAX_BLOCK_OPERATION_VOLUME) return false
                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        for (z in minZ..maxZ) {
                            from.world.getBlockAt(x, y, z).setType(material, false)
                        }
                    }
                }
                true
            }
            null -> false
        }
    }

    private fun evaluateCondition(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
    ): Boolean {
        val kind = runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrDefault(ConditionKind.TARGET_EXISTS)
        val target = resolveTarget(context, node.targetSpec, session)
        return when (kind) {
            ConditionKind.TARGET_EXISTS -> target != null
            ConditionKind.ENTITY_STATE -> when (node.string("state")) {
                "sneaking" -> target is Player && target.isSneaking
                "on_ground" -> target?.isOnGround == true
                else -> false
            }
            ConditionKind.VARIABLE_STATE -> compareVariable(
                getVariable(
                    session,
                    node.string("variable"),
                    runCatching { VariableScope.valueOf(node.string("variableScope", VariableScope.TEMPORARY.name)) }
                        .getOrDefault(VariableScope.TEMPORARY),
                ),
                node.string("value"),
                node.string("operator", "=="),
            )
            ConditionKind.BLOCK_STATE -> {
                val conditionPosition = node.conditionPositionSpec
                val location = when {
                    conditionPosition != null ->
                        resolvePosition(conditionPosition, session, context) ?: return false
                    context?.position != null ->
                        resolvePosition(context.position, session, context) ?: return false
                    else -> session.origin
                }
                location.world == session.origin.world && location.block.type == Material.matchMaterial(node.string("block"))
            }
            ConditionKind.ITEM_POSSESSION -> {
                val player = target as? Player ?: return false
                val material = Material.matchMaterial(node.string("item")) ?: return false
                player.inventory.contains(material, node.int("count", 1).coerceAtLeast(1))
            }
        }
    }

    private fun executeVariable(
        node: CommandNode,
        session: ExecutionSession,
        inheritedContext: ExecutionContextSpec?,
    ): Boolean = runCatching {
        val name = node.string("name")
        val operation = VariableOperation.valueOf(node.string("operation", VariableOperation.SET.name))
        val scope = VariableScope.valueOf(node.string("scope", VariableScope.TEMPORARY.name))
        if (operation == VariableOperation.CLEAR) return removeVariable(session, name, scope)
        val current = getVariable(session, name, scope)
        val type = VariableType.valueOf(node.string("type", current?.type?.name ?: VariableType.BOOLEAN.name))
        // 現在値が存在し型が設定型と不一致の加減算・切替は、仕様3.3「型不正は実行失敗」に従い起動全体を停止する。
        // SETとSTORE系は上書き保存のため対象外。
        if (current != null && current.type != type &&
            operation in setOf(VariableOperation.ADD, VariableOperation.SUBTRACT, VariableOperation.TOGGLE)
        ) {
            return false
        }
        val value = when (operation) {
            VariableOperation.SET -> parseVariable(type, node.string("value"), session)
            VariableOperation.ADD, VariableOperation.SUBTRACT -> {
                val sign = if (operation == VariableOperation.ADD) 1 else -1
                when (type) {
                    VariableType.INTEGER -> {
                        val delta = Math.multiplyExact(sign.toLong(), node.string("value", "0").toLong())
                        WorldVariableValue(
                            type,
                            integerValue = Math.addExact(current?.integerValue ?: 0, delta),
                        )
                    }
                    VariableType.DECIMAL -> {
                        val result = (current?.decimalValue ?: 0.0) + sign * node.string("value", "0").toDouble()
                        if (!result.isFinite()) return false
                        WorldVariableValue(type, decimalValue = result)
                    }
                    else -> return false
                }
            }
            VariableOperation.TOGGLE -> WorldVariableValue(VariableType.BOOLEAN, booleanValue = !(current?.booleanValue ?: false))
            VariableOperation.STORE_POSITION -> WorldVariableValue(
                VariableType.POSITION,
                position = (inheritedContext?.position?.let {
                    resolvePosition(it, session, inheritedContext)
                } ?: if (inheritedContext?.position == null) session.origin else return false).let {
                    SavedPosition(it.x, it.y, it.z, it.yaw, it.pitch)
                },
            )
            VariableOperation.STORE_TARGET -> WorldVariableValue(
                VariableType.ENTITY,
                entityId = resolveTarget(inheritedContext, node.targetSpec, session)?.uniqueId ?: return false,
            )
            VariableOperation.CLEAR -> return false
        }
        setVariable(session, name, scope, value)
        true
    }.getOrDefault(false)

    private fun getVariable(session: ExecutionSession, name: String, scope: VariableScope): WorldVariableValue? =
        if (scope == VariableScope.WORLD) plugin.variables.get(session.worldId, name) else session.temporaryVariables[name]

    private fun setVariable(session: ExecutionSession, name: String, scope: VariableScope, value: WorldVariableValue) {
        if (scope == VariableScope.WORLD) plugin.variables.set(session.worldId, name, value)
        else session.temporaryVariables[name] = value
    }

    private fun removeVariable(session: ExecutionSession, name: String, scope: VariableScope): Boolean {
        if (scope == VariableScope.WORLD) plugin.variables.remove(session.worldId, name)
        else session.temporaryVariables.remove(name)
        return true
    }

    private fun parseVariable(type: VariableType, raw: String, session: ExecutionSession): WorldVariableValue {
        val resolved = when (raw) {
            "\$current_iteration_value" -> session.currentIterationValue?.toString()
                ?: error("current iteration value is unavailable")
            "\$current_loop_count" -> session.currentLoopCount?.toString()
                ?: error("current loop count is unavailable")
            else -> raw
        }
        return when (type) {
        VariableType.BOOLEAN -> WorldVariableValue(type, booleanValue = resolved.toBooleanStrict())
        VariableType.INTEGER -> WorldVariableValue(type, integerValue = resolved.toLong())
        VariableType.DECIMAL -> WorldVariableValue(
            type,
            decimalValue = resolved.toDouble().takeIf(Double::isFinite) ?: error("decimal must be finite"),
        )
        VariableType.TEXT -> WorldVariableValue(type, textValue = resolved)
        VariableType.POSITION, VariableType.ENTITY ->
            error("$type cannot be assigned from text")
        }
    }

    private fun compareVariable(value: WorldVariableValue?, raw: String, operator: String): Boolean {
        if (operator == "unset") return value == null
        if (operator == "set") return value != null
        value ?: return false
        val comparison = when (value.type) {
            VariableType.BOOLEAN -> (value.booleanValue ?: false).compareTo(raw.toBooleanStrictOrNull() ?: return false)
            VariableType.INTEGER -> (value.integerValue ?: 0).compareTo(raw.toLongOrNull() ?: return false)
            VariableType.DECIMAL -> (value.decimalValue ?: 0.0).compareTo(raw.toDoubleOrNull() ?: return false)
            VariableType.TEXT -> (value.textValue ?: "").compareTo(raw)
            VariableType.POSITION, VariableType.ENTITY -> return false
        }
        return when (operator) {
            "==" -> comparison == 0
            "!=" -> comparison != 0
            ">" -> comparison > 0
            "<" -> comparison < 0
            ">=" -> comparison >= 0
            "<=" -> comparison <= 0
            else -> false
        }
    }

    private fun resolveTarget(context: ExecutionContextSpec?, nodeTarget: TargetSpec?, session: ExecutionSession): Entity? =
        resolveTargets(context, nodeTarget, session).firstOrNull()

    private fun resolveTargets(
        context: ExecutionContextSpec?,
        nodeTarget: TargetSpec?,
        session: ExecutionSession,
    ): List<Entity> = resolveTargetSpecs(
        nodeTarget ?: context?.target ?: context?.executor ?: TargetSpec(TargetKind.INHERITED_TARGET),
        session,
        context,
    )

    private fun resolveTargetSpec(
        spec: TargetSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec? = session.context,
    ): Entity? = resolveTargetSpecs(spec, session, context).firstOrNull()

    private fun resolveTargetSpecs(
        spec: TargetSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec? = session.context,
    ): List<Entity> {
        val selectionOrigin = selectionOrigin(context, session) ?: return emptyList()
        val candidates: List<Entity> = when (spec.kind) {
            TargetKind.INHERITED_TARGET -> listOfNotNull(
                context?.target
                    ?.takeUnless { it.kind == TargetKind.INHERITED_TARGET }
                    ?.let { resolveTargetSpec(it, session, context) }
            )
            TargetKind.NEAREST_PLAYER, TargetKind.NEARBY_PLAYERS, TargetKind.ALL_PLAYERS, TargetKind.RANDOM_PLAYER ->
                session.origin.world.players.filter { matches(it, spec, session, context) }
            TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES ->
                session.origin.world.entities.filter { matches(it, spec, session, context) }
            TargetKind.FIXED_ENTITY -> listOfNotNull(spec.fixedEntityId?.let(session.origin.world::getEntity))
        }
        val inMyWorld = candidates.filter { it.world == session.origin.world }
        val sorted = when {
            spec.kind == TargetKind.RANDOM_PLAYER || spec.sort == me.awabi2048.kantancommander.model.TargetSort.RANDOM ->
                inMyWorld.shuffled()
            spec.sort == me.awabi2048.kantancommander.model.TargetSort.FURTHEST ->
                inMyWorld.sortedByDescending { it.location.distanceSquared(selectionOrigin) }
            else -> inMyWorld.sortedBy { it.location.distanceSquared(selectionOrigin) }
        }
        val defaultLimit = when (spec.kind) {
            TargetKind.NEAREST_PLAYER, TargetKind.RANDOM_PLAYER, TargetKind.NEAREST_ENTITY,
            TargetKind.INHERITED_TARGET, TargetKind.FIXED_ENTITY -> 1
            else -> Int.MAX_VALUE
        }
        return sorted.take((spec.limit ?: defaultLimit).coerceAtLeast(1))
    }

    private fun matches(
        entity: Entity,
        spec: TargetSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
    ): Boolean {
        // ディスク本体の表示実体（BlockDisplay）は設置物であり、対象選択の相手になるべきではない。
        // すべてのエンティティ系対象から恒常的に除外する。
        if (PlacementStore.DISPLAY_TAG in entity.scoreboardTags) return false
        if (spec.entityType != null && entity.type.key.toString() != spec.entityType) return false
        if (spec.name != null && entity.name != spec.name) return false
        if (spec.tag != null && spec.tag !in entity.scoreboardTags) return false
        val origin = selectionOrigin(context, session) ?: return false
        val distance = entity.location.distance(origin)
        if (spec.minimumDistance != null && distance < spec.minimumDistance) return false
        if (spec.maximumDistance != null && distance > spec.maximumDistance) return false
        return spec.gameMode == null || entity is Player && entity.gameMode.name.equals(spec.gameMode, true)
    }

    private fun selectionOrigin(context: ExecutionContextSpec?, session: ExecutionSession): Location? =
        when (val position = context?.position) {
            null -> session.origin
            else -> when (position.kind) {
                PositionKind.CAPTURED, PositionKind.COORDINATES,
                PositionKind.DISK, PositionKind.MYWORLD_SPAWN,
                PositionKind.TEMPORARY_VARIABLE, PositionKind.WORLD_VARIABLE ->
                    resolvePosition(position, session, context)
                PositionKind.EXECUTOR ->
                    context.executor?.let { resolveTargetSpec(it, session, context) }?.location
                PositionKind.TARGET ->
                    context.target?.let { resolveTargetSpec(it, session, context) }?.location
            }
        }

    private fun contextFrom(node: CommandNode) = node.contextOverride ?: ExecutionContextSpec()

    /** 現在ノードの明示設定を最優先し、PREVIOUS指定時だけ直前の有効execute指定を基準にします。 */
    private fun effectiveContext(node: CommandNode, session: ExecutionSession): ExecutionContextSpec? {
        return ExecutionSemantics.effectiveContext(
            session.context,
            session.previousContext,
            node.effectiveContextSource,
            node.contextOverride,
        )
    }

    private fun resolvePosition(
        spec: PositionSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec? = session.context,
    ): Location? = when (spec.kind) {
        PositionKind.CAPTURED, PositionKind.COORDINATES -> Location(
            session.origin.world,
            spec.x ?: session.origin.x,
            spec.y ?: session.origin.y,
            spec.z ?: session.origin.z,
            spec.yaw ?: session.origin.yaw,
            spec.pitch ?: session.origin.pitch,
        )
        PositionKind.DISK -> session.origin.clone()
        PositionKind.EXECUTOR -> context?.executor?.let { resolveTargetSpec(it, session, context) }?.location
        PositionKind.TARGET -> context?.target?.let { resolveTargetSpec(it, session, context) }?.location
        PositionKind.MYWORLD_SPAWN -> session.origin.world.spawnLocation
        PositionKind.TEMPORARY_VARIABLE -> session.temporaryVariables[spec.variable.orEmpty()]?.position?.let {
            Location(session.origin.world, it.x, it.y, it.z, it.yaw, it.pitch)
        }
        PositionKind.WORLD_VARIABLE -> plugin.variables.get(session.worldId, spec.variable.orEmpty())?.position?.let {
            Location(session.origin.world, it.x, it.y, it.z, it.yaw, it.pitch)
        }
    }

    private fun applyFacing(
        destination: Location,
        facing: me.awabi2048.kantancommander.model.FacingSpec,
        context: ExecutionContextSpec?,
        session: ExecutionSession,
    ) {
        when (facing.kind) {
            FacingKind.INHERITED -> Unit
            FacingKind.ROTATION, FacingKind.CAPTURED -> {
                destination.yaw = facing.yaw ?: destination.yaw
                destination.pitch = facing.pitch ?: destination.pitch
            }
            FacingKind.EXECUTOR -> {
                val location = context?.executor
                    ?.let { resolveTargetSpec(it, session, context) }
                    ?.location ?: return
                destination.yaw = location.yaw
                destination.pitch = location.pitch
            }
            FacingKind.TARGET -> {
                val location = context?.target
                    ?.let { resolveTargetSpec(it, session, context) }
                    ?.location ?: return
                faceLocation(destination, location)
            }
            FacingKind.COORDINATES -> faceLocation(
                destination,
                Location(
                    destination.world,
                    facing.x ?: return,
                    facing.y ?: return,
                    facing.z ?: return,
                ),
            )
            FacingKind.MYWORLD_SPAWN -> faceLocation(destination, session.origin.world.spawnLocation)
        }
    }

    private fun faceLocation(destination: Location, target: Location) {
        if (destination.world != target.world) return
        val direction = target.toVector().subtract(destination.toVector())
        if (direction.lengthSquared() > 0.0) destination.direction = direction
    }

    private fun stop(
        session: ExecutionSession,
        script: DiskScript,
        nodeId: UUID,
        depth: Int,
        reason: String,
        done: (Boolean) -> Unit,
    ) {
        plugin.logger.warning(
            "[KantanCommander] forced-stop root=${session.rootId} disk=${script.id} node=$nodeId " +
                "reason=$reason executed=${session.executed} limit=${session.budget} depth=$depth " +
                "world=${session.origin.world.name} " +
                "location=${session.origin.blockX},${session.origin.blockY},${session.origin.blockZ}"
        )
        done(false)
    }

    private data class ExecutionSession(
        val rootId: UUID,
        val origin: Location,
        val actor: Player?,
        val budget: Int,
        val maxDepth: Int,
        val worldId: UUID,
        var executed: Int = 0,
        var context: ExecutionContextSpec? = null,
        var previousContext: ExecutionContextSpec? = null,
        val temporaryVariables: MutableMap<String, WorldVariableValue> = mutableMapOf(),
        val loops: MutableList<LoopFrame> = mutableListOf(),
        var currentIterationValue: Long? = null,
        var currentLoopCount: Long? = null,
    )

    private data class LoopFrame(
        val startId: UUID,
        val endId: UUID,
        var value: Long,
        var end: Long,
        var step: Long,
        var count: Long,
        val startContext: ExecutionContextSpec?,
    )
}

internal object ExecutionSemantics {
    fun conditionResult(rawResult: Boolean, inverted: Boolean): Boolean =
        rawResult xor inverted

    fun withinBudget(executed: Int, budget: Int): Boolean =
        executed < budget

    fun withinCallDepth(currentDepth: Int, maximumDepth: Int): Boolean =
        currentDepth < maximumDepth

    fun nextForValue(current: Long, step: Long): Long? =
        try {
            Math.addExact(current, step)
        } catch (_: ArithmeticException) {
            null
        }

    fun mergeContexts(
        inherited: ExecutionContextSpec?,
        override: ExecutionContextSpec?,
    ): ExecutionContextSpec? {
        if (override == null) return inherited
        if (inherited == null) return override
        return ExecutionContextSpec(
            executor = override.executor ?: inherited.executor,
            target = override.target ?: inherited.target,
            position = override.position ?: inherited.position,
            facing = override.facing ?: inherited.facing,
        )
    }

    fun effectiveContext(
        base: ExecutionContextSpec?,
        previous: ExecutionContextSpec?,
        source: ContextSource,
        override: ExecutionContextSpec?,
    ): ExecutionContextSpec? = mergeContexts(
        if (source == ContextSource.PREVIOUS) previous ?: base else base,
        override,
    )

    fun withinForRange(
        value: Long,
        end: Long,
        step: Long,
        inclusiveEnd: Boolean,
    ): Boolean = when {
        step > 0 && inclusiveEnd -> value <= end
        step > 0 -> value < end
        step < 0 && inclusiveEnd -> value >= end
        step < 0 -> value > end
        else -> false
    }
}
