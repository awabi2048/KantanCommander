package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.MAX_BLOCK_OPERATION_VOLUME
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.ControlBlockStateConditionPolicy
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.NumericExpression
import me.awabi2048.kantancommander.model.TemporaryTemplate
import me.awabi2048.kantancommander.model.TemporaryValue
import me.awabi2048.kantancommander.model.TemporaryVariableType
import me.awabi2048.kantancommander.model.normalizedTemporaryName
import me.awabi2048.kantancommander.model.selectedControlBlockStates
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.model.WorldVariableValue
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.DisplayTextTiming
import com.awabi2048.ccsystem.CCSystem
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.SoundCategory
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.logging.Level
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.kantancommander.item.ItemStackCodec
import org.bukkit.inventory.EquipmentSlot
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

class SequenceExecutor(private val plugin: KantanCommanderPlugin) {
    private val timedActionBar = TimedActionBarService(plugin)
    private val systemEntityRegistry
        get() = CCSystem.getAPI().getSystemEntityRegistry()

    fun execute(scriptId: UUID, origin: Location, actor: Player? = null, callback: (Boolean) -> Unit = {}) {
        val worldData = if (plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) {
            MyWorldManagerApi.getWorldRepository()?.findByWorldName(origin.world.name)
        } else null
        if (worldData == null) {
            plugin.logger.warning("[KantanCommander] rejected disk=$scriptId reason=outside_myworld world=${origin.world.name}")
            return callback(false)
        }
        val script = plugin.scripts.load(scriptId) ?: return callback(false)
        val validationErrors = ExecutableScriptValidator.validate(
            script,
            plugin.graphLimits(),
            plugin.variables.definitions(worldData.uuid),
        )
        if (validationErrors.isNotEmpty()) {
            plugin.logger.warning(
                "[KantanCommander] rejected disk=$scriptId reason=invalid_script " +
                    "errors=${validationErrors.joinToString(" | ") { it.rendered() }}"
            )
            return callback(false)
        }
        // 同一ディスクの再トリガー（タイマーとレッドストーンの同時到来、WAIT中の再起動など）は
        // 実行可能な範囲で並行許可する。各起動は独立したExecutionSessionを持つため干渉しない。
        val session = ExecutionSession(
            rootId = scriptId,
            origin = origin.clone(),
            actor = actor,
            creatorId = script.owner,
            programName = script.name,
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

        val next: (UUID?, NodeExecutionOutcome) -> Unit = { target, outcome ->
            when (outcome) {
                NodeExecutionOutcome.SUCCESS,
                NodeExecutionOutcome.SKIPPED,
                -> runNode(script, graph, target, session, depth, done)
                NodeExecutionOutcome.FAILED ->
                    stop(session, script, node.id, depth, "node_failed", done)
            }
        }
        when (node.type) {
            CommandType.WAIT -> {
                // 保存・入力値は秒を正本とし、Minecraftのスケジューラ境界でだけtickへ変換します。
                val seconds = resolveNumber(node.string("seconds"), session)
                    ?.takeIf(CommandValueRules::isWaitSeconds)
                    ?: return stop(session, script, node.id, depth, "invalid_wait_seconds", done)
                val waitTicks = CommandValueRules.secondsToTicks(seconds)
                    ?.takeIf { it >= 1L }
                    ?: return stop(session, script, node.id, depth, "invalid_wait_seconds", done)
                plugin.server.scheduler.runTaskLater(
                    plugin,
                    Runnable { next(node.next, NodeExecutionOutcome.SUCCESS) },
                    waitTicks,
                )
            }
            CommandType.CONDITION -> {
                val effectiveContext = effectiveContext(session)
                val rawResult = evaluateCondition(
                    node,
                    session,
                    effectiveContext,
                )
                val result = ExecutionSemantics.conditionResult(rawResult, node.boolean("inverted"))
                session.previousContext = effectiveContext
                plugin.logger.info("[KantanCommander] condition disk=${script.id} node=${node.id} result=$result")
                next(if (result) node.trueNext else node.falseNext, NodeExecutionOutcome.SUCCESS)
            }
            CommandType.DISK_CALL -> {
                val callerContext = session.context
                val callerPrevious = session.previousContext
                // 一時変数は呼出先へ引き継がず、復帰時に復元します（隔離）。
                val callerTemporaries = session.temporaries.toMap()
                session.temporaries.clear()
                // 実行状態はDISK_CALLの境界を越えて共有しません。現在はGUIから
                // コンテキストを生成しませんが、内部呼出し元が保持している実行状態
                // を呼出先の処理へ渡す境界として、この保存・復元を残します。
                session.context = effectiveContext(session)
                runDiskCall(node, session, depth) { success ->
                    session.context = callerContext
                    session.previousContext = callerPrevious
                    session.temporaries.clear()
                    session.temporaries.putAll(callerTemporaries)
                    next(
                        node.next,
                        if (success) NodeExecutionOutcome.SUCCESS else NodeExecutionOutcome.FAILED,
                    )
                }
            }
            CommandType.TEMP_SET -> {
                val success = executeTemporary(node, session)
                next(
                    node.next,
                    if (success) NodeExecutionOutcome.SUCCESS else NodeExecutionOutcome.FAILED,
                )
            }
            CommandType.VARIABLE -> {
                val success = executeVariable(
                    node,
                    session,
                )
                if (success) session.previousContext = effectiveContext(session)
                next(
                    node.next,
                    if (success) NodeExecutionOutcome.SUCCESS else NodeExecutionOutcome.FAILED,
                )
            }
            CommandType.MERGE -> next(node.next, NodeExecutionOutcome.SUCCESS)
            CommandType.FOR_START -> beginFor(script, graph, node, session, depth, done)
            CommandType.FOR_END -> finishForIteration(script, graph, node, session, depth, done)
            CommandType.BREAK -> breakFor(script, graph, node, session, depth, done)
            CommandType.CONTINUE -> continueFor(script, graph, node, session, depth, done)
            else -> {
                // 一時参照の解決失敗は、入力値そのものの実行失敗とは異なります。
                // 仕様上は「対象が見つからなかった」と同じくそのノードだけを
                // スキップし、後続ノードを継続します。静的検証を通過した後でも、
                // ENTITYの死亡・別ワールド・アンロードは実行時に起こり得るため、
                // 実処理の前にこの判定を行います。
                val outcome = when {
                    hasUnavailableTemporaryReference(node, session) -> NodeExecutionOutcome.SKIPPED
                    else -> when (executeImmediate(node, session)) {
                        ImmediateExecutionResult.SUCCESS -> NodeExecutionOutcome.SUCCESS
                        ImmediateExecutionResult.SKIPPED -> NodeExecutionOutcome.SKIPPED
                        ImmediateExecutionResult.FAILED -> NodeExecutionOutcome.FAILED
                    }
                }
                next(node.next, outcome)
            }
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
        val count = resolvePositiveInt(node.string("count", "1"), session)
            ?: return stop(session, script, node.id, depth, "invalid_for_count", done)
        session.loops += LoopFrame(node.id, endId, count.toLong(), 1, session.context)
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
        session.context = frame.startContext
        if (ExecutionSemantics.shouldRunNextLoopIteration(frame.count, frame.limit)) {
            frame.count = requireNotNull(ExecutionSemantics.nextLoopCount(frame.count))
            session.currentLoopCount = frame.count
            runNode(script, graph, startNode.trueNext, session, depth, done)
        } else {
            plugin.logger.info("[KantanCommander] for-finish root=${session.rootId} disk=${script.id} node=${frame.startId} reason=count_complete iterations=${frame.count}")
            session.loops.remove(frame)
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

    private fun runDiskCall(node: CommandNode, session: ExecutionSession, depth: Int, done: (Boolean) -> Unit) {
        if (!ExecutionSemantics.withinCallDepth(depth, session.maxDepth)) {
            plugin.logger.warning("[KantanCommander] disk-call-depth root=${session.rootId} node=${node.id} depth=$depth max=${session.maxDepth}")
            return done(false)
        }
        val snapshot = node.snapshot ?: return done(false)
        val synthetic = DiskScript(name = "snapshot", owner = session.actor?.uniqueId ?: UUID(0, 0), graph = snapshot)
        runGraph(synthetic, snapshot, session, depth + 1, done)
    }

    private fun executeImmediate(node: CommandNode, session: ExecutionSession): ImmediateExecutionResult = try {
        if (executeImmediateBoolean(node, session)) ImmediateExecutionResult.SUCCESS
        else ImmediateExecutionResult.FAILED
    } catch (_: ParticleQuotaRejected) {
        // 上限超過は入力・実行失敗ではなく、このノードだけを送信せず後続へ進める
        // 制御結果です。通常の例外ログへ混ぜると、運用上の想定内の抑止が障害に見えます。
        ImmediateExecutionResult.SKIPPED
    } catch (failure: Throwable) {
        plugin.logger.log(Level.WARNING, "[KantanCommander] node failed root=${session.rootId} node=${node.id}", failure)
        ImmediateExecutionResult.FAILED
    }

    private fun executeImmediateBoolean(node: CommandNode, session: ExecutionSession): Boolean = run {
        val effectiveContext = effectiveContext(session)
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
                (node.destinationFacingSpec ?: effectiveContext?.facing)?.let {
                    applyFacing(destination, it, effectiveContext, session)
                }
                targets.all { it.teleport(destination.clone()) }
            }
            CommandType.GIVE_ITEM -> {
                val players = targets.filterIsInstance<Player>()
                if (players.isEmpty()) return false
                // 一時変数参照があれば最優先します。リテラルとの併記は検証で拒否します。
                val refTemp = node.itemTempRef?.takeIf(String::isNotBlank)
                    ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.ITEM }
                val material = if (refTemp != null) {
                    CommandValueRules.material(refTemp.item.orEmpty(), allowAir = false)
                } else {
                    CommandValueRules.material(node.string("item"), allowAir = false)
                } ?: return false
                val template = if (refTemp != null) {
                    refTemp.itemData?.takeIf(String::isNotBlank)?.let(ItemStackCodec::decode)
                        ?: ItemStack(material)
                } else {
                    ItemStackCodec.decode(node.string("itemData")) ?: ItemStack(material)
                }
                val count = resolvePositiveInt(node.string("count"), session) ?: return false
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
                    "equip" -> {
                        val refTemp = node.itemTempRef?.takeIf(String::isNotBlank)
                            ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                            ?.takeIf { it.type == TemporaryVariableType.ITEM }
                        val material = if (refTemp != null) {
                            CommandValueRules.material(refTemp.item.orEmpty(), allowAir = false)
                        } else {
                            CommandValueRules.material(node.string("item"), allowAir = false)
                        } ?: return false
                        val template = if (refTemp != null) {
                            refTemp.itemData?.takeIf(String::isNotBlank)?.let(ItemStackCodec::decode)
                                ?: ItemStack(material)
                        } else {
                            ItemStackCodec.decode(node.string("itemData")) ?: ItemStack(material)
                        }
                        val slot = runCatching { EquipmentSlot.valueOf(node.string("slot")) }.getOrNull() ?: return false
                        val overwrite = node.boolean("overwrite")
                        val applicable = targets.filterIsInstance<LivingEntity>().mapNotNull { it.equipment }
                        if (applicable.isEmpty()) return false
                        applicable.forEach { equipment ->
                            if (overwrite || equipment.getItem(slot).type.isAir) {
                                equipment.setItem(slot, template.clone())
                            }
                        }
                        true
                    }
                    "tag" -> {
                        val tag = resolveText(node.string("tag"), session) ?: return false
                        // タグは単一値としてそのまま扱います。カンマを分割したり、
                        // 複数タグとして順番に適用したりしません。
                        if (!CommandValueRules.isTag(tag)) return false
                        when (node.string("tagOperation", "add")) {
                            // タグ追加は冪等な設定操作として扱います。既にタグを持つ対象が
                            // 混在していても、対象全体の処理を失敗扱いにしません。
                            "add" -> {
                                targets.forEach { it.addScoreboardTag(tag) }
                                true
                            }
                            "remove" -> targets.forEach { it.removeScoreboardTag(tag) }.let { true }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
            CommandType.DISPLAY_TEXT -> {
                val players = targets.filterIsInstance<Player>()
                if (players.isEmpty()) return false
                val text = legacyText(resolveText(node.string("text"), session) ?: return false)
                val subtitle = legacyText(resolveText(node.string("subtitle"), session) ?: return false)
                when (node.string("mode", "tellraw")) {
                    "title" -> players.forEach { player ->
                        timedActionBar.cancel(player, clear = true)
                        val timing = displayTiming(node, session)
                        player.showTitle(Title.title(
                                text,
                                subtitle,
                                Title.Times.times(
                                    timing.fadeInDuration,
                                    timing.stayDuration,
                                    timing.fadeOutDuration,
                                ),
                            ))
                    }
                    "subtitle" -> players.forEach { player ->
                        timedActionBar.cancel(player, clear = true)
                        val timing = displayTiming(node, session)
                        player.showTitle(Title.title(
                            Component.empty(),
                            text,
                            Title.Times.times(
                                timing.fadeInDuration,
                                timing.stayDuration,
                                timing.fadeOutDuration,
                            ),
                        ))
                    }
                    "actionbar" -> players.forEach { player ->
                        timedActionBar.show(player, text, displayTiming(node, session))
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
                // 召喚タグは単一値です。空欄はタグなしとして扱い、カンマを
                // 複数タグの区切りに変換しないことでGUI・保存・実行の契約を揃えます。
                val tag = resolveText(node.string("tags"), session) ?: return false
                if (tag.isNotBlank() && !CommandValueRules.isTag(tag)) return false
                // 召喚位置が指定されていればそちらを優先し、未設定時はコンテキスト位置（effectiveOrigin）を使用します。
                val summonOrigin = node.summonPositionSpec?.let { resolvePosition(it, session, effectiveContext) ?: return false } ?: effectiveOrigin
                val spawn = summonOrigin.clone()
                effectiveContext?.facing?.let { applyFacing(spawn, it, effectiveContext, session) }
                val entity = summonOrigin.world.spawnEntity(spawn, type)
                if (tag.isNotBlank()) entity.addScoreboardTag(tag)
                val rawCustomName = node.string("customName")
                val customName = resolveText(rawCustomName, session)
                    ?: return false.also { entity.remove() }
                if (customName.isNotEmpty()) {
                    entity.customName(legacyText(customName))
                    entity.isCustomNameVisible = true
                }
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
                val refTemp = node.soundTempRef?.takeIf(String::isNotBlank)
                    ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.SOUND }
                val sound = refTemp?.sound ?: node.string("sound")
                if (NamespacedKey.fromString(sound) == null) return false
                val volume = (refTemp?.volume ?: resolveNumber(node.string("volume", "1.0"), session))
                    ?.toFloat() ?: return false
                val pitch = (refTemp?.pitch ?: resolveNumber(node.string("pitch", "1.0"), session))
                    ?.toFloat() ?: return false
                if (node.string("soundScope", "POSITION") == "WORLD") {
                    // 全域指定は各プレイヤー位置を音源位置にするため、現行の
                    // 「起動ワールドの全プレイヤーへ確実に届ける」挙動を維持します。
                    effectiveOrigin.world.players.forEach { it.playSound(it.location, sound, SoundCategory.MASTER, volume, pitch) }
                } else {
                    val source = node.soundPositionSpec?.let { resolvePosition(it, session, effectiveContext) } ?: effectiveOrigin
                    effectiveOrigin.world.players.forEach { it.playSound(source, sound, SoundCategory.MASTER, volume, pitch) }
                }
                true
            }
            CommandType.PARTICLE -> executeParticle(node, session, effectiveContext, effectiveOrigin)
            CommandType.APPLY_EFFECT -> {
                val refTemp = node.effectTempRef?.takeIf(String::isNotBlank)
                    ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.EFFECT }
                val key = NamespacedKey.fromString(refTemp?.effect ?: node.string("effect")) ?: return false
                val effect = Registry.EFFECT.get(key) ?: return false
                val applicable = targets.filterIsInstance<LivingEntity>()
                if (applicable.isEmpty()) return false
                val seconds = refTemp?.seconds
                    ?: resolvePositiveInt(node.string("seconds"), session) ?: return false
                val level = refTemp?.level
                    ?: resolvePositiveInt(node.string("level"), session) ?: return false
                applicable.forEach {
                    it.addPotionEffect(org.bukkit.potion.PotionEffect(
                        effect,
                        seconds * 20,
                        level - 1,
                    ))
                }
                true
            }
            CommandType.CAMERA_SHAKE -> {
                val intensity = resolveNumber(node.string("intensity", "1.0"), session)?.toFloat() ?: return false
                val seconds = resolveNumber(node.string("seconds", "5.0"), session)?.toFloat() ?: return false
                targets.filterIsInstance<Player>().forEach {
                    CameraShakeService.apply(
                        plugin,
                        it,
                        intensity,
                        seconds,
                        node.string("shakeType", "positional"),
                    )
                }
                true
            }
            CommandType.BLOCK_OPERATION -> executeBlockOperation(node, session, effectiveContext)
            CommandType.ENTITY_DELETE -> {
                if (targets.isEmpty()) return false
                targets.forEach(Entity::remove)
                true
            }
            else -> true
        }
        if (success) session.previousContext = effectiveContext
        success
    }

    /**
     * Particleの送信は対象指定を持たず、中心位置のワールドにいる全プレイヤーへ
     * 1回のパケット送信で届けます。個数の上限判定はパケット送信前に行い、拒否時に
     * 部分表示が発生しないようにします。
     */
    private fun executeParticle(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
        effectiveOrigin: Location,
    ): Boolean {
        val particle = ParticleSettings.particle(node) ?: return false
        val deltaX = resolveNumber(node.string(ParticleSettings.PARAM_DELTA_X, "0.0"), session)
            ?.takeIf(Double::isFinite) ?: return false
        val deltaY = resolveNumber(node.string(ParticleSettings.PARAM_DELTA_Y, "0.0"), session)
            ?.takeIf(Double::isFinite) ?: return false
        val deltaZ = resolveNumber(node.string(ParticleSettings.PARAM_DELTA_Z, "0.0"), session)
            ?.takeIf(Double::isFinite) ?: return false
        val speed = resolveNumber(node.string(ParticleSettings.PARAM_SPEED, "0.0"), session)
            ?.takeIf { it.isFinite() && it >= 0.0 } ?: return false
        val count = resolvePositiveInt(node.string(ParticleSettings.PARAM_COUNT, "1"), session) ?: return false
        val origin = node.particlePositionSpec
            ?.let { resolvePosition(it, session, context) ?: return false }
            ?: effectiveOrigin
        val data = ParticleSettings.parseData(particle, node.string(ParticleSettings.PARAM_DATA))
            .getOrNull() ?: return false
        val bukkitData = data.toBukkitData(origin)
        if (ParticleSettings.requiresData(particle) && bukkitData == null) return false

        if (!plugin.particleQuota.tryAcquire(origin.world.uid, count)) {
            throw ParticleQuotaRejected()
        }
        // world.playersを明示的にreceiverへ渡すことで、「実行位置周囲の対象」ではなく
        // 「表示中心と同じワールドの全プレイヤー」という仕様をAPI呼び出しにも残します。
        origin.world.spawnParticle(
            particle,
            origin.world.players,
            null,
            origin.x,
            origin.y,
            origin.z,
            count,
            deltaX,
            deltaY,
            deltaZ,
            speed,
            bukkitData,
            true,
        )
        return true
    }

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
        val refTemp = node.blockTempRef?.takeIf(String::isNotBlank)
            ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
            ?.takeIf { it.type == TemporaryVariableType.BLOCK }
        val material = if (refTemp != null) {
            CommandValueRules.placementMaterial(refTemp.block.orEmpty())
        } else {
            CommandValueRules.placementMaterial(node.string("block"))
        } ?: return false
        return when (BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value))) {
            BlockOperationMode.SETBLOCK -> {
                val location = node.blockPositionSpec?.let { resolvePosition(it, session, context) } ?: return false
                val world = location.world ?: return false
                if (plugin.placements.isRegistered(world, location.blockX, location.blockY, location.blockZ)) {
                    // 拡張コマンドブロックの位置は、setblockでも常に変更を拒否します。
                    return false
                }
                location.block.setType(material, false)
                true
            }
            BlockOperationMode.FILL -> {
                val from = node.blockFromSpec?.let { resolvePosition(it, session, context) } ?: return false
                val to = node.blockToSpec?.let { resolvePosition(it, session, context) } ?: return false
                val world = from.world ?: return false
                if (world != to.world) return false
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
                // 1座標でも保護対象なら、通常のfillのように対象外だけを飛ばさず、
                // 変更前にコマンド全体を失敗させます。これにより部分変更を残しません。
                if (BlockOperationProtectionPolicy.hasProtectedBlock(
                        minX,
                        maxX,
                        minY,
                        maxY,
                        minZ,
                        maxZ,
                    ) { coordinate ->
                        plugin.placements.isRegistered(world, coordinate.x, coordinate.y, coordinate.z)
                    }
                ) return false
                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        for (z in minZ..maxZ) {
                            world.getBlockAt(x, y, z).setType(material, false)
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
        return when (kind) {
            ConditionKind.TARGET_EXISTS -> resolveTarget(context, node.targetSpec, session) != null
            ConditionKind.PLAYER_STATE -> {
                val target = resolveTarget(context, node.targetSpec, session)
                val player = target as? Player ?: return false
                val sneaking = node.params["sneaking"]?.takeIf(String::isNotBlank)?.toBooleanStrictOrNull()
                val sneakingMatches = sneaking == null || player.isSneaking == sneaking
                val refTemp = node.itemTempRef?.takeIf(String::isNotBlank)
                    ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.ITEM }
                val item = refTemp?.item ?: node.string("item")
                val itemData = refTemp?.itemData ?: node.string("itemData")
                val itemMatches = item.isBlank() || playerHasItem(player, item, itemData)
                sneakingMatches && itemMatches
            }
            ConditionKind.VARIABLE_STATE -> {
                val value = plugin.variables.get(session.worldId, node.string("variable"))
                val comparisonValue = resolveNumber(node.string("value"), session) ?: return false
                compareVariable(value, comparisonValue, node.string("operator", "=="))
            }
            ConditionKind.BLOCK_STATE -> {
                val conditionPosition = node.conditionPositionSpec
                val location = when {
                    conditionPosition != null ->
                        resolvePosition(conditionPosition, session, context) ?: return false
                    context?.position != null ->
                        resolvePosition(context.position, session, context) ?: return false
                    else -> session.origin
                }
                location.world == session.origin.world &&
                    location.block.type == CommandValueRules.material(node.string("block"))
            }
            ConditionKind.CONTROL_BLOCK_STATE -> {
                // 制御ブロック自身の現在状態を判定します。複数項目はポリシー側でAND評価し、
                // 外側の反転指定は既存の条件実行共通処理へ任せます。
                ControlBlockStateConditionPolicy.matches(
                    node.selectedControlBlockStates(),
                    RedstoneInputReader.isPowered(session.origin.block),
                )
            }
        }
    }

    private fun executeVariable(
        node: CommandNode,
        session: ExecutionSession,
    ): Boolean = runCatching {
        val name = node.string("name")
        val operation = VariableOperation.valueOf(node.string("operation", VariableOperation.DEFINE.name))
        val current = plugin.variables.get(session.worldId, name)
        // CHANGEではGUI上のtypeを編集させず、MyWorldに定義済みの型を唯一の正とします。
        // 古いノードに残ったtypeを参照すると、STRING変数の変更がNUMBERとして拒否されます。
        val type = when (operation) {
            VariableOperation.DEFINE -> VariableType.valueOf(node.string("type", VariableType.NUMBER.name))
            VariableOperation.CHANGE -> current?.type ?: return false
        }
        val value = when (operation) {
            VariableOperation.DEFINE -> {
                if (current != null) return false
                parseAssignedValue(type, node.string("value"), session)
            }
            VariableOperation.CHANGE -> {
                when (type) {
                    VariableType.NUMBER -> when (VariableChangeMode.valueOf(node.string("changeMode", VariableChangeMode.ASSIGN.name))) {
                        VariableChangeMode.ASSIGN -> parseAssignedValue(type, node.string("value"), session)
                        VariableChangeMode.CALCULATE -> {
                            val expression = NumericExpression.parse(node.string("value")).expression ?: return false
                            val result = expression.evaluate(
                                { reference ->
                                    if (SystemVariableNames.isSystemName(reference)) {
                                        when (reference) {
                                            SystemVariableNames.CURRENT_LOOP_COUNT -> session.currentLoopCount?.toDouble()
                                            else -> null
                                        }
                                    } else plugin.variables.get(session.worldId, reference)?.numberValue
                                },
                                { reference ->
                                    session.temporaries[reference.normalizedTemporaryName()]
                                        ?.takeIf { it.type == TemporaryVariableType.NUMBER }
                                        ?.numberValue?.takeIf(Double::isFinite)
                                },
                            ) ?: return false
                            WorldVariableValue(VariableType.NUMBER, numberValue = result)
                        }
                    }
                    VariableType.STRING -> parseAssignedValue(type, node.string("value"), session)
                }
            }
        }
        when (operation) {
            VariableOperation.DEFINE -> plugin.variables.define(session.worldId, name, value)
            VariableOperation.CHANGE -> {
                plugin.variables.set(session.worldId, name, value)
                true
            }
        }
    }.getOrDefault(false)

    /**
     * 一時変数を設定します。再設定は上書きとして扱います。
     *
     * NUMBER・STRING はリテラル値を受け付け、`%{name}%` 参照の展開に対応します。
     * 複合6型は型ごとの共通設定で解決済み値を保持する想定であり、
     * ここでは型ごとの最小検証のうえ保存します。
     */
    private fun executeTemporary(node: CommandNode, session: ExecutionSession): Boolean = runCatching {
        val name = me.awabi2048.kantancommander.model.TemporaryTemplate.normalized(node.string("name"))
        require(CommandValueRules.isVariableName(name)) { "invalid temporary name" }
        val type = TemporaryVariableType.parse(
            node.string("tempType", TemporaryVariableType.NUMBER.name),
        ) ?: return false
        val value = when (type) {
            TemporaryVariableType.NUMBER -> TemporaryValue(
                type,
                numberValue = resolveNumber(node.string("value"), session) ?: error("number must be finite"),
            )
            TemporaryVariableType.STRING -> TemporaryValue(
                type,
                stringValue = resolveText(node.string("value"), session) ?: error("string is unavailable"),
            )
            TemporaryVariableType.LOCATION -> {
                // 新形式は通常コマンドと同じPositionSpec/FacingSpecから解決します。
                // 旧POSITIONのx/y/z形式は読み込み済みデータを壊さないため、この実行境界
                // だけでLOCATIONへ組み立て直します。以後の参照は常に一つのSavedLocationです。
                val positionSpec = node.temporaryLocationPositionSpec ?: PositionSpec(
                    PositionKind.COORDINATES,
                    x = resolveNumber(node.string("x"), session) ?: error("invalid x"),
                    y = resolveNumber(node.string("y"), session) ?: error("invalid y"),
                    z = resolveNumber(node.string("z"), session) ?: error("invalid z"),
                )
                val facingSpec = node.temporaryLocationFacingSpec ?: FacingSpec(
                    FacingKind.CAPTURED,
                    yaw = node.string("yaw").takeIf(String::isNotBlank)
                        ?.let { resolveNumber(it, session)?.toFloat() } ?: 0f,
                    pitch = node.string("pitch").takeIf(String::isNotBlank)
                        ?.let { resolveNumber(it, session)?.toFloat() } ?: 0f,
                )
                val location = resolvePosition(positionSpec, session, session.context)
                    ?: error("location is unavailable")
                applyFacing(location, facingSpec, session.context, session)
                TemporaryValue(
                    type,
                    location = me.awabi2048.kantancommander.model.SavedLocation(
                        location.x,
                        location.y,
                        location.z,
                        location.yaw,
                        location.pitch,
                    ),
                )
            }
            TemporaryVariableType.ITEM -> {
                val material = CommandValueRules.material(node.string("item"), allowAir = false)
                    ?: error("invalid item")
                TemporaryValue(type, item = material.name, itemData = node.string("itemData"))
            }
            TemporaryVariableType.BLOCK -> {
                val material = CommandValueRules.placementMaterial(node.string("block"))
                    ?: error("invalid block")
                TemporaryValue(type, block = material.name)
            }
            TemporaryVariableType.ENTITY -> {
                val targetSpec = node.temporaryEntityTargetSpec
                val entityId = if (targetSpec != null) {
                    resolveTargetSpec(targetSpec, session, session.context)?.uniqueId
                } else {
                    node.string("entityId").takeIf(String::isNotBlank)
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                }
                // 定義時点で実体が見つからなくても一時値そのものは保存します。
                // 死亡・別ワールド・未ロードは参照時の「対象なし」と同じ契約で扱い、
                // 後続ノードだけをスキップできるようにします。
                TemporaryValue(type, entityId = entityId)
            }
            TemporaryVariableType.SOUND -> {
                if (!CommandValueRules.isSoundId(node.string("sound"))) error("invalid sound")
                val volume = resolveNumber(node.string("volume", "1.0"), session)
                    ?.takeIf { it.isFinite() && it in 0.0..34.0 } ?: error("invalid volume")
                val pitch = resolveNumber(node.string("pitch", "1.0"), session)
                    ?.takeIf { it.isFinite() && it in 0.5..2.0 } ?: error("invalid pitch")
                TemporaryValue(type, sound = node.string("sound"), volume = volume, pitch = pitch)
            }
            TemporaryVariableType.EFFECT -> {
                if (!CommandValueRules.isEffectId(node.string("effect"))) error("invalid effect")
                val level = resolveNumber(node.string("level", "1"), session)
                    ?.takeIf { it == kotlin.math.floor(it) && it in 1.0..255.0 }
                    ?.toInt() ?: error("invalid level")
                val seconds = resolveNumber(node.string("seconds", "30"), session)
                    ?.takeIf { it == kotlin.math.floor(it) && it in 1.0..86_400.0 }
                    ?.toInt() ?: error("invalid seconds")
                TemporaryValue(type, effect = node.string("effect"), level = level, seconds = seconds)
            }
        }
        session.temporaries[name] = value
        true
    }.getOrDefault(false)

    private fun parseAssignedValue(type: VariableType, raw: String, session: ExecutionSession): WorldVariableValue {        return when (type) {
            VariableType.NUMBER -> WorldVariableValue(
                VariableType.NUMBER,
                numberValue = resolveNumber(raw, session) ?: error("number must be finite"),
            )
            VariableType.STRING -> WorldVariableValue(
                VariableType.STRING,
                stringValue = resolveText(raw, session) ?: error("string variable is unavailable"),
            )
        }
    }

    private fun compareVariable(value: WorldVariableValue?, comparisonValue: Double, operator: String): Boolean {
        val actual = value?.takeIf { it.type == VariableType.NUMBER }?.numberValue ?: return false
        val comparison = actual.compareTo(comparisonValue)
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
    ): List<Entity> = nodeTarget?.let { resolveTargetSpecs(it, session, context) }
        ?: context?.target?.let { resolveTargetSpecs(it, session, context) }
        ?: context?.executor?.let { resolveTargetSpecs(it, session, context) }
        ?: emptyList()

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
        val resolvedSpec = resolveTargetFilter(spec, session) ?: return emptyList()
        val selectionOrigin = selectionOrigin(context, session) ?: return emptyList()
        val candidates: List<Entity> = when (resolvedSpec.kind) {
            TargetKind.TEMPORARY -> listOfNotNull(
                resolvedSpec.tempName
                    ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.ENTITY }
                    ?.entityId?.let(session.origin.world::getEntity),
            )
            TargetKind.NEAREST_PLAYER, TargetKind.NEARBY_PLAYERS, TargetKind.ALL_PLAYERS, TargetKind.RANDOM_PLAYER ->
                session.origin.world.players.filter { matches(it, resolvedSpec, session, context) }
            TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES ->
                session.origin.world.entities.filter { matches(it, resolvedSpec, session, context) }
            TargetKind.FIXED_ENTITY -> listOfNotNull(resolvedSpec.fixedEntityId?.let(session.origin.world::getEntity))
        }
        // 対象種別・固定UUID・一時変数の全経路をここで同じように検閲します。
        // 個別の操作実装で除外すると、新しいコマンド種別の追加時に抜け道になります。
        val inMyWorld = candidates
            .filter { it.world == session.origin.world }
            .filterNot(systemEntityRegistry::isSystemEntity)
        val sorted = when {
            resolvedSpec.kind == TargetKind.RANDOM_PLAYER || resolvedSpec.sort == me.awabi2048.kantancommander.model.TargetSort.RANDOM ->
                inMyWorld.shuffled()
            resolvedSpec.sort == me.awabi2048.kantancommander.model.TargetSort.FURTHEST ->
                inMyWorld.sortedByDescending { it.location.distanceSquared(selectionOrigin) }
            else -> inMyWorld.sortedBy { it.location.distanceSquared(selectionOrigin) }
        }
        val defaultLimit = when (resolvedSpec.kind) {
            TargetKind.NEAREST_PLAYER, TargetKind.RANDOM_PLAYER, TargetKind.NEAREST_ENTITY,
            TargetKind.FIXED_ENTITY, TargetKind.TEMPORARY -> 1
            else -> Int.MAX_VALUE
        }
        return sorted.take((resolvedSpec.limit ?: defaultLimit).coerceAtLeast(1))
    }

    /** 対象名・タグだけを実行直前に補間し、Registry IDや構造化値は型を保ちます。 */
    private fun resolveTargetFilter(spec: TargetSpec, session: ExecutionSession): TargetSpec? {
        fun resolve(value: String?): String? {
            if (value == null || value.isBlank()) return null
            return resolveText(value, session)
        }
        val tag = resolve(spec.tag)
        val name = resolve(spec.name)
        if ((spec.tag?.isNotBlank() == true && tag == null) ||
            (spec.name?.isNotBlank() == true && name == null)
        ) return null
        return spec.copy(tag = tag, name = name)
    }

    private fun matches(
        entity: Entity,
        spec: TargetSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
    ): Boolean {
        if (spec.entityType != null && entity.type.key.toString() != spec.entityType) return false
        if (spec.name != null && entity.name != spec.name) return false
        if (spec.tag != null && spec.tag !in entity.scoreboardTags) return false
        val origin = searchOriginFor(spec, context, session) ?: return false
        val distance = entity.location.distance(origin)
        if (spec.minimumDistance != null && distance < spec.minimumDistance) return false
        if (spec.maximumDistance != null && distance > spec.maximumDistance) return false
        if (!withinSelectorAxis(entity.location.x, origin.x, spec.dx)) return false
        if (!withinSelectorAxis(entity.location.y, origin.y, spec.dy)) return false
        if (!withinSelectorAxis(entity.location.z, origin.z, spec.dz)) return false
        return spec.gameMode == null || entity is Player && entity.gameMode.name.equals(spec.gameMode, true)
    }

    /** dx/dy/dzは基準ブロックを含むバニラセレクターの直方体として判定します。 */
    private fun withinSelectorAxis(value: Double, origin: Double, extent: Double?): Boolean {
        if (extent == null) return true
        val minimum = kotlin.math.floor(origin)
        return value >= minimum && value < minimum + extent + 1.0
    }

    private fun selectionOrigin(context: ExecutionContextSpec?, session: ExecutionSession): Location? =
        when (val position = context?.position) {
            null -> session.origin
            else -> when (position.kind) {
                PositionKind.CAPTURED, PositionKind.COORDINATES,
                PositionKind.DISK, PositionKind.MYWORLD_SPAWN, PositionKind.TEMPORARY ->
                    resolvePosition(position, session, context)
                // TARGETはテレポート先などのコマンド固有設定でのみ使用します。
                PositionKind.TARGET -> null
            }
        }

    /**
     * 対象探索の基準位置です。「探索の基準」設定があれば最優先し、
     * なければ従来の選択原点（コンテキスト位置または制御ブロック位置）を用います。
     */
    private fun searchOriginFor(spec: TargetSpec, context: ExecutionContextSpec?, session: ExecutionSession): Location? {
        val search = spec.searchOrigin?.takeIf { it.hasAnySetting() }
        if (search != null) {
            search.positionTemp?.takeIf(String::isNotBlank)?.let { name ->
                val temp = session.temporaries[TemporaryTemplate.normalized(name)]
                    ?.takeIf { it.type == TemporaryVariableType.LOCATION }
                    ?.location ?: return null
                return Location(session.origin.world, temp.x, temp.y, temp.z, temp.yaw, temp.pitch)
            }
            search.position?.let { return resolvePosition(it, session, context) }
        }
        return selectionOrigin(context, session)
    }

    /** GUI設定ではなく、実行エンジンが現在保持している内部状態だけを返します。 */
    private fun effectiveContext(session: ExecutionSession): ExecutionContextSpec? = session.context

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
        PositionKind.TEMPORARY -> {
            val temp = spec.tempName
                ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.LOCATION }
                    ?.location ?: return null
            Location(session.origin.world, temp.x, temp.y, temp.z, temp.yaw, temp.pitch)
        }
        // TARGETはテレポート先などのコマンド固有設定で解決するため、通常の
        // 実行位置へ渡された場合は静的検証と同じく未解決として扱います。
        PositionKind.TARGET -> null
        PositionKind.MYWORLD_SPAWN -> session.origin.world.spawnLocation
    }

    private fun applyFacing(
        destination: Location,
        facing: me.awabi2048.kantancommander.model.FacingSpec,
        context: ExecutionContextSpec?,
        session: ExecutionSession,
    ) {
        when (facing.kind) {
            FacingKind.ROTATION, FacingKind.CAPTURED -> {
                destination.yaw = facing.yaw ?: destination.yaw
                destination.pitch = facing.pitch ?: destination.pitch
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
            FacingKind.TEMPORARY -> {
                val temp = facing.tempName
                    ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.LOCATION }
                    ?.location ?: return
                faceLocation(destination, Location(destination.world, temp.x, temp.y, temp.z))
            }
            FacingKind.MYWORLD_SPAWN -> faceLocation(destination, session.origin.world.spawnLocation)
        }
    }

    private fun resolveText(raw: String, session: ExecutionSession): String? {
        // 一時変数 `%{name}%` を先に展開し、残った `${name}` をワールド内変数として展開します。
        val tempExpanded = if (TemporaryTemplate.references(raw).isEmpty() && !raw.contains('%')) {
            raw
        } else {
            TemporaryTemplate.interpolateText(raw) { name ->
                when (name) {
                    SystemVariableNames.CURRENT_LOOP_COUNT -> session.currentLoopCount?.toString()
                    else -> session.temporaries[TemporaryTemplate.normalized(name)]?.let(::stringifyTemporary)
                }
            } ?: return null
        }
        return VariableTemplate.interpolateText(tempExpanded) { name ->
            when (name) {
                SystemVariableNames.CURRENT_LOOP_COUNT -> session.currentLoopCount?.toString()
                else -> plugin.variables.get(session.worldId, name)?.let(VariableTemplate::stringify)
            }
        }
            ?: tempExpanded.takeIf {
                VariableTemplate.references(it).isEmpty() && !VariableTemplate.hasMalformedReference(it) &&
                    TemporaryTemplate.references(it).isEmpty() && !TemporaryTemplate.hasMalformedReference(it)
            }
    }

    private fun resolveNumber(raw: String, session: ExecutionSession): Double? {
        val tempExpanded = if (TemporaryTemplate.references(raw).isEmpty() && !raw.contains('%')) {
            raw
        } else {
            TemporaryTemplate.interpolateText(raw) { name ->
                when (name) {
                    SystemVariableNames.CURRENT_LOOP_COUNT -> session.currentLoopCount?.toString()
                    else -> session.temporaries[TemporaryTemplate.normalized(name)]
                        ?.takeIf { it.type == TemporaryVariableType.NUMBER }
                        ?.numberValue?.takeIf(Double::isFinite)?.toString()
                }
            } ?: return null
        }
        val expanded = if (VariableTemplate.references(tempExpanded).isEmpty()) tempExpanded else {
            VariableTemplate.interpolateText(tempExpanded) { name ->
                when (name) {
                    SystemVariableNames.CURRENT_LOOP_COUNT -> session.currentLoopCount?.toString()
                    // 数値欄は文字列変数を暗黙にDoubleへ変換しません。保存値がたまたま
                    // 数字だけでも、定義型を越境するとGUI・実行・出力で意味が分岐するためです。
                    else -> plugin.variables.get(session.worldId, name)
                        ?.takeIf { it.type == VariableType.NUMBER }
                        ?.numberValue
                        ?.takeIf(Double::isFinite)
                        ?.toString()
                }
            } ?: return null
        }
        return expanded.toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    /**
     * 実行時にだけ確定する一時参照の欠損を、通常のノード失敗と分けて判定します。
     *
     * 静的検証は「その経路で定義が存在すること」までしか保証できません。特に
     * ENTITYは定義後に死亡・別ワールド移動・アンロードが起こるため、
     * executeImmediateへ渡してfalseを返すだけではスクリプト全体を停止してしまいます。
     * ここで参照先を確認し、呼び出し側がノード単位のSKIPPEDとして後続へ進めます。
     */
    private fun hasUnavailableTemporaryReference(node: CommandNode, session: ExecutionSession): Boolean {
        fun scalarReferencesAvailable(raw: String): Boolean =
            TemporaryTemplate.references(raw).all { name ->
                val value = session.temporaries[TemporaryTemplate.normalized(name)]
                value != null && value.type in setOf(TemporaryVariableType.NUMBER, TemporaryVariableType.STRING)
            }

        // テキスト／数値欄に現れる `%{name}%` はスカラーだけを許可します。
        // POSITION等を空文字へ変換して実行を続けると、型エラーが成功に見えます。
        if (node.params.values.any { !scalarReferencesAvailable(it) }) return true

        fun entityAvailable(name: String?): Boolean {
            val value = name?.takeIf(String::isNotBlank)
                ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                ?: return false
            if (value.type != TemporaryVariableType.ENTITY) return false
            val id = value.entityId ?: return false
            val entity = session.origin.world.getEntity(id) ?: return false
            return entity.isValid &&
                !entity.isDead &&
                entity.world == session.origin.world &&
                !systemEntityRegistry.isSystemEntity(entity)
        }

        fun positionAvailable(name: String?): Boolean {
            val value = name?.takeIf(String::isNotBlank)
                ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                ?: return false
            return value.type == TemporaryVariableType.LOCATION && value.location != null
        }

        fun targetUnavailable(spec: TargetSpec?): Boolean =
            spec?.kind == TargetKind.TEMPORARY && !entityAvailable(spec.tempName)

        fun positionUnavailable(spec: PositionSpec?): Boolean =
            spec?.kind == PositionKind.TEMPORARY && !positionAvailable(spec.tempName)

        fun facingUnavailable(spec: me.awabi2048.kantancommander.model.FacingSpec?): Boolean =
            spec?.kind == FacingKind.TEMPORARY && !positionAvailable(spec.tempName)

        val effective = effectiveContext(session)
        return listOf(
            node.targetSpec,
            node.secondaryTargetSpec,
            node.destinationTargetSpec,
            effective?.target,
            effective?.executor,
        ).any(::targetUnavailable) || listOf(
            node.destinationSpec,
            node.conditionPositionSpec,
            node.blockPositionSpec,
            node.blockFromSpec,
            node.blockToSpec,
            node.soundPositionSpec,
            node.particlePositionSpec,
            node.summonPositionSpec,
            effective?.position,
        ).any(::positionUnavailable) || listOf(
            node.destinationFacingSpec,
            effective?.facing,
        ).any(::facingUnavailable) || listOfNotNull(
            node.targetSpec?.searchOrigin,
            node.secondaryTargetSpec?.searchOrigin,
            node.destinationTargetSpec?.searchOrigin,
            effective?.target?.searchOrigin,
            effective?.executor?.searchOrigin,
        ).any { search ->
            search.positionTemp?.let { !positionAvailable(it) } == true ||
                positionUnavailable(search.position)
        } || listOfNotNull(
            node.itemTempRef to TemporaryVariableType.ITEM,
            node.blockTempRef to TemporaryVariableType.BLOCK,
            node.soundTempRef to TemporaryVariableType.SOUND,
            node.effectTempRef to TemporaryVariableType.EFFECT,
        ).any { (name, expected) ->
            name?.takeIf(String::isNotBlank)?.let {
                session.temporaries[TemporaryTemplate.normalized(it)]?.type != expected
            } == true
        }
    }

    /** 一時変数値を文字列へ変換します。複合型は暗黙に空文字へ変換しません。 */
    private fun stringifyTemporary(value: TemporaryValue): String? = when (value.type) {
        TemporaryVariableType.NUMBER -> value.numberValue?.let {
            if (it.isFinite() && it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        }
        TemporaryVariableType.STRING -> value.stringValue
        else -> null
    }

    private fun displayTiming(node: CommandNode, session: ExecutionSession): DisplayTextTiming {
        fun value(key: String, fallback: Double): Double =
            resolveNumber(node.string(key), session)
                ?.takeIf(CommandValueRules::isDisplayTimeSeconds)
                ?: fallback
        return DisplayTextTiming(
            value("fadeInSeconds", 1.0),
            value("staySeconds", 3.0),
            value("fadeOutSeconds", 1.0),
        )
    }

    private fun legacyText(raw: String): Component =
        LegacyComponentSerializer.legacySection().deserialize(raw.replace('&', '§'))

    /** 数値テンプレートを実行値の正のIntへ変換する共通境界です。 */
    private fun resolvePositiveInt(raw: String, session: ExecutionSession): Int? {
        val value = resolveNumber(raw, session) ?: return null
        if (value != kotlin.math.floor(value) || value <= 0.0 || value > Int.MAX_VALUE.toDouble()) return null
        return value.toInt()
    }

    /** プレイヤー状態の所持判定は、メインハンド由来のItemStack情報も尊重します。 */
    private fun playerHasItem(player: Player, itemId: String, itemData: String): Boolean {
        val material = CommandValueRules.material(itemId, allowAir = false) ?: return false
        val required = itemData.takeIf(String::isNotBlank)?.let(ItemStackCodec::decode)
        return player.inventory.contents.any { stack ->
            stack != null && !stack.type.isAir && stack.type == material &&
                (required == null || stack.isSimilar(required))
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
        notifyCreatorOfFailure(session)
        done(false)
    }

    /**
     * 実行途中の失敗を最上位プログラムの作成者へ一度だけ通知します。
     * DISK_CALLの入れ子や非同期WAITから同じ失敗経路へ戻っても、作成者への通知が
     * 重複しないようセッション単位で抑制します。オフラインの場合は送信先がないため、
     * 実行ログだけを正本として通知を捨てます。
     */
    private fun notifyCreatorOfFailure(session: ExecutionSession) {
        if (session.failureNotified) return
        session.failureNotified = true
        val creator = plugin.server.getPlayer(session.creatorId) ?: return
        creator.sendMessage("§cかんたんコマンダープログラム【${session.programName}】の実行に失敗しました。")
        creator.playSound(
            creator.location,
            "minecraft:block.note_block.bit",
            SoundCategory.MASTER,
            1.0f,
            0.5f,
        )
    }

    /** 即時ノード処理結果。Particle上限超過は失敗ではなく、そのノードだけを省略します。 */
    private enum class ImmediateExecutionResult { SUCCESS, SKIPPED, FAILED }

    /** 上限超過を通常の実行例外と区別する内部制御例外です。 */
    private class ParticleQuotaRejected : RuntimeException()

    /** ノード処理結果。参照先が無い場合だけ、失敗と区別して後続へ進めます。 */
    private enum class NodeExecutionOutcome { SUCCESS, SKIPPED, FAILED }

    private data class ExecutionSession(
        val rootId: UUID,
        val origin: Location,
        val actor: Player?,
        /** 入れ子呼び出しでも通知先を変えないため、最上位スクリプトの作成者を保持します。 */
        val creatorId: UUID,
        val programName: String,
        val budget: Int,
        val maxDepth: Int,
        val worldId: UUID,
        var executed: Int = 0,
        var failureNotified: Boolean = false,
        var context: ExecutionContextSpec? = null,
        var previousContext: ExecutionContextSpec? = null,
        val loops: MutableList<LoopFrame> = mutableListOf(),
        var currentLoopCount: Long? = null,
        /** 一時変数の実行内保持域です。実行終了時に破棄し、DISK_CALL越境では隔離します。 */
        val temporaries: MutableMap<String, TemporaryValue> = linkedMapOf(),
    )

    private data class LoopFrame(
        val startId: UUID,
        val endId: UUID,
        val limit: Long,
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

    /**
     * 回数指定ループの継続判定を一箇所へ集約します。
     *
     * 実行器とテストがそれぞれ境界条件を持つと、1回多く実行する差異が
     * 保存データや出力先によって生じます。現在回数が上限未満のときだけ
     * 次の反復へ進む、という新仕様の境界を共通化します。
     */
    fun shouldRunNextLoopIteration(currentCount: Long, limit: Long): Boolean =
        currentCount > 0 && limit > 0 && currentCount < limit

    /** 次の回数を安全に計算し、符号付き64bitの上限では停止できるようにします。 */
    fun nextLoopCount(currentCount: Long): Long? =
        currentCount.takeIf { it < Long.MAX_VALUE }?.plus(1)

    fun mergeContexts(
        inherited: ExecutionContextSpec?,
        override: ExecutionContextSpec?,
    ): ExecutionContextSpec? {
        if (override == null || !override.hasAnySetting()) return inherited
        if (inherited == null) return override
        return ExecutionContextSpec(
            executor = override.executor ?: inherited.executor,
            target = override.target ?: inherited.target,
            position = override.position ?: inherited.position,
            facing = override.facing ?: inherited.facing,
        )
    }

}
