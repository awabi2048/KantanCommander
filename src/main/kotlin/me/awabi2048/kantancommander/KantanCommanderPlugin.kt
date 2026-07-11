package me.awabi2048.kantancommander

import me.awabi2048.kantancommander.command.KantanCommanderCommand
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.data.PlacementStore
import me.awabi2048.kantancommander.execution.RedstoneTriggerListener
import me.awabi2048.kantancommander.execution.SequenceExecutor
import me.awabi2048.kantancommander.gui.CommandEditMenu
import me.awabi2048.kantancommander.gui.ProgramListMenu
import me.awabi2048.kantancommander.gui.SequenceEditorMenu
import me.awabi2048.kantancommander.item.DiskInteractionListener
import me.awabi2048.kantancommander.security.PlacementAccessPolicy
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.plugin.java.JavaPlugin

class KantanCommanderPlugin : JavaPlugin() {
    lateinit var scripts: ScriptStore
        private set
    lateinit var placements: PlacementStore
        private set
    lateinit var executor: SequenceExecutor
        private set
    lateinit var programListMenu: ProgramListMenu
        private set
    lateinit var editorMenu: SequenceEditorMenu
        private set
    lateinit var commandEditMenu: CommandEditMenu
        private set
    lateinit var placementAccess: PlacementAccessPolicy
        private set

    override fun onEnable() {
        saveDefaultConfig()

        KcI18n.init(this)
        scripts = ScriptStore(dataFolder.resolve("scripts"), logger)
        placements = PlacementStore(this, dataFolder.resolve("placements.json"))
        executor = SequenceExecutor(this)

        programListMenu = ProgramListMenu(this)
        editorMenu = SequenceEditorMenu(this)
        commandEditMenu = CommandEditMenu(this)
        placementAccess = PlacementAccessPolicy(this)

        getCommand("kankoma")?.setExecutor(KantanCommanderCommand(this))
        registerEvents()
        placements.restoreDisplays()
    }

    override fun onDisable() {
        if (::placements.isInitialized) {
            placements.removeAllDisplays()
        }
        runCatching { KcI18n.shutdown() }
    }

    private fun registerEvents() {
        val pm = server.pluginManager
        pm.registerEvents(DiskInteractionListener(this), this)
        pm.registerEvents(RedstoneTriggerListener(this), this)
        pm.registerEvents(programListMenu, this)
        pm.registerEvents(editorMenu, this)
        pm.registerEvents(commandEditMenu, this)
    }
}
