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
import me.awabi2048.kantancommander.data.EntityVariableLifecycleListener
import me.awabi2048.kantancommander.data.WorldVariableUsageFinder
import me.awabi2048.kantancommander.data.WorldVariableRemovalResult
import me.awabi2048.kantancommander.data.WorldVariableUsageScanResult
import me.awabi2048.kantancommander.execution.RedstoneTriggerListener
import me.awabi2048.kantancommander.execution.EventTriggerListener
import me.awabi2048.kantancommander.execution.PlayerFlightPermissionService
import me.awabi2048.kantancommander.execution.ParticleQuotaService
import me.awabi2048.kantancommander.execution.SequenceExecutor
import me.awabi2048.kantancommander.execution.TestExecutionCoordinator
import me.awabi2048.kantancommander.execution.TestExecutionPreferences
import me.awabi2048.kantancommander.execution.SummonedEntityTracker
import me.awabi2048.kantancommander.feedback.FeedbackCleanupListener
import me.awabi2048.kantancommander.feedback.PlayerFeedbackService
import me.awabi2048.kantancommander.export.VanillaDatapackExporter
import me.awabi2048.kantancommander.export.KantanStandaloneExportContributor
import me.awabi2048.mwmchanpon.api.StandaloneExportContributors
import me.awabi2048.kantancommander.gui.CommandEditMenu
import me.awabi2048.kantancommander.gui.GestureEditorFacade
import me.awabi2048.kantancommander.gui.ProgramListMenu
import me.awabi2048.kantancommander.gui.SequenceEditorMenu
import me.awabi2048.kantancommander.item.KantanInteractionListener
import me.awabi2048.kantancommander.item.ControlBlockInventorySync
import me.awabi2048.kantancommander.item.KantanItemGrantProvider
import me.awabi2048.kantancommander.item.KantanItemGrantService
import me.awabi2048.kantancommander.placement.PlacementProtectionListener
import me.awabi2048.kantancommander.placement.WorldEditControlBlockSyncListener
import me.awabi2048.kantancommander.item.ItemSelectionListener
import me.awabi2048.kantancommander.security.PlacementAccessPolicy
import me.awabi2048.kantancommander.security.LuckPermsRecalculateAdapter
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
    "3455fcbe2f69d31be76fd565a6453ed2dbb28b19f37be0fc876ba39318ee2de3"

