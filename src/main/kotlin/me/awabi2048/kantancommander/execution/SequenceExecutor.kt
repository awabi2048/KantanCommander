package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskCallMode
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.SavedPosition
import me.awabi2048.kantancommander.model.VariableOperation
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
                val result = evaluateCondition(node, session)
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
            else -> next(node.next, executeImmediate(node, session))
        }
    }

    private fun runDiskCall(node: CommandNode, session: ExecutionSession, depth: Int, done: (Boolean) -> Unit) {
        if (depth >= session.maxDepth) {
            plugin.logger.warning("[KantanCommander] disk-call-depth root=${session.rootId} node=${node.id} depth=$depth max=${session.maxDepth}")
            return done(false)
        }
        val mode = runCatching { DiskCallMode.valueOf(node.string("mode")) }.getOrDefault(DiskCallMode.LIVE_REFERENCE)
        if (mode == DiskCallMode.SNAPSHOT) {
            val snapshot = node.snapshot ?: return done(false)
            val synthetic = DiskScript(name = "snapshot", owner = session.actor?.uniqueId ?: UUID(0, 0), graph = snapshot)
            return runGraph(synthetic, snapshot, session, depth + 1, done)
        }
        val targetId = runCatching { UUID.fromString(node.string("diskId")) }.getOrNull() ?: return done(false)
        val target = plugin.scripts.load(targetId) ?: return done(false)
        runGraph(target, target.graph, session, depth + 1, done)
    }

    private fun executeImmediate(node: CommandNode, session: ExecutionSession): Boolean = runCatching {
        val target = resolveTarget(node.contextOverride ?: session.context, node.targetSpec, session)
        val effectiveContext = node.contextOverride ?: session.context
        val effectiveOrigin = effectiveContext?.position?.let { resolvePosition(it, session) } ?: session.origin
        when (node.type) {
            CommandType.TELEPORT -> {
                val entity = target ?: return false
                val destination = parseLocation(node.string("destination", "~ ~ ~"), effectiveOrigin)
                effectiveContext?.facing?.let { facing ->
                    if (facing.kind == FacingKind.ROTATION || facing.kind == FacingKind.CAPTURED) {
                        destination.yaw = facing.yaw ?: destination.yaw
                        destination.pitch = facing.pitch ?: destination.pitch
                    }
                }
                entity.teleport(destination)
            }
            CommandType.GIVE_ITEM -> {
                val player = target as? Player ?: return false
                val material = Material.matchMaterial(node.string("item", "minecraft:stone")) ?: return false
                val leftovers = player.inventory.addItem(ItemStack(material, node.int("count", 1).coerceIn(1, material.maxStackSize)))
                leftovers.isEmpty()
            }
            CommandType.ENTITY_ACTION -> {
                val entity = target ?: return false
                when (node.string("action", "ride")) {
                    "ride" -> {
                        val vehicle = resolveSelector(node.string("other"), session) ?: return false
                        vehicle.addPassenger(entity)
                    }
                    "dismount" -> entity.leaveVehicle()
                    else -> false
                }
            }
            CommandType.DISPLAY_TEXT -> {
                val player = target as? Player ?: return false
                val text = Component.text(node.string("text"))
                when (node.string("mode", "tellraw")) {
                    "title" -> player.showTitle(Title.title(
                        text,
                        Component.empty(),
                        Title.Times.times(
                            Duration.ofMillis(node.int("fadeIn", 10).coerceAtLeast(0) * 50L),
                            Duration.ofMillis(node.int("stay", 60).coerceAtLeast(0) * 50L),
                            Duration.ofMillis(node.int("fadeOut", 10).coerceAtLeast(0) * 50L),
                        ),
                    ))
                    "actionbar" -> player.sendActionBar(text)
                    else -> player.sendMessage(text)
                }
                true
            }
            else -> true
        }
    }.onFailure {
        plugin.logger.log(Level.WARNING, "[KantanCommander] node failed root=${session.rootId} node=${node.id}", it)
    }.getOrDefault(false)

    private fun evaluateCondition(node: CommandNode, session: ExecutionSession): Boolean {
        val kind = runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrDefault(ConditionKind.TARGET_EXISTS)
        val target = resolveTarget(session.context, node.targetSpec, session)
        return when (kind) {
            ConditionKind.TARGET_EXISTS -> target != null
            ConditionKind.ENTITY_STATE -> when (node.string("state")) {
                "sneaking" -> target is Player && target.isSneaking
                "on_ground" -> target?.isOnGround == true
                else -> false
            }
            ConditionKind.VARIABLE_STATE -> compareVariable(
                plugin.variables.get(session.worldId, node.string("variable")),
                node.string("value"),
                node.string("operator", "=="),
            )
            ConditionKind.BLOCK_STATE -> {
                val location = parseLocation(node.string("position", "~ ~ ~"), session.origin)
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
        if (operation == VariableOperation.CLEAR) return plugin.variables.remove(session.worldId, name)
        val current = plugin.variables.get(session.worldId, name)
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
        plugin.variables.set(session.worldId, name, value)
        true
    }.getOrDefault(false)

    private fun parseVariable(type: VariableType, raw: String, session: ExecutionSession): WorldVariableValue = when (type) {
        VariableType.BOOLEAN -> WorldVariableValue(type, booleanValue = raw.toBooleanStrict())
        VariableType.INTEGER -> WorldVariableValue(type, integerValue = raw.toLong())
        VariableType.DECIMAL -> WorldVariableValue(type, decimalValue = raw.toDouble())
        VariableType.TEXT -> WorldVariableValue(type, textValue = raw)
        VariableType.POSITION -> parseLocation(raw, session.origin).let {
            WorldVariableValue(type, position = SavedPosition(it.x, it.y, it.z, it.yaw, it.pitch))
        }
        VariableType.ENTITY -> WorldVariableValue(type, entityId = UUID.fromString(raw))
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
        resolveTargetSpec(nodeTarget ?: context?.target ?: context?.executor ?: TargetSpec(TargetKind.ACTIVATOR), session)

    private fun resolveTargetSpec(spec: TargetSpec, session: ExecutionSession): Entity? = when (spec.kind) {
        TargetKind.EXECUTOR -> session.context?.executor?.let { resolveTargetSpec(it, session) } ?: session.actor
        TargetKind.ACTIVATOR -> session.actor
        TargetKind.INHERITED_TARGET -> session.context?.target?.takeUnless { it == spec }?.let { resolveTargetSpec(it, session) }
        TargetKind.NEAREST_PLAYER, TargetKind.NEARBY_PLAYERS ->
            session.origin.world.players.filter { matches(it, spec, session) }.minByOrNull { it.location.distanceSquared(session.origin) }
        TargetKind.RANDOM_PLAYER -> session.origin.world.players.filter { matches(it, spec, session) }.randomOrNull()
        TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES ->
            session.origin.world.entities.filter { matches(it, spec, session) }.minByOrNull { it.location.distanceSquared(session.origin) }
        TargetKind.FIXED_ENTITY -> spec.fixedEntityId?.let(session.origin.world::getEntity)
    }

    private fun matches(entity: Entity, spec: TargetSpec, session: ExecutionSession): Boolean {
        if (spec.excludeActivator && entity == session.actor) return false
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

    private fun resolvePosition(spec: PositionSpec, session: ExecutionSession): Location = when (spec.kind) {
        PositionKind.CAPTURED, PositionKind.COORDINATES -> Location(
            session.origin.world,
            spec.x ?: session.origin.x,
            spec.y ?: session.origin.y,
            spec.z ?: session.origin.z,
            spec.yaw ?: session.origin.yaw,
            spec.pitch ?: session.origin.pitch,
        )
        PositionKind.DISK -> session.origin.clone()
        PositionKind.EXECUTOR -> resolveTargetSpec(session.context?.executor ?: TargetSpec(TargetKind.ACTIVATOR), session)?.location ?: session.origin
        PositionKind.TARGET -> resolveTargetSpec(session.context?.target ?: TargetSpec(TargetKind.ACTIVATOR), session)?.location ?: session.origin
        PositionKind.MYWORLD_SPAWN -> session.origin.world.spawnLocation
        PositionKind.VARIABLE -> plugin.variables.get(session.worldId, spec.variable.orEmpty())?.position?.let {
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
    )
}
