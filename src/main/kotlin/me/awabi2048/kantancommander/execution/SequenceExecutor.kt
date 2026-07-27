package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
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
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Duration
import java.util.UUID
import java.util.logging.Level
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.kantancommander.item.ItemStackCodec

class SequenceExecutor(private val plugin: KantanCommanderPlugin) {
    private val running = mutableSetOf<UUID>()

    fun execute(scriptId: UUID, origin: Location, actor: Player? = null, callback: (Boolean) -> Unit = {}) {
        val worldData = if (plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) {
            MyWorldManagerApi.getWorldRepository()?.findByWorldName(origin.world.name)
        } else null
        if (worldData == null) {
            plugin.logger.warning("[KantanCommander] rejected disk=$scriptId reason=outside_myworld world=${origin.world.name}")
            return callback(false)
        }
        if (!running.add(scriptId)) return callback(false)
        val script = plugin.scripts.load(scriptId) ?: return finish(scriptId, false, callback)
        val session = ExecutionSession(
            rootId = scriptId,
            origin = origin.clone(),
            actor = actor,
            budget = plugin.config.getInt("execution.maximum-command-count", 1024).coerceAtLeast(1),
            maxDepth = plugin.config.getInt("execution.maximum-disk-call-depth", 3).coerceAtLeast(0),
            worldId = worldData.uuid,
        )
        plugin.logger.info("[KantanCommander] start disk=$scriptId world=${origin.world.name} location=${origin.blockX},${origin.blockY},${origin.blockZ}")
        runGraph(script, script.graph, session, 0) { success ->
            plugin.logger.info("[KantanCommander] finish disk=$scriptId success=$success executed=${session.executed}/${session.budget}")
            finish(scriptId, success, callback)
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
        val node = graph.nodes[nodeId] ?: return stop(session, script, nodeId, "missing_node", done)
        if (node.type == CommandType.FOR_START && node.trueNext == node.pairedNodeId) {
            val after = node.pairedNodeId?.let(graph.nodes::get)?.next
            return runNode(script, graph, after, session, depth, done)
        }
        if (session.executed >= session.budget) return stop(session, script, nodeId, "command_limit", done)
        session.executed++
        plugin.logger.info("[KantanCommander] execute root=${session.rootId} disk=${script.id} node=${node.id} type=${node.type} count=${session.executed}/${session.budget}")

        val next: (UUID?, Boolean) -> Unit = { target, success ->
            session.results[node.id] = success
            runNode(script, graph, target, session, depth, done)
        }
        when (node.type) {
            CommandType.WAIT -> plugin.server.scheduler.runTaskLater(
                plugin,
                Runnable { next(node.next, true) },
                node.int("ticks", 20).coerceAtLeast(1).toLong(),
            )
            CommandType.CONDITION -> {
                val rawResult = evaluateCondition(node, session, node.contextOverride ?: session.context)
                val result = if (node.boolean("inverted")) !rawResult else rawResult
                plugin.logger.info("[KantanCommander] condition disk=${script.id} node=${node.id} result=$result")
                next(if (result) node.trueNext else node.falseNext, true)
            }
            CommandType.CONTEXT -> {
                session.context = contextFrom(node)
                next(node.next, true)
            }
            CommandType.DISK_CALL -> runDiskCall(node, session, depth) { success -> next(node.next, success) }
            CommandType.VARIABLE -> next(node.next, executeVariable(node, session))
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
        val endId = node.pairedNodeId ?: return stop(session, script, node.id, "missing_for_end", done)
        val start = resolveForValue(node, "start", session) ?: return stop(session, script, node.id, "invalid_for_start", done)
        val end = resolveForValue(node, "end", session) ?: return stop(session, script, node.id, "invalid_for_end", done)
        val step = resolveForValue(node, "step", session) ?: return stop(session, script, node.id, "invalid_for_step", done)
        if (step == 0L) return stop(session, script, node.id, "zero_for_step", done)
        if (!withinForRange(start, end, step)) {
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
            ?: return stop(session, script, node.id, "for_frame_missing", done)
        val startNode = graph.nodes[frame.startId]
            ?: return stop(session, script, node.id, "for_start_missing", done)
        val currentEnd = resolveForValue(startNode, "end", session)
            ?: return stop(session, script, node.id, "invalid_for_end", done)
        val currentStep = resolveForValue(startNode, "step", session)
            ?: return stop(session, script, node.id, "invalid_for_step", done)
        if (currentStep == 0L) return stop(session, script, node.id, "zero_for_step", done)
        val nextValue = try {
            Math.addExact(frame.value, currentStep)
        } catch (_: ArithmeticException) {
            return stop(session, script, node.id, "for_overflow", done)
        }
        session.context = frame.startContext
        if (withinForRange(nextValue, currentEnd, currentStep)) {
            frame.value = nextValue
            frame.end = currentEnd
            frame.step = currentStep
            frame.count++
            session.currentIterationValue = frame.value
            session.currentLoopCount = frame.count
            runNode(script, graph, startNode.trueNext, session, depth, done)
        } else {
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
            ?: return stop(session, script, node.id, "break_outside_for", done)
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
            ?: return stop(session, script, node.id, "continue_outside_for", done)
        session.context = frame.startContext
        runNode(script, graph, frame.endId, session, depth, done)
    }

    private fun resolveForValue(node: CommandNode, prefix: String, session: ExecutionSession): Long? {
        val source = node.string("${prefix}Source", "FIXED")
        val value = node.string("${prefix}Value", if (prefix == "step") "1" else "0")
        return when (source) {
            "FIXED" -> value.toLongOrNull()
            "TEMPORARY" -> session.temporaryVariables[value]?.integerValue
            else -> null
        }
    }

    private fun withinForRange(value: Long, end: Long, step: Long) =
        if (step > 0) value <= end else value >= end

    private fun runDiskCall(node: CommandNode, session: ExecutionSession, depth: Int, done: (Boolean) -> Unit) {
        if (depth >= session.maxDepth) {
            plugin.logger.warning("[KantanCommander] disk-call-depth root=${session.rootId} node=${node.id} depth=$depth max=${session.maxDepth}")
            return done(false)
        }
        val snapshot = node.snapshot ?: return done(false)
        val synthetic = DiskScript(name = "snapshot", owner = session.actor?.uniqueId ?: UUID(0, 0), graph = snapshot)
        runGraph(synthetic, snapshot, session, depth + 1, done)
    }

    private fun executeImmediate(node: CommandNode, session: ExecutionSession): Boolean = runCatching {
                val effectiveContext = node.contextOverride ?: session.context
                val targets = resolveTargets(effectiveContext, node.targetSpec, session)
                val effectiveOrigin = effectiveContext?.position?.let { resolvePosition(it, session, effectiveContext) } ?: session.origin
        when (node.type) {
            CommandType.TELEPORT -> {
                if (targets.isEmpty()) return false
                val destination = node.destinationTargetSpec
                    ?.let { resolveTargetSpec(it, session)?.location }
                    ?: node.destinationSpec?.let { resolvePosition(it, session) }
                    ?: effectiveOrigin.clone()
                effectiveContext?.facing?.let { facing ->
                    if (facing.kind == FacingKind.ROTATION || facing.kind == FacingKind.CAPTURED) {
                        destination.yaw = facing.yaw ?: destination.yaw
                        destination.pitch = facing.pitch ?: destination.pitch
                    }
                }
                targets.all { it.teleport(destination.clone()) }
            }
            CommandType.GIVE_ITEM -> {
                val players = targets.filterIsInstance<Player>()
                if (players.isEmpty()) return false
                val material = Material.matchMaterial(node.string("item", "minecraft:stone")) ?: return false
                val stack = ItemStackCodec.decode(node.string("itemData"))
                    ?: ItemStack(material)
                stack.amount = node.int("count", 1).coerceIn(1, stack.maxStackSize)
                players.all { player -> player.inventory.addItem(stack.clone()).isEmpty() }
            }
            CommandType.ENTITY_ACTION -> {
                if (targets.isEmpty()) return false
                when (node.string("action", "ride")) {
                    "ride" -> {
                        val vehicle = resolveTarget(effectiveContext, node.secondaryTargetSpec, session)
                            ?: resolveSelector(node.string("other"), session)
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
                    "title" -> players.forEach { player -> player.showTitle(Title.title(
                            text,
                            Component.empty(),
                            Title.Times.times(
                                Duration.ofMillis(node.int("fadeIn", 10).coerceAtLeast(0) * 50L),
                                Duration.ofMillis(node.int("stay", 60).coerceAtLeast(0) * 50L),
                                Duration.ofMillis(node.int("fadeOut", 10).coerceAtLeast(0) * 50L),
                            ),
                        ))
                    }
                    "actionbar" -> players.forEach { it.sendActionBar(text) }
                    else -> players.forEach { it.sendMessage(text) }
                }
                true
            }
            else -> true
        }
    }.onFailure {
        plugin.logger.log(Level.WARNING, "[KantanCommander] node failed root=${session.rootId} node=${node.id}", it)
    }.getOrDefault(false)

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
                val origin = context?.position?.let { resolvePosition(it, session, context) } ?: session.origin
                val location = parseLocation(node.string("position", "~ ~ ~"), origin)
                location.world == session.origin.world && location.block.type == Material.matchMaterial(node.string("block"))
            }
            ConditionKind.ITEM_POSSESSION -> {
                val player = target as? Player ?: return false
                val material = Material.matchMaterial(node.string("item")) ?: return false
                player.inventory.contains(material, node.int("count", 1).coerceAtLeast(1))
            }
        }
    }

    private fun executeVariable(node: CommandNode, session: ExecutionSession): Boolean = runCatching {
        val name = node.string("name")
        val operation = VariableOperation.valueOf(node.string("operation", VariableOperation.SET.name))
        val scope = VariableScope.valueOf(node.string("scope", VariableScope.TEMPORARY.name))
        if (operation == VariableOperation.CLEAR) return removeVariable(session, name, scope)
        val current = getVariable(session, name, scope)
        val type = VariableType.valueOf(node.string("type", current?.type?.name ?: VariableType.BOOLEAN.name))
        val value = when (operation) {
            VariableOperation.SET -> parseVariable(type, node.string("value"), session)
            VariableOperation.ADD, VariableOperation.SUBTRACT -> {
                val sign = if (operation == VariableOperation.ADD) 1 else -1
                when (type) {
                    VariableType.INTEGER -> WorldVariableValue(type, integerValue = (current?.integerValue ?: 0) + sign * node.string("value", "0").toLong())
                    VariableType.DECIMAL -> WorldVariableValue(type, decimalValue = (current?.decimalValue ?: 0.0) + sign * node.string("value", "0").toDouble())
                    else -> return false
                }
            }
            VariableOperation.TOGGLE -> WorldVariableValue(VariableType.BOOLEAN, booleanValue = !(current?.booleanValue ?: false))
            VariableOperation.STORE_POSITION -> WorldVariableValue(
                VariableType.POSITION,
                position = SavedPosition(session.origin.x, session.origin.y, session.origin.z, session.origin.yaw, session.origin.pitch),
            )
            VariableOperation.STORE_TARGET -> WorldVariableValue(
                VariableType.ENTITY,
                entityId = resolveTarget(session.context, node.targetSpec, session)?.uniqueId ?: return false,
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

    private fun removeVariable(session: ExecutionSession, name: String, scope: VariableScope): Boolean =
        if (scope == VariableScope.WORLD) plugin.variables.remove(session.worldId, name)
        else session.temporaryVariables.remove(name) != null

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
        VariableType.DECIMAL -> WorldVariableValue(type, decimalValue = resolved.toDouble())
        VariableType.TEXT -> WorldVariableValue(type, textValue = resolved)
        VariableType.POSITION -> parseLocation(resolved, session.origin).let {
            WorldVariableValue(type, position = SavedPosition(it.x, it.y, it.z, it.yaw, it.pitch))
        }
        VariableType.ENTITY -> WorldVariableValue(type, entityId = UUID.fromString(resolved))
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
        nodeTarget ?: context?.target ?: context?.executor ?: TargetSpec(TargetKind.ACTIVATOR),
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
        val candidates: List<Entity> = when (spec.kind) {
            TargetKind.EXECUTOR -> listOfNotNull(
                context?.executor
                    ?.takeUnless { it.kind == TargetKind.EXECUTOR }
                    ?.let { resolveTargetSpec(it, session, context) }
                    ?: session.actor
            )
            TargetKind.ACTIVATOR -> listOfNotNull(session.actor)
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
        val sorted = when {
            spec.kind == TargetKind.RANDOM_PLAYER || spec.sort == me.awabi2048.kantancommander.model.TargetSort.RANDOM ->
                candidates.shuffled()
            spec.sort == me.awabi2048.kantancommander.model.TargetSort.FURTHEST ->
                candidates.sortedByDescending { it.location.distanceSquared(session.origin) }
            else -> candidates.sortedBy { it.location.distanceSquared(session.origin) }
        }
        val defaultLimit = when (spec.kind) {
            TargetKind.NEAREST_PLAYER, TargetKind.RANDOM_PLAYER, TargetKind.NEAREST_ENTITY,
            TargetKind.EXECUTOR, TargetKind.ACTIVATOR, TargetKind.INHERITED_TARGET, TargetKind.FIXED_ENTITY -> 1
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
        if (spec.excludeActivator && entity == session.actor) return false
        if (spec.excludeExecutor && entity == context?.executor?.let { resolveTargetSpec(it, session, context) }) return false
        if (spec.entityType != null && entity.type.key.toString() != spec.entityType) return false
        if (spec.name != null && entity.name != spec.name) return false
        if (spec.tag != null && spec.tag !in entity.scoreboardTags) return false
        val distance = entity.location.distance(session.origin)
        if (spec.minimumDistance != null && distance < spec.minimumDistance) return false
        if (spec.maximumDistance != null && distance > spec.maximumDistance) return false
        return spec.gameMode == null || entity is Player && entity.gameMode.name.equals(spec.gameMode, true)
    }

    private fun resolveSelector(selected: String, session: ExecutionSession): Entity? {
        return when (selected) {
            "@s", "actor" -> session.actor
            "@p" -> session.origin.world.players.minByOrNull { it.location.distanceSquared(session.origin) }
            else -> runCatching { UUID.fromString(selected) }.getOrNull()
                ?.let(session.origin.world::getEntity)
        }
    }

    private fun contextFrom(node: CommandNode) = node.contextOverride ?: ExecutionContextSpec()

    private fun resolvePosition(
        spec: PositionSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec? = session.context,
    ): Location = when (spec.kind) {
        PositionKind.CAPTURED, PositionKind.COORDINATES -> Location(
            session.origin.world,
            spec.x ?: session.origin.x,
            spec.y ?: session.origin.y,
            spec.z ?: session.origin.z,
            spec.yaw ?: session.origin.yaw,
            spec.pitch ?: session.origin.pitch,
        )
        PositionKind.DISK -> session.origin.clone()
        PositionKind.EXECUTOR -> resolveTargetSpec(context?.executor ?: TargetSpec(TargetKind.ACTIVATOR), session, context)?.location ?: session.origin
        PositionKind.TARGET -> resolveTargetSpec(context?.target ?: TargetSpec(TargetKind.ACTIVATOR), session, context)?.location ?: session.origin
        PositionKind.MYWORLD_SPAWN -> session.origin.world.spawnLocation
        PositionKind.TEMPORARY_VARIABLE -> session.temporaryVariables[spec.variable.orEmpty()]?.position?.let {
            Location(session.origin.world, it.x, it.y, it.z, it.yaw, it.pitch)
        } ?: session.origin
        PositionKind.WORLD_VARIABLE -> plugin.variables.get(session.worldId, spec.variable.orEmpty())?.position?.let {
            Location(session.origin.world, it.x, it.y, it.z, it.yaw, it.pitch)
        } ?: session.origin
    }

    private fun parseLocation(raw: String, origin: Location): Location {
        val parts = raw.trim().split(Regex("\\s+"))
        fun coordinate(part: String?, base: Double): Double =
            if (part == null || part == "~") base else if (part.startsWith("~")) base + part.drop(1).toDoubleOrNull().orZero() else part.toDoubleOrNull() ?: base
        return Location(origin.world, coordinate(parts.getOrNull(0), origin.x), coordinate(parts.getOrNull(1), origin.y), coordinate(parts.getOrNull(2), origin.z))
    }

    private fun Double?.orZero() = this ?: 0.0
    private fun compare(left: Int, right: Int, operator: String) = when (operator) {
        "==" -> left == right
        "!=" -> left != right
        ">" -> left > right
        "<" -> left < right
        "<=" -> left <= right
        else -> left >= right
    }

    private fun stop(session: ExecutionSession, script: DiskScript, nodeId: UUID, reason: String, done: (Boolean) -> Unit) {
        plugin.logger.warning("[KantanCommander] forced-stop root=${session.rootId} disk=${script.id} node=$nodeId reason=$reason executed=${session.executed} limit=${session.budget} world=${session.origin.world.name}")
        done(false)
    }

    private fun finish(scriptId: UUID, success: Boolean, callback: (Boolean) -> Unit) {
        running.remove(scriptId)
        callback(success)
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
        val results: MutableMap<UUID, Boolean> = mutableMapOf(),
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
