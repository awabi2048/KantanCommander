package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.BlockMode
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.NotePlayEvent
import java.util.UUID

class RedstoneTriggerListener(private val plugin: KantanCommanderPlugin) : Listener {
    private val powered = mutableMapOf<String, Boolean>()
    private val lastRun = mutableMapOf<UUID, Long>()
    private val alwaysImpulseRun = mutableSetOf<UUID>()

    fun start() {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 1L, 1L)
    }

    private fun tick() {
        val now = plugin.server.currentTick.toLong()
        plugin.placements.all().forEach { placement ->
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            val block = world.getBlockAt(placement.x, placement.y, placement.z)
            if (block.type != Material.NOTE_BLOCK) return@forEach
            val script = plugin.scripts.load(placement.scriptId) ?: return@forEach
            if (script.blockMode == BlockMode.CHAIN) return@forEach
            val hasPower = block.isBlockPowered || block.isBlockIndirectlyPowered
            val previous = powered.put(placement.key, hasPower) ?: false
            val active = script.activation == ActivationMode.ALWAYS_ACTIVE || hasPower
            val shouldRun = when (script.blockMode) {
                BlockMode.IMPULSE -> active && (
                    script.activation == ActivationMode.ALWAYS_ACTIVE && alwaysImpulseRun.add(script.id) ||
                        script.activation == ActivationMode.NEEDS_REDSTONE && !previous && hasPower
                    )
                BlockMode.REPEAT -> {
                    val delay = script.delayTicks.coerceAtLeast(plugin.config.getInt("execution.minimum-repeat-delay-ticks", 1))
                    active && now - (lastRun[script.id] ?: Long.MIN_VALUE / 2) >= delay
                }
                BlockMode.CHAIN -> false
            }
            if (shouldRun) {
                lastRun[script.id] = now
                plugin.executor.execute(script.id, block.location.add(0.5, 0.5, 0.5)) { success ->
                    if (success) triggerChain(placement.world, placement.x, placement.y, placement.z, placement.facing, mutableSetOf(), 0)
                }
            }
        }
    }

    private fun triggerChain(worldName: String, x: Int, y: Int, z: Int, facing: String, visited: MutableSet<UUID>, depth: Int) {
        if (depth >= plugin.config.getInt("execution.maximum-chain-length", 64)) return
        val world = Bukkit.getWorld(worldName) ?: return
        val face = runCatching { BlockFace.valueOf(facing) }.getOrNull() ?: return
        val next = plugin.placements.find(world, x + face.modX, y + face.modY, z + face.modZ) ?: return
        val script = plugin.scripts.load(next.scriptId) ?: return
        if (script.blockMode != BlockMode.CHAIN || !visited.add(script.id)) return
        val block = world.getBlockAt(next.x, next.y, next.z)
        val poweredNow = block.isBlockPowered || block.isBlockIndirectlyPowered
        if (script.activation == ActivationMode.NEEDS_REDSTONE && !poweredNow) return
        plugin.executor.execute(script.id, block.location.add(0.5, 0.5, 0.5)) { success ->
            if (success || !script.conditional) {
                triggerChain(next.world, next.x, next.y, next.z, next.facing, visited, depth + 1)
            }
        }
    }

    @EventHandler
    fun onNote(event: NotePlayEvent) {
        if (plugin.placements.find(event.block.location) != null) event.isCancelled = true
    }
}
