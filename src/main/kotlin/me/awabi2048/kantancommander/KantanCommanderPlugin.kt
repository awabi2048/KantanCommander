package me.awabi2048.kantancommander

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.config.ConfigClassification
import com.awabi2048.ccsystem.api.config.ManagedConfigSpec
import com.awabi2048.ccsystem.api.gui.MenuTargetPolicy
import com.awabi2048.ccsystem.api.gui.PublicMenuDefinition
import me.awabi2048.kantancommander.command.KantanCommanderCommand
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.data.PlacementStore
import me.awabi2048.kantancommander.data.WorldVariableStore
import me.awabi2048.kantancommander.data.WorldVariableLifecycleListener
import me.awabi2048.kantancommander.execution.RedstoneTriggerListener
import me.awabi2048.kantancommander.execution.SequenceExecutor
import me.awabi2048.kantancommander.export.VanillaDatapackExporter
import me.awabi2048.kantancommander.export.KantanStandaloneExportContributor
import me.awabi2048.mwmchanpon.api.StandaloneExportContributors
import me.awabi2048.kantancommander.gui.CommandEditMenu
import me.awabi2048.kantancommander.gui.ProgramListMenu
import me.awabi2048.kantancommander.gui.SequenceEditorMenu
import me.awabi2048.kantancommander.item.DiskInteractionListener
import me.awabi2048.kantancommander.item.KantanItemGrantProvider
import me.awabi2048.kantancommander.item.ItemSelectionListener
import me.awabi2048.kantancommander.security.PlacementAccessPolicy
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.plugin.java.JavaPlugin

class KantanCommanderPlugin : JavaPlugin() {
    lateinit var scripts: ScriptStore
        private set
    lateinit var placements: PlacementStore
        private set
    lateinit var variables: WorldVariableStore
        private set
    lateinit var executor: SequenceExecutor
        private set
    lateinit var programListMenu: ProgramListMenu
        private set
    lateinit var editorMenu: SequenceEditorMenu
        private set
    lateinit var commandEditMenu: CommandEditMenu
        private set
    lateinit var itemSelection: ItemSelectionListener
        private set
    lateinit var placementAccess: PlacementAccessPolicy
        private set
    lateinit var exporter: VanillaDatapackExporter
        private set
    private lateinit var triggerListener: RedstoneTriggerListener
    private var standaloneExportContributor: KantanStandaloneExportContributor? = null

