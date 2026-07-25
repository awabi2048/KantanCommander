package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.ActivationMode
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.NotePlayEvent
import java.util.UUID

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
            if (block.type != Material.NOTE_BLOCK) return@forEach
            val script = plugin.scripts.load(placement.scriptId) ?: return@forEach
            val hasPower = block.isBlockPowered || block.isBlockIndirectlyPowered
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

    @EventHandler
    fun onNote(event: NotePlayEvent) {
        if (plugin.placements.find(event.block.location) != null) event.isCancelled = true
    }
}
