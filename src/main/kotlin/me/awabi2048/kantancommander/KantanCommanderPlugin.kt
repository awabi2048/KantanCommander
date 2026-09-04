package me.awabi2048.kantancommander

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.CCSystemAPI
import com.awabi2048.ccsystem.api.config.ConfigClassification
import com.awabi2048.ccsystem.api.config.ConfigMigration
import com.awabi2048.ccsystem.api.config.ManagedConfigSpec
import com.awabi2048.ccsystem.api.gui.MenuTargetPolicy
import com.awabi2048.ccsystem.api.gui.PublicMenuDefinition
import com.awabi2048.ccsystem.api.localization.LocalizationCatalogContract
import me.awabi2048.kantancommander.command.KantanCommanderCommand
import me.awabi2048.kantancommander.command.KantanCommandPermissions
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.data.GraphLimits
import me.awabi2048.kantancommander.data.PlacementStore
import me.awabi2048.kantancommander.data.WorldVariableStore
import me.awabi2048.kantancommander.data.WorldVariableLifecycleListener
import me.awabi2048.kantancommander.data.WorldVariableUsage
import me.awabi2048.kantancommander.data.WorldVariableUsageFinder
import me.awabi2048.kantancommander.data.WorldVariableRemovalResult
import me.awabi2048.kantancommander.execution.RedstoneTriggerListener
import me.awabi2048.kantancommander.execution.SequenceExecutor
import me.awabi2048.kantancommander.execution.SummonedEntityTracker
import me.awabi2048.kantancommander.export.VanillaDatapackExporter
import me.awabi2048.kantancommander.export.KantanStandaloneExportContributor
import me.awabi2048.mwmchanpon.api.StandaloneExportContributors
import me.awabi2048.kantancommander.gui.CommandEditMenu
import me.awabi2048.kantancommander.gui.GestureEditorFacade
import me.awabi2048.kantancommander.gui.ProgramListMenu
import me.awabi2048.kantancommander.gui.SequenceEditorMenu
import me.awabi2048.kantancommander.item.KantanInteractionListener
import me.awabi2048.kantancommander.item.KantanItemGrantProvider
import me.awabi2048.kantancommander.item.KantanItemGrantService
import me.awabi2048.kantancommander.placement.PlacementProtectionListener
import me.awabi2048.kantancommander.item.ItemSelectionListener
import me.awabi2048.kantancommander.security.PlacementAccessPolicy
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.block.Block
import org.bukkit.plugin.java.JavaPlugin

/**
 * Kantan Commanderが要求するCC-System言語契約をビルド時・起動時で共有します。
 *
 * この値は表示文そのものではなく、Kantanの全ローカライズキーの構造契約です。
 * CC-System側でキーを追加・削除したときに、Kantanの依存JARと実行時JARの世代ずれを
 * 起動直後に検出できるよう、依存テストとonEnableの両方から参照します。
 */
internal const val KANTAN_COMMANDER_LOCALIZATION_CONTRACT_FINGERPRINT =
    "321527484fbe2f97a3cf7e3fa767394197e886ca2fbdde3f5df31817246cbb15"

class KantanCommanderPlugin : JavaPlugin() {
    lateinit var scripts: ScriptStore
        private set
    lateinit var placements: PlacementStore
        private set
    lateinit var variables: WorldVariableStore
        private set
    lateinit var worldVariableUsageFinder: WorldVariableUsageFinder
        private set
    lateinit var executor: SequenceExecutor
        private set
    lateinit var summonedEntities: SummonedEntityTracker
        private set
    lateinit var programListMenu: ProgramListMenu
        private set
    lateinit var editorMenu: SequenceEditorMenu
        private set
    lateinit var commandEditMenu: CommandEditMenu
        private set
    lateinit var gestureEditor: GestureEditorFacade
        private set
    lateinit var itemSelection: ItemSelectionListener
        private set
    lateinit var placementAccess: PlacementAccessPolicy
        private set
    lateinit var exporter: VanillaDatapackExporter
        private set
    lateinit var itemGrantService: KantanItemGrantService
        private set
    private lateinit var triggerListener: RedstoneTriggerListener
    private var standaloneExportContributor: KantanStandaloneExportContributor? = null