    override fun onEnable() {
        CCSystem.getAPI().getConfigSchemaService().register(
            "kantan",
            listOf(
                ManagedConfigSpec(
                    owner = "kantan",
                    sourcePlugin = this,
                    resourcePath = "config.yml",
                    targetPath = dataFolder.resolve("config.yml").toPath(),
                    currentVersion = 7,
                    classification = ConfigClassification.MANAGED_CONFIG,
                    migrations = mapOf(
                        1 to com.awabi2048.ccsystem.api.config.ConfigMigration { config ->
                            removeLegacyConfig(config)
                            config.set("execution.maximum-command-count", 1024)
                            config.set("execution.maximum-disk-call-depth", 3)
                            config.set("timer.minimum-units", 1)
                            config.set("timer.maximum-units", 86400)
                        },
                        2 to com.awabi2048.ccsystem.api.config.ConfigMigration { config ->
                            removeLegacyConfig(config)
                            config.set("execution.maximum-command-count", 1024)
                            config.set("execution.maximum-disk-call-depth", 3)
                            config.set("timer.minimum-units", 1)
                            config.set("timer.maximum-units", 86400)
                        },
                        3 to com.awabi2048.ccsystem.api.config.ConfigMigration { config ->
                            removeLegacyConfig(config)
                        },
                        4 to com.awabi2048.ccsystem.api.config.ConfigMigration { config ->
                            config.set("display.glowing", null)
                        },
                        5 to com.awabi2048.ccsystem.api.config.ConfigMigration { config ->
                            config.set("graph.maximum-node-count", 512)
                            config.set("graph.maximum-map-width", 1024)
                            config.set("graph.maximum-map-height", 256)
                            config.set("graph.maximum-branch-depth", 32)
                        },
                        6 to com.awabi2048.ccsystem.api.config.ConfigMigration { config ->
                            config.set(
                                "execution.max-nodes-per-activation",
                                config.getInt("execution.maximum-command-count", 1024),
                            )
                            config.set(
                                "execution.max-disk-call-depth",
                                config.getInt("execution.maximum-disk-call-depth", 3),
                            )
                            config.set(
                                "limits.max-nodes-per-disk",
                                config.getInt("graph.maximum-node-count", 512),
                            )
                            config.set("limits.max-map-width", config.getInt("graph.maximum-map-width", 1024))
                            config.set("limits.max-map-height", config.getInt("graph.maximum-map-height", 256))
                            config.set(
                                "limits.max-branch-depth",
                                config.getInt("graph.maximum-branch-depth", 32),
                            )
                            config.set("execution.maximum-command-count", null)
                            config.set("execution.maximum-disk-call-depth", null)
                            config.set("graph", null)
                        },
                    ),
                    validator = com.awabi2048.ccsystem.api.config.ConfigValidator { config ->
                        require(config.getInt("execution.max-nodes-per-activation") >= 1)
                        require(config.getInt("execution.max-disk-call-depth") >= 0)
                        require(config.getInt("timer.minimum-units", 1) == 1)
                        require(config.getInt("timer.maximum-units", 86400) == 86400)
                        require(config.getInt("limits.max-nodes-per-disk") >= 1)
                        require(config.getInt("limits.max-map-width") >= 9)
                        require(config.getInt("limits.max-map-height") >= 3)
                        require(config.getInt("limits.max-branch-depth") >= 1)
                    },
                    reloadAction = { reloadConfig() }
                )
            )
        )
        check(CCSystem.getAPI().getConfigSchemaService().prepare("kantan").successful) {
            "Kantan Commander Config preparation failed"
        }
        KcI18n.init(this)
        reloadConfig()
        rebuildConfiguredServices()
        CCSystem.getAPI().getItemGrantService().register(KantanItemGrantProvider(this))
        placements = PlacementStore(this, dataFolder.resolve("placements.json"))
        variables = WorldVariableStore(dataFolder.resolve("world-variables"))
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
        itemSelection = ItemSelectionListener(this)
        editorMenu = SequenceEditorMenu(this)
        commandEditMenu = CommandEditMenu(this)
        placementAccess = PlacementAccessPolicy(this)
        triggerListener = RedstoneTriggerListener(this)

        getCommand("kankoma")?.setExecutor(KantanCommanderCommand(this))
        registerEvents()
        placements.restoreDisplays()
        triggerListener.start()
        if (server.pluginManager.isPluginEnabled("MWMChanpon")) {
            KantanStandaloneExportContributor(this).also {
                StandaloneExportContributors.register(it)
                standaloneExportContributor = it
            }
        }
    }

    override fun onDisable() {
        standaloneExportContributor?.let(StandaloneExportContributors::unregister)
        standaloneExportContributor = null
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
        pm.registerEvents(itemSelection, this)
        pm.registerEvents(WorldVariableLifecycleListener(this), this)
    }

    fun reloadManagedSettings(): Boolean {
        val result = CCSystem.getAPI().getConfigSchemaService().prepare("kantan")
        if (!result.successful) return false
        reloadConfig()
        rebuildConfiguredServices()
        return true
    }

    private fun rebuildConfiguredServices() {
        scripts = ScriptStore(
            dataFolder.resolve("structured-disks"),
            logger,
            me.awabi2048.kantancommander.data.GraphLimits(
                maximumNodeCount = config.getInt("limits.max-nodes-per-disk"),
                maximumMapWidth = config.getInt("limits.max-map-width"),
                maximumMapHeight = config.getInt("limits.max-map-height"),
                maximumBranchDepth = config.getInt("limits.max-branch-depth"),
            ),
        )
        exporter = VanillaDatapackExporter(
            scripts,
            dataFolder.resolve("exports"),
            config.getInt("execution.max-nodes-per-activation"),
        )
    }

    private fun removeLegacyConfig(config: org.bukkit.configuration.file.YamlConfiguration) {
        config.set("max-commands-per-disk", null)
        config.set("execution.minimum-repeat-delay-ticks", null)
        config.set("execution.maximum-chain-length", null)
        config.set("execution.maximum-particle-count", null)
        config.set("execution.nearby-radius", null)
    }
}
