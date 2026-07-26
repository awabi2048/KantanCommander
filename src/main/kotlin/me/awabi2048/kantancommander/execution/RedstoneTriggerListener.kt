package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.ActivationMode
import org.bukkit.Bukkit
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.util.UUID
import me.awabi2048.kantancommander.placement.PlacedDiskMaterials

class RedstoneTriggerListener(private val plugin: KantanCommanderPlugin) : Listener {
    private val powered = mutableMapOf<String, Boolean>()
    private val lastRun = mutableMapOf<UUID, Long>()

    fun start() {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::tick), 1L, 1L)
    }

    private fun tick() {
        val now = plugin.server.currentTick.toLong()
        plugin.placements.all().forEach { placement ->
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            val block = world.getBlockAt(placement.x, placement.y, placement.z)
            if (!PlacedDiskMaterials.isPlacedDisk(block.type)) return@forEach
            val script = plugin.scripts.load(placement.scriptId) ?: return@forEach
            val hasPower = POWER_FACES.any { face ->
                val adjacent = block.getRelative(face)
                adjacent.blockPower > 0 || adjacent.isBlockPowered || adjacent.isBlockIndirectlyPowered
            }
            val previous = powered.put(placement.key, hasPower) ?: false
            val shouldRun = when {
                !script.timer.enabled -> script.activation == ActivationMode.NEEDS_REDSTONE && !previous && hasPower
                script.activation == ActivationMode.ALWAYS_ACTIVE ->
                    now - (lastRun[script.id] ?: Long.MIN_VALUE / 2) >= script.timer.intervalTicks
                else -> hasPower && now - (lastRun[script.id] ?: Long.MIN_VALUE / 2) >= script.timer.intervalTicks
            }
            if (shouldRun) {
                lastRun[script.id] = now
                plugin.executor.execute(script.id, block.location.add(0.5, 0.5, 0.5))
            }
        }
    }

    companion object {
        private val POWER_FACES = listOf(
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
        )
    }
}