class KantanCommanderPlugin : JavaPlugin() {
    lateinit var scripts: ScriptStore
        private set
    lateinit var placements: PlacementStore
        private set
    /** issue #11 の建造物ストアです。バニラ構造物形式を単一正本とします。 */
    lateinit var structures: me.awabi2048.kantancommander.structure.StructureStore
        private set
    /** issue #24 の Entity Group ストアです。群の所属・pivot・姿勢を永続管理します。 */
    lateinit var entityGroups: me.awabi2048.kantancommander.data.EntityGroupStore
        private set
    /**
     * issue #23 の高精度 Hit Test です。#14 の操作ツールが選択方法として
     * 利用できる独立 API であり、操作ツールの session／UI とは結合しません。
     */
    lateinit var hitTest: me.awabi2048.kantancommander.hittest.HitTestEngine
        private set
    /** issue #13 の制御ブロック注視表示です。設定で有効化すると program 情報を短時間表示します。 */
    lateinit var gazeDisplay: me.awabi2048.kantancommander.display.ControlBlockGazeService
        private set
    lateinit var variables: WorldVariableStore
        private set
    /** issue #22 のEntity変数storeです。PDCを永続領域に使います。 */
    lateinit var entityVariables: me.awabi2048.kantancommander.model.EntityVariableStore
        private set
    lateinit var worldVariableUsageFinder: WorldVariableUsageFinder
        private set
    lateinit var executor: SequenceExecutor
        private set
    lateinit var testExecution: TestExecutionCoordinator
        private set
    lateinit var testExecutionPreferences: TestExecutionPreferences
        private set
    lateinit var summonedEntities: SummonedEntityTracker
        private set
    /** 花火の飛行と破裂を本実行・試射で共有するサービスです。 */
    lateinit var fireworkFlight: me.awabi2048.kantancommander.execution.FireworkFlightService
        private set
    lateinit var particleQuota: ParticleQuotaService
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
    lateinit var feedback: PlayerFeedbackService
        private set
    lateinit var controlBlockSync: ControlBlockInventorySync
        private set
    lateinit var exporter: VanillaDatapackExporter
        private set
    lateinit var itemGrantService: KantanItemGrantService
        private set
    /** issue #14の操作ツールwandの入力処理です。選択状態・可視化を保持します。 */
    lateinit var operationTool: me.awabi2048.kantancommander.operationtool.OperationToolListener
        private set
    /** issue #14の操作ツールwandの操作用Inventory GUIです。導線のみ担当します。 */
    lateinit var operationMenu: me.awabi2048.kantancommander.operationtool.OperationToolMenu
        private set
    private lateinit var triggerListener: RedstoneTriggerListener
    private lateinit var eventTriggerListener: EventTriggerListener
    internal lateinit var playerFlightPermissions: PlayerFlightPermissionService
    private var worldEditSync: WorldEditControlBlockSyncListener? = null
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
                    currentVersion = 9,
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
                            // エディターの表示方式は固定設定ではなく、操作プレイヤーの
                            // Java版／統合版環境から決定するため、デバッグ用の旧設定を
                            // 移行時に削除します。残したままにすると、設定ファイル上の
                            // 値と実際のGUI経路が一致しない状態になります。
                            config.set("use-gesture-editor", null)
                        },
                        5 to ConfigMigration { config ->
                            // Particleの追加上限は既存のmigration 4が実行済みの
                            // 設定(バージョン5)には適用されないため、独立した世代として
                            // 導入します。これにより、旧ビルドで既にバージョン5へ
                            // 移行済みの環境でも既定値が補完されます。
                            config.set("execution.max-particles-per-world-per-second", 600)
                        },
                        6 to ConfigMigration { config ->
                            // タイマー間隔に下限を設けないため、旧来の最小秒数設定を
                            // 削除します。上限はそのまま維持します。
                            config.set("timer.minimum-seconds", null)
                        },
                        7 to ConfigMigration { config ->
                            // issue #13: 制御ブロック注視表示の設定を既定値で補完します。
                            // 既定は無効であり、既存環境の表示を変えません。
                            config.set("gaze.enabled", false)
                            config.set("gaze.distance", 8.0)
                            config.set("gaze.interval-ticks", 10)
                        },
                        8 to ConfigMigration { config ->
                            // issue #14: 操作ツールwandの上限値を既定値で補完します。
                            // EasyArmorStandsと初期値を揃え、既存環境の動作を変えません。
                            config.set("operation-tool.max-entities", 64)
                            config.set("operation-tool.max-box-edge", 128.0)
                        },
                    ),
                    validator = com.awabi2048.ccsystem.api.config.ConfigValidator { config ->
                        require(config.getInt("execution.max-nodes-per-activation") >= 1)
                        require(config.getInt("execution.max-disk-call-depth") >= 0)
                        require(config.getInt("execution.max-summoned-entities-per-world") >= 1)
                        require(config.getInt("execution.max-summoned-entities-server") >=
                            config.getInt("execution.max-summoned-entities-per-world"))
                        require(config.getInt("execution.max-particles-per-world-per-second") >= 1)
                        require(config.getInt("timer.maximum-seconds", 86400) == 86400)
                        require(config.getInt("limits.max-nodes-per-disk") >= 1)
                        require(config.getInt("limits.max-map-width") >= 9)
                        require(config.getInt("limits.max-map-height") >= 3)
                        require(config.getInt("limits.max-branch-depth") >= 1)
                        // issue #13: 注視表示の設定範囲です。距離・周期は正の有限値に限ります。
                        require(config.getDouble("gaze.distance", 8.0) in 1.0..32.0)
                        require(config.getLong("gaze.interval-ticks", 10L) >= 1L)
                        // issue #14: 操作ツールwandの上限値範囲です。上限超過時は追加・確定しません。
                        require(config.getInt("operation-tool.max-entities", 64) in 1..1024)
                        require(config.getDouble("operation-tool.max-box-edge", 128.0) in 1.0..512.0)
                    },
                    reloadAction = {
                        reloadConfig()
                        if (::testExecution.isInitialized) testExecution.abortAll()
                        if (::scripts.isInitialized) rebuildConfiguredServices()
                        // issue #13: 注視表示の設定を再読込後に組み替えます。
                        if (::gazeDisplay.isInitialized) gazeDisplay.reload()
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
        structures = me.awabi2048.kantancommander.structure.StructureStore(
            logger,
            dataFolder.resolve("structures"),
        )
        entityGroups = me.awabi2048.kantancommander.data.EntityGroupStore(
            dataFolder.resolve("entity-groups.json"),
            logger,
        )
        hitTest = me.awabi2048.kantancommander.hittest.HitTestEngine(
            exclusion = { entity ->
                runCatching { CCSystem.getAPI().getSystemEntityRegistry().isSystemEntity(entity) }
                    .getOrDefault(false)
            },
        )
        gazeDisplay = me.awabi2048.kantancommander.display.ControlBlockGazeService(this)
        server.pluginManager.registerEvents(gazeDisplay, this)
        gazeDisplay.reload()
        variables = WorldVariableStore(dataFolder.resolve("world-variables"), logger)
        entityVariables = me.awabi2048.kantancommander.model.EntityVariableStore(
            me.awabi2048.kantancommander.model.PdcEntityVariableBackend(),
        )
        worldVariableUsageFinder = WorldVariableUsageFinder(
            placements = { placements.all() },
            scripts = { scripts.listAll() },
        )
        summonedEntities = SummonedEntityTracker(
            this,
            dataFolder.resolve("summoned-entities.csv"),
        )
        // Particleの表示寿命はサーバー側で追跡できないため、実行個数を直近1秒の
        // ワールド別送信台帳へ加算します。limitProviderを遅延評価し、設定リロード後
        // の上限変更を次回の送信判定から反映します。
        particleQuota = ParticleQuotaService(limitProvider = {
            config.getInt("execution.max-particles-per-world-per-second", 600)
        })
        fireworkFlight = me.awabi2048.kantancommander.execution.FireworkFlightService(this)
        executor = SequenceExecutor(this)
        testExecution = TestExecutionCoordinator(executor)
        testExecutionPreferences = TestExecutionPreferences(this)

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
        feedback = PlayerFeedbackService()
        controlBlockSync = ControlBlockInventorySync(this)
        operationTool = me.awabi2048.kantancommander.operationtool.OperationToolListener(
            this,
            me.awabi2048.kantancommander.operationtool.OperationToolSessions(
                maxEntities = config.getInt("operation-tool.max-entities", 64),
                maxBoxEdge = config.getDouble("operation-tool.max-box-edge", 128.0),
            ),
        )
        operationMenu = me.awabi2048.kantancommander.operationtool.OperationToolMenu(this)
        triggerListener = RedstoneTriggerListener(this)
        eventTriggerListener = EventTriggerListener(this)
        playerFlightPermissions = PlayerFlightPermissionService(this)

        getCommand("kankoma")?.setExecutor(KantanCommanderCommand(this, itemGrantService))
        registerEvents()
        operationTool.start()
        placements.restoreDisplays()
        triggerListener.start()
        eventTriggerListener.start()
        if (server.pluginManager.isPluginEnabled("MWMChanpon")) {
            KantanStandaloneExportContributor(this).also {
                StandaloneExportContributors.register(it)
                standaloneExportContributor = it
            }
        }
    }

    override fun onDisable() {
        runCatching { if (::playerFlightPermissions.isInitialized) playerFlightPermissions.clear() }
        runCatching { worldEditSync?.stopSweep() }
        worldEditSync = null
        runCatching { if (::testExecution.isInitialized) testExecution.shutdown() }
        runCatching { if (::operationTool.isInitialized) operationTool.stop() }
        runCatching { if (::gestureEditor.isInitialized) gestureEditor.closeAll() }
        runCatching { if (::gazeDisplay.isInitialized) gazeDisplay.stop() }
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
        pm.registerEvents(operationTool, this)
        pm.registerEvents(triggerListener, this)
        pm.registerEvents(eventTriggerListener, this)
        pm.registerEvents(playerFlightPermissions, this)
        pm.registerEvents(itemSelection, this)
        pm.registerEvents(WorldVariableLifecycleListener(this), this)
        pm.registerEvents(EntityVariableLifecycleListener(this), this)
        pm.registerEvents(summonedEntities, this)
        pm.registerEvents(FeedbackCleanupListener(this), this)
        pm.registerEvents(me.awabi2048.kantancommander.gui.GestureHeldRefreshListener(this), this)
        pm.registerEvents(me.awabi2048.kantancommander.gui.GestureSessionCleanupListener(this), this)
        pm.registerEvents(controlBlockSync, this)
        // WorldEdit系の有無に関わらず登録します。WorldEdit操作は内部の可用性判定で
        // 守り、バニラ一括操作の警告とゴースト掃除は常時有効にします。
        WorldEditControlBlockSyncListener(this).also {
            pm.registerEvents(it, this)
            it.startSweep()
            worldEditSync = it
        }
        if (!LuckPermsRecalculateAdapter.register(this, logger) { playerUuid ->
                server.scheduler.runTask(this, Runnable {
                    server.getPlayer(playerUuid)?.let { controlBlockSync.normalize(it) }
                })
            }
        ) {
            logger.fine("LuckPerms再計算イベントは未接続のため、所持品同期は再評価点で追従します")
        }
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

    /**
     * issue #7 のイベント起動の登録を作り直します。
     *
     * 配置の追加・撤去・スクリプトの保存・削除の境界から呼び出し、
     * チャンク単位の登録と台帳の食い違いを残しません。
     */
    internal fun refreshEventTriggerIndex() {
        if (::eventTriggerListener.isInitialized) eventTriggerListener.rebuild()
    }

    /** issue #7 のスクリプト削除時に、そのスクリプトへのイベント登録を解除します。 */
    internal fun forgetEventTriggerScript(scriptId: java.util.UUID) {
        if (::eventTriggerListener.isInitialized) eventTriggerListener.forgetScript(scriptId)
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
            mutationGuard = { scriptId, _, operation ->
                if (::testExecution.isInitialized) {
                    testExecution.assertMutationAllowed(scriptId, operation)
                }
            },
            bypassedMutationNotifier = { scriptId, operation ->
                if (::testExecution.isInitialized) {
                    testExecution.notifyBypassedMutation(scriptId, operation)
                }
            },
            onScriptDeleted = { scriptId -> forgetEventTriggerScript(scriptId) },
            // issue #16: orphan 解放・回収で配置参照の正本を残します。
            isPlacedScript = { scriptId ->
                ::placements.isInitialized && placements.findByScript(scriptId).isNotEmpty()
            },
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
     * GUIの一覧表示・削除確認が保存済みプログラムの破損へ巻き込まれないための入口です。
     * 原因はここでログへ残し、画面層には構造化された「完全／不完全」だけを渡します。
     */
    internal fun findWorldVariableUsagesSafely(
        worldName: String,
        variableNames: Collection<String>,
    ): WorldVariableUsageScanResult {
        val result = worldVariableUsageFinder.findAllSafely(worldName, variableNames)
        result.failure?.let { failure ->
            logger.log(
                java.util.logging.Level.WARNING,
                "ワールド内変数の使用箇所を完全には確認できませんでした: world=$worldName names=$variableNames",
                failure,
            )
        }
        return result
    }

    /**
     * ワールド内変数の削除境界です。使用判定と永続化呼び出しを一つの処理へ束ね、
     * 確認画面を開いた後に使用箇所が増えた場合も、削除を成功扱いにしません。
     */
    internal fun removeWorldVariable(
        worldId: java.util.UUID,
        worldName: String,
        variableName: String,
    ): WorldVariableRemovalResult {
        val scan = findWorldVariableUsagesSafely(worldName, listOf(variableName))
        if (!scan.complete) {
            // 使用箇所が不明なときは、参照切れを作らないことを優先します。
            return WorldVariableRemovalResult(removed = false, scanComplete = false)
        }
        val usages = scan.usages[variableName].orEmpty()
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
            Quadruple(
                CCSystem.getAPI().guiRuntimeContractVersion,
                CCSystem.getAPI().gestureGuiContractVersion,
                CCSystem.getAPI().gestureGuiLayoutContractVersion,
                CCSystem.getAPI().gestureHtmlProfileVersion,
            )
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
            actualVersions.second != REQUIRED_GESTURE_GUI_CONTRACT_VERSION ||
            actualVersions.third != REQUIRED_GESTURE_GUI_LAYOUT_CONTRACT_VERSION ||
            actualVersions.fourth != REQUIRED_GESTURE_HTML_PROFILE_VERSION
        ) {
            logger.severe(
                "CC-System GUI契約版が一致しないため、Kantan Commanderを無効化します: " +
                    "expectedRuntime=$REQUIRED_GUI_RUNTIME_CONTRACT_VERSION, actualRuntime=${actualVersions.first}, " +
                    "expectedGesture=$REQUIRED_GESTURE_GUI_CONTRACT_VERSION, actualGesture=${actualVersions.second}, " +
                    "expectedLayout=$REQUIRED_GESTURE_GUI_LAYOUT_CONTRACT_VERSION, actualLayout=${actualVersions.third}, " +
                    "expectedHtml=$REQUIRED_GESTURE_HTML_PROFILE_VERSION, actualHtml=${actualVersions.fourth}",
            )
            server.pluginManager.disablePlugin(this)
            return false
        }
        return true
    }

    /** 4 版同時照合のための組です。Triple では版が不足します。 */
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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
        const val REQUIRED_GESTURE_GUI_LAYOUT_CONTRACT_VERSION = CCSystemAPI.GESTURE_GUI_LAYOUT_CONTRACT_VERSION
        const val REQUIRED_GESTURE_HTML_PROFILE_VERSION = CCSystemAPI.GESTURE_HTML_PROFILE_VERSION
        const val LOCALIZATION_DOMAIN = "kantan_commander_clean"
        const val REQUIRED_LOCALIZATION_CONTRACT_FINGERPRINT =
            KANTAN_COMMANDER_LOCALIZATION_CONTRACT_FINGERPRINT
    }

}
