package me.awabi2048.kantancommander

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.CCSystemAPI
import com.awabi2048.ccsystem.api.config.ConfigClassification
import com.awabi2048.ccsystem.api.config.ManagedConfigSpec
import com.awabi2048.ccsystem.api.gui.MenuTargetPolicy
import com.awabi2048.ccsystem.api.gui.PublicMenuDefinition
import me.awabi2048.kantancommander.command.KantanCommanderCommand
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.data.GraphLimits
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
import me.awabi2048.kantancommander.placement.PlacementProtectionListener
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
        if (!verifyGuiRuntimeContract()) return

        CCSystem.getAPI().getConfigSchemaService().register(
            "kantan",
            listOf(
                ManagedConfigSpec(
                    owner = "kantan",
                    sourcePlugin = this,
                    resourcePath = "config.yml",
                    targetPath = dataFolder.resolve("config.yml").toPath(),
                    currentVersion = 1,
                    classification = ConfigClassification.MANAGED_CONFIG,
                    migrations = emptyMap(),
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
                    reloadAction = {
                        reloadConfig()
                        if (::scripts.isInitialized) rebuildConfiguredServices()
                    }
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
        pm.registerEvents(PlacementProtectionListener(this), this)
        pm.registerEvents(triggerListener, this)
        pm.registerEvents(itemSelection, this)
        pm.registerEvents(WorldVariableLifecycleListener(this), this)
    }

    internal fun resetActivationTiming(scriptId: java.util.UUID) {
        triggerListener.resetTiming(scriptId)
    }

    internal fun forgetActivationState(placementKey: String, scriptId: java.util.UUID) {
        triggerListener.forget(placementKey, scriptId)
    }

    fun reloadManagedSettings(): Boolean {
        return CCSystem.getAPI().getConfigSchemaService().reload("kantan").successful
    }

    private fun rebuildConfiguredServices() {
        val graphLimits = graphLimits()
        scripts = ScriptStore(
            dataFolder.resolve("structured-disks"),
            logger,
            graphLimits,
        )
        exporter = VanillaDatapackExporter(
            scripts,
            dataFolder.resolve("exports"),
            config.getInt("execution.max-nodes-per-activation"),
            config.getInt("execution.max-disk-call-depth"),
            graphLimits,
        )
    }

    internal fun graphLimits() = GraphLimits(
        maximumNodeCount = config.getInt("limits.max-nodes-per-disk"),
        maximumMapWidth = config.getInt("limits.max-map-width"),
        maximumMapHeight = config.getInt("limits.max-map-height"),
        maximumBranchDepth = config.getInt("limits.max-branch-depth"),
    )

    /**
     * MenuDialogRequestなどのGUI公開ABIが異なるCC-System上では、登録処理より先に停止します。
     * 古いKantan Commanderが新しいCC-Systemへ接続してNoSuchMethodErrorを起こすことを防ぎます。
     */
    private fun verifyGuiRuntimeContract(): Boolean {
        val ccSystemPlugin = server.pluginManager.getPlugin("CC-System")
        if (ccSystemPlugin == null || !ccSystemPlugin.isEnabled) {
            logger.severe("CC-Systemが有効ではないため、Kantan Commanderを無効化します")
            server.pluginManager.disablePlugin(this)
            return false
        }

        val actualVersion = try {
            CCSystem.getAPI().guiRuntimeContractVersion
        } catch (failure: LinkageError) {
            logger.severe("CC-System GUI契約版を取得できないため、Kantan Commanderを無効化します: ${failure.message}")
            server.pluginManager.disablePlugin(this)
            return false
        } catch (failure: RuntimeException) {
            logger.severe("CC-System GUI契約版の取得に失敗したため、Kantan Commanderを無効化します: ${failure.message}")
            server.pluginManager.disablePlugin(this)
            return false
        }

        if (actualVersion != REQUIRED_GUI_RUNTIME_CONTRACT_VERSION) {
            logger.severe(
                "CC-System GUI契約版が一致しないため、Kantan Commanderを無効化します: " +
                    "expected=$REQUIRED_GUI_RUNTIME_CONTRACT_VERSION, actual=$actualVersion",
            )
            server.pluginManager.disablePlugin(this)
            return false
        }
        return true
    }

    private companion object {
        const val REQUIRED_GUI_RUNTIME_CONTRACT_VERSION = CCSystemAPI.GUI_RUNTIME_CONTRACT_VERSION
    }

}
