package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.DataManager
import me.awabi2048.kantancommander.data.PlacedDiskManager
import me.awabi2048.kantancommander.data.model.TriggerType
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.event.block.NotePlayEvent

class RedstoneTriggerListener(private val plugin: KantanCommanderPlugin) : Listener {

    private val previousPower = mutableMapOf<String, Int>()
    private val executor = SequenceExecutor(plugin)

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockRedstone(event: BlockRedstoneEvent) {
        val block = event.block
        if (block.type != Material.NOTE_BLOCK) return

        val placement = PlacedDiskManager.findByLocation(block.location) ?: return
        val script = DataManager.load(placement.diskUUID) ?: return
        if (script.commands.isEmpty()) return

        val key = "${block.world.name},${block.x},${block.y},${block.z}"
        val oldPower = previousPower[key] ?: event.oldCurrent
        previousPower[key] = event.newCurrent

        val shouldTrigger = when (script.triggerType) {
            TriggerType.REDSTONE_EDGE -> oldPower <= 0 && event.newCurrent > 0
            TriggerType.REDSTONE_RISING -> event.newCurrent > 0
        }

        if (shouldTrigger && !executor.isExecuting(placement.diskUUID)) {
            executor.execute(
                commands = script.commands,
                origin = block.location.add(0.5, 0.5, 0.5),
                player = null,
                scriptUUID = placement.diskUUID
            )
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onNotePlay(event: NotePlayEvent) {
        val block = event.block
        if (block.type != Material.NOTE_BLOCK) return
        val placement = PlacedDiskManager.findByLocation(block.location) ?: return
        event.isCancelled = true
    }
}
