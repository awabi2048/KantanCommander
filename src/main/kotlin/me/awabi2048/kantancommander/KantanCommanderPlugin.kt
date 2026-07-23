package me.awabi2048.kantancommander

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.config.ConfigClassification
import com.awabi2048.ccsystem.api.config.ManagedConfigSpec
import com.awabi2048.ccsystem.api.gui.MenuTargetPolicy
import com.awabi2048.ccsystem.api.gui.PublicMenuDefinition
import me.awabi2048.kantancommander.command.KantanCommanderCommand
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.data.PlacementStore
import me.awabi2048.kantancommander.execution.RedstoneTriggerListener
import me.awabi2048.kantancommander.execution.SequenceExecutor
import me.awabi2048.kantancommander.gui.CommandEditMenu
import me.awabi2048.kantancommander.gui.ProgramListMenu
import me.awabi2048.kantancommander.gui.SequenceEditorMenu
import me.awabi2048.kantancommander.item.DiskInteractionListener
import me.awabi2048.kantancommander.item.KantanItemGrantProvider
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
    private lateinit var triggerListener: RedstoneTriggerListener

    override fun onEnable() {
        CCSystem.getAPI().getConfigSchemaService().register(
            "kantan",
            listOf(
                ManagedConfigSpec(
                    owner = "kantan",
                    sourcePlugin = this,
                    resourcePath = "config.yml",
                    targetPath = dataFolder.resolve("config.yml").toPath(),
                    currentVersion = 2,
                    classification = ConfigClassification.MANAGED_CONFIG,
                    migrations = mapOf(
                        1 to com.awabi2048.ccsystem.api.config.ConfigMigration { config ->
                            config.set("execution.minimum-repeat-delay-ticks", 1)
                            config.set("execution.maximum-chain-length", 64)
                            config.set("execution.maximum-particle-count", 256)
                            config.set("execution.nearby-radius", 32)
                        }
                    ),
                    validator = com.awabi2048.ccsystem.api.config.ConfigValidator { config ->
                        require(config.getInt("execution.minimum-repeat-delay-ticks", 1) >= 1)
                        require(config.getInt("execution.maximum-chain-length", 64) in 1..256)
                        require(config.getInt("execution.maximum-particle-count", 256) in 1..4096)
                        require(config.getDouble("execution.nearby-radius", 32.0) > 0.0)
                    },
                    reloadAction = { reloadConfig() }
                )
            )
        )
        check(CCSystem.getAPI().getConfigSchemaService().prepare("kantan").successful) {
            "Kantan Commander Config preparation failed"
        }
        reloadConfig()

        KcI18n.init(this)
        scripts = ScriptStore(dataFolder.resolve("scripts"), logger)
        CCSystem.getAPI().getItemGrantService().register(KantanItemGrantProvider(this))
        placements = PlacementStore(this, dataFolder.resolve("placements.json"))
        executor = SequenceExecutor(this)

        programListMenu = ProgramListMenu(this)
        CCSystem.getAPI().getMenuCommandService().unregisterOwner("kantan")
        CCSystem.getAPI().getMenuCommandService().register(
            PublicMenuDefinition(
                owner = "kantan",
                id = "programs",
                permission = "kankoma.use",
                targetPolicy = MenuTargetPolicy.SELF_ONLY,
                argumentKeys = setOf("page"),
                opener = { player, arguments ->
                    programListMenu.open(player, arguments["page"]?.toIntOrNull() ?: 0)
                    true
                }
            )
        )
        editorMenu = SequenceEditorMenu(this)
        commandEditMenu = CommandEditMenu(this)
        placementAccess = PlacementAccessPolicy(this)
        triggerListener = RedstoneTriggerListener(this)

        getCommand("kankoma")?.setExecutor(KantanCommanderCommand(this))
        registerEvents()
        placements.restoreDisplays()
        triggerListener.start()
    }

    override fun onDisable() {
        if (::placements.isInitialized) {
            placements.removeAllDisplays()
        }
        runCatching { KcI18n.shutdown() }
        runCatching { CCSystem.getAPI().getItemGrantService().unregister("kantan") }
        runCatching { CCSystem.getAPI().getConfigSchemaService().unregister("kantan") }
        runCatching { CCSystem.getAPI().getMenuCommandService().unregisterOwner("kantan") }
        runCatching { CCSystem.getAPI().getMenuRuntimeService().unregisterOwner("kantan") }
    }

    private fun registerEvents() {
        val pm = server.pluginManager
        pm.registerEvents(DiskInteractionListener(this), this)
        pm.registerEvents(triggerListener, this)
    }
}
