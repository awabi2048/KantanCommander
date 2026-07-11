package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.TriggerMode
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.event.block.NotePlayEvent

class RedstoneTriggerListener(private val plugin: KantanCommanderPlugin) : Listener {
    @EventHandler
    fun onRedstone(event: BlockRedstoneEvent) {
        if (event.block.type != Material.NOTE_BLOCK) return
        val placement = plugin.placements.find(event.block.location) ?: return
        val script = plugin.scripts.load(placement.scriptId) ?: return
        val shouldRun = when (script.trigger) {
            TriggerMode.REDSTONE_RISING -> event.oldCurrent <= 0 && event.newCurrent > 0
            TriggerMode.REDSTONE_EDGE -> event.oldCurrent != event.newCurrent && event.newCurrent > 0
        }
        if (shouldRun) plugin.executor.execute(script.id, event.block.location.add(0.5, 0.5, 0.5))
    }

    @EventHandler
    fun onNote(event: NotePlayEvent) {
        if (plugin.placements.find(event.block.location) != null) {
            event.isCancelled = true
        }
    }
}