    override fun onEnable() {
        if (!verifyGuiRuntimeContract()) return
        if (!verifyLocalizationContract()) return

        CCSystem.getAPI().getConfigSchemaService().register(
            "kantan",
            listOf(
                ManagedConfigSpec(
                    owner = "kantan",
                    sourcePlugin = this,
                    resourcePath = "config.yml",
                    targetPath = dataFolder.resolve("config.yml").toPath(),
                    currentVersion = 5,
                    classification = ConfigClassification.MANAGED_CONFIG,
                    migrations = mapOf(
                        1 to ConfigMigration { config ->
                            config.set("execution.max-summoned-entities-per-world", 256)
                            config.set("execution.max-summoned-entities-server", 2048)
                        },
                        2 to ConfigMigration { config ->
                            // v3からタイマー設定の保存単位を秒へ明示します。旧キーは
                            // スキーマ上の固定値だったため、実行時の秒範囲を新しい既定値で
                            // 置き換えてから旧キーを削除し、旧単位を再解釈しないようにします。
                            config.set("timer.minimum-seconds", 1)
                            config.set("timer.maximum-seconds", 86400)
                            config.set("timer.minimum-units", null)
                            config.set("timer.maximum-units", null)
                        },
                        3 to ConfigMigration { config ->
                            // プログラム名の既定値は作成者名から生成するため、旧来の
                            // 旧ディスク名設定を残すと利用者が古い名称へ戻せてしまいます。
                            config.set("default-disk-name", null)
                        },
                        4 to ConfigMigration { config ->
                            // 追従を標準とするGesture GUIへ移行します。旧設定は残して
                            // 既存の /kankoma gesture on|off を壊さず、未設定時だけ
                            // 従来どおりインベントリエディターへフォールバックします。
                            config.set("use-gesture-editor", config.getBoolean("use-gesture-editor", false))
                        },
                    ),
                    validator = com.awabi2048.ccsystem.api.config.ConfigValidator { config ->
                        require(config.getInt("execution.max-nodes-per-activation") >= 1)
                        require(config.getInt("execution.max-disk-call-depth") >= 0)
                        require(config.getInt("execution.max-summoned-entities-per-world") >= 1)
                        require(config.getInt("execution.max-summoned-entities-server") >=
                            config.getInt("execution.max-summoned-entities-per-world"))
                        require(config.getInt("timer.minimum-seconds", 1) == 1)
                        require(config.getInt("timer.maximum-seconds", 86400) == 86400)
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
        itemGrantService = KantanItemGrantService(this)
        CCSystem.getAPI().getItemGrantService().register(KantanItemGrantProvider(itemGrantService))
        placements = PlacementStore(this, dataFolder.resolve("placements.json"))
        variables = WorldVariableStore(dataFolder.resolve("world-variables"), logger)
        worldVariableUsageFinder = WorldVariableUsageFinder(
            placements = { placements.all() },
            scripts = { scripts.listAll() },
        )
        summonedEntities = SummonedEntityTracker(
            this,
            dataFolder.resolve("summoned-entities.csv"),
        )
        executor = SequenceExecutor(this)

        programListMenu = ProgramListMenu(this)
        CCSystem.getAPI().getMenuCommandService().unregisterOwner("kantan")
        listOf(
            Triple("library", KantanCommandPermissions.LIBRARY, programListMenu::openLibrary),
            Triple("history", KantanCommandPermissions.HISTORY, programListMenu::openHistory),
        ).forEach { (id, permission, opener) ->
            CCSystem.getAPI().getMenuCommandService().register(
                PublicMenuDefinition(
                    owner = "kantan",
                    id = id,
                    permission = permission,
                    targetPolicy = MenuTargetPolicy.SELF_ONLY,
                    argumentKeys = setOf("page"),
                    opener = { player, arguments ->
                        opener(player, arguments["page"]?.toIntOrNull() ?: 0)
                        true
                    },
                ),
            )
        }
        itemSelection = ItemSelectionListener(this)
        editorMenu = SequenceEditorMenu(this)
        commandEditMenu = CommandEditMenu(this)
        gestureEditor = GestureEditorFacade(this)
        placementAccess = PlacementAccessPolicy(this)
        triggerListener = RedstoneTriggerListener(this)

        getCommand("kankoma")?.setExecutor(KantanCommanderCommand(this, itemGrantService))
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
        runCatching { if (::gestureEditor.isInitialized) gestureEditor.closeAll() }
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
        pm.registerEvents(KantanInteractionListener(this), this)
        pm.registerEvents(PlacementProtectionListener(this), this)
        pm.registerEvents(triggerListener, this)
        pm.registerEvents(itemSelection, this)
        pm.registerEvents(WorldVariableLifecycleListener(this), this)
        pm.registerEvents(summonedEntities, this)
    }

    internal fun resetActivationTiming(scriptId: java.util.UUID) {
        triggerListener.resetTiming(scriptId)
    }

    /** 拡張ブロック撤去後に、残存している隣接ダストの接続状態を再計算します。 */
    internal fun refreshRedstoneTopologyAround(block: Block) {
        triggerListener.refreshDustTopologyAround(block)
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

    /** 一覧表示と削除確認が同じMyWorld単位の使用判定を使うための入口です。 */
    internal fun findWorldVariableUsages(worldName: String, variableName: String): List<WorldVariableUsage> =
        worldVariableUsageFinder.find(worldName, variableName)

    internal fun findWorldVariableUsages(
        worldName: String,
        variableNames: Collection<String>,
    ): Map<String, List<WorldVariableUsage>> =
        worldVariableUsageFinder.findAll(worldName, variableNames)

    /**
     * ワールド内変数の削除境界です。使用判定と永続化呼び出しを一つの処理へ束ね、
     * 確認画面を開いた後に使用箇所が増えた場合も、削除を成功扱いにしません。
     */
    internal fun removeWorldVariable(
        worldId: java.util.UUID,
        worldName: String,
        variableName: String,
    ): WorldVariableRemovalResult {
        val usages = findWorldVariableUsages(worldName, variableName)
        if (usages.isNotEmpty()) return WorldVariableRemovalResult(removed = false, usages = usages)
        return WorldVariableRemovalResult(removed = variables.remove(worldId, variableName))
    }

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

        val actualVersions = try {
            CCSystem.getAPI().guiRuntimeContractVersion to CCSystem.getAPI().gestureGuiContractVersion
        } catch (failure: LinkageError) {
            logger.severe("CC-System GUI契約版を取得できないため、Kantan Commanderを無効化します: ${failure.message}")
            server.pluginManager.disablePlugin(this)
            return false
        } catch (failure: RuntimeException) {
            logger.severe("CC-System GUI契約版の取得に失敗したため、Kantan Commanderを無効化します: ${failure.message}")
            server.pluginManager.disablePlugin(this)
            return false
        }

        if (
            actualVersions.first != REQUIRED_GUI_RUNTIME_CONTRACT_VERSION ||
            actualVersions.second != REQUIRED_GESTURE_GUI_CONTRACT_VERSION
        ) {
            logger.severe(
                "CC-System GUI契約版が一致しないため、Kantan Commanderを無効化します: " +
                    "expectedRuntime=$REQUIRED_GUI_RUNTIME_CONTRACT_VERSION, actualRuntime=${actualVersions.first}, " +
                    "expectedGesture=$REQUIRED_GESTURE_GUI_CONTRACT_VERSION, actualGesture=${actualVersions.second}",
            )
            server.pluginManager.disablePlugin(this)
            return false
        }
        return true
    }

    /**
     * KantanのGUIが要求する全ローカライズキーの世代を、機能登録より前に照合します。
     * これによりCC-SystemとKantanCommanderのJAR世代がずれても、GUI操作中の連鎖例外にはしません。
     */
    private fun verifyLocalizationContract(): Boolean {
        val actualFingerprint = runCatching {
            LocalizationCatalogContract.fingerprint(LOCALIZATION_DOMAIN)
        }.getOrElse { failure ->
            logger.severe("CC-System言語契約の取得に失敗したため、Kantan Commanderを無効化します: ${failure.message}")
            server.pluginManager.disablePlugin(this)
            return false
        }
        if (actualFingerprint != REQUIRED_LOCALIZATION_CONTRACT_FINGERPRINT) {
            logger.severe(
                "CC-System言語契約が一致しないため、Kantan Commanderを無効化します: " +
                    "expected=$REQUIRED_LOCALIZATION_CONTRACT_FINGERPRINT, actual=$actualFingerprint",
            )
            server.pluginManager.disablePlugin(this)
            return false
        }
        return true
    }

    private companion object {
        const val REQUIRED_GUI_RUNTIME_CONTRACT_VERSION = CCSystemAPI.GUI_RUNTIME_CONTRACT_VERSION
        const val REQUIRED_GESTURE_GUI_CONTRACT_VERSION = CCSystemAPI.GESTURE_GUI_CONTRACT_VERSION
        const val LOCALIZATION_DOMAIN = "kantan_commander_clean"
        const val REQUIRED_LOCALIZATION_CONTRACT_FINGERPRINT =
            KANTAN_COMMANDER_LOCALIZATION_CONTRACT_FINGERPRINT
    }

}
