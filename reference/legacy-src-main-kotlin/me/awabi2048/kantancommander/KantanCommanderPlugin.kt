package me.awabi2048.kantancommander

import me.awabi2048.kantancommander.command.KantanCommanderCommand
import me.awabi2048.kantancommander.data.DataManager
import me.awabi2048.kantancommander.data.PlacedDiskManager
import me.awabi2048.kantancommander.execution.RedstoneTriggerListener
import me.awabi2048.kantancommander.gui.CommandParamMenu
import me.awabi2048.kantancommander.gui.CommandTypeSelectionMenu
import me.awabi2048.kantancommander.gui.ProgramListMenu
import me.awabi2048.kantancommander.gui.SequenceEditorMenu
import me.awabi2048.kantancommander.item.DiskInteractionListener
import me.awabi2048.kantancommander.util.I18nHelper
import org.bukkit.plugin.java.JavaPlugin

class KantanCommanderPlugin : JavaPlugin() {

    companion object {
        lateinit var instance: KantanCommanderPlugin
            private set
    }

    val sequenceEditorMenu: SequenceEditorMenu by lazy { SequenceEditorMenu(this) }
    val commandTypeSelectionMenu: CommandTypeSelectionMenu by lazy { CommandTypeSelectionMenu(this) }
    val commandParamMenu: CommandParamMenu by lazy { CommandParamMenu(this) }
    val programListMenu: ProgramListMenu by lazy { ProgramListMenu(this) }

    override fun onEnable() {
        instance = this

        saveDefaultConfig()

        I18nHelper.init(this)
        DataManager.init(this)
        PlacedDiskManager.init(this)
        PlacedDiskManager.rebuildAllDisplays(this)

        registerCommands()
        registerListeners()
        registerMenuRoutes()

        logger.info("KantanCommander v${pluginMeta.version} が有効になりました")
    }

    override fun onDisable() {
        I18nHelper.shutdown()
        logger.info("KantanCommander が無効になりました")
    }

    private fun registerCommands() {
        val command = KantanCommanderCommand(this)
        getCommand("kankoma")?.setExecutor(command)
        getCommand("kankoma")?.tabCompleter = command
    }

    private fun registerListeners() {
        val pm = server.pluginManager
        pm.registerEvents(DiskInteractionListener(this), this)
        pm.registerEvents(RedstoneTriggerListener(this), this)
        pm.registerEvents(sequenceEditorMenu, this)
        pm.registerEvents(commandTypeSelectionMenu, this)
        pm.registerEvents(commandParamMenu, this)
        pm.registerEvents(programListMenu, this)
    }

    private fun registerMenuRoutes() {
        sequenceEditorMenu.initialize()
        commandTypeSelectionMenu.initialize()
        commandParamMenu.initialize()
        programListMenu.initialize()
    }
}
