package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiOutline
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiAbsoluteOffsets
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBlock
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBox
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiHover
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiHoverPosition
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiHoverTarget
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiItem
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import me.awabi2048.kantancommander.gui.GestureLowerPanel
import me.awabi2048.kantancommander.gui.GestureSettingVisualPolicy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** 内容域の原点です。旧来の全画面座標からの換算に用います。 */
data class ContentFrame(val minX: Double, val maxY: Double)

/** パネル内容域の原点を求めます。 */
internal fun contentFrame(panel: GestureGuiPanel): ContentFrame = ContentFrame(
    minX = -panel.width / 2.0 + panel.frameWidth,
    maxY = panel.height / 2.0 - panel.frameWidth,
)

/** 旧来の中心・寸法指定を絶対配置へ変換します。 */
internal fun placed(frame: ContentFrame, cx: Double, cy: Double, w: Double, h: Double): GestureGuiAbsoluteOffsets =
    GestureGuiAbsoluteOffsets(
        left = (cx - w / 2.0) - frame.minX,
        top = frame.maxY - (cy + h / 2.0),
    )

/** 設定タブ列の1件です。文言・素材・ホバー解決値は呼び出し側で用意します。 */
data class SettingsTabEntry(
    val label: Component,
    val selected: Boolean,
    val attentionHover: GestureGuiHoverText?,
    val background: BlockData,
)

/** 説明ブロックの2行目です。警告（赤）または案内（灰）のどちらかです。 */
data class DescriptionDetail(
    val text: Component,
    val y: Double,
)

/**
 * 現在値の1行です。
 *
 * 値はComponentで保持し、バニラ翻訳（種別名など）も欠落させません。
 * 従来の文字列値は `Component.text` で包んで渡します。
 */
data class SettingsValueRow(
    val id: String,
    val label: String,
    val value: Component,
    val y: Double,
)

/** 設定候補・対象分類カードの1件です。座標は呼び出し側で解決済みで渡します。 */
data class SettingsChoiceCard(
    /** 要素 ID 全文です（例 lower-setting-choice:xxx）。無効時は消費面になります。 */
    val elementId: String,
    val label: Component,
    val background: BlockData,
    val outline: BlockData?,
    val enabled: Boolean,
    val cx: Double,
    val cy: Double,
    val w: Double,
    val h: Double,
    val labelDy: Double,
    val labelSize: Double,
    val labelWidth: Int,
    /** 旧ホバー規則の解決値をそのまま渡します。 */
    val hover: GestureGuiHoverText?,
    /** 常設説明の置換要否です（子画面のみ true）。 */
    val replacesDescription: Boolean,
    /** 視覚 ID の接頭辞です（例 setting-choice / position-target-choice）。 */
    val visualTag: String,
    val visualIndex: Int,
)

/** 汎用操作カード1件です。入力枠・取得・試射・候補導線を同じ形で表します。 */
data class SettingsActionCard(
    /** 要素 ID 全文です。操作面なしの場合は null にします。 */
    val elementId: String?,
    /** 文言です。ない場合（表示専用の無言操作面）は文言表示を作りません。 */
    val label: Component?,
    val background: BlockData,
    val cx: Double,
    val cy: Double,
    val w: Double,
    val h: Double,
    val labelSize: Double,
    val labelWidth: Int,
    /** 文言の中心の垂直ずらしです。装飾グリフ等の微調整に用います。 */
    val labelDy: Double = 0.0,
    val gestures: Set<GestureGuiGesture>,
    val guard: ((Player, GestureGuiGesture) -> Boolean)?,
    val hover: GestureGuiHoverText?,
    val visualTag: String,
    /** 選択中等の外周枠です。ない場合は枠なしです。 */
    val outline: BlockData? = null,
    /** action なしで入力を無音消費します（余白解除・バリア等）。表示専用では false です。 */
    val consume: Boolean = false,
)

/** ID候補一覧の1セルです。ID文字列は常時表示せず、ホバーで示します。 */
data class ResourcePickerCard(
    /** 要素 ID 全文です（例 lower-inline-pick:sound:minecraft:block.note_block.harp）。 */
    val elementId: String,
    val icon: ItemStack?,
    val background: BlockData,
    /** 現在選択中の候補の外周枠です。ない場合は枠なしです。 */
    val outline: BlockData?,
    val cx: Double,
    val cy: Double,
    val w: Double,
    val h: Double,
    /** 表示名とNamespaced IDの解決値をそのまま渡します。 */
    val hover: GestureGuiHoverText?,
    val visualIndex: Int,
)

/** プログラム全体設定カード1件です。背景・図柄・2行文言・操作面を同じ矩形で表します。 */
data class ScriptSettingCard(
    /**
     * 要素 ID 全文です（例 lower-script-name）。
     * 操作面のノード ID を兼ね、コンパイル後の要素 ID になります。
     */
    val elementId: String,
    val label: String,
    val value: Component,
    val background: BlockData,
    /**
     * 図柄です。サーバーなし単体テストでは ItemStack を生成できないため、
     * null の場合は図柄ノードを作りません（本番経路は常に指定します）。
     */
    val icon: ItemStack?,
    /** 未完了時の注意ホバーです。完了時は null です。 */
    val attentionHover: GestureGuiHoverText?,
    /**
     * 操作受付の実行可否です。null の場合は常に受け付けます。
     * 内容があるときだけ有効な操作（未選択時下部のディスク取得など）は、
     * クリック時点の最新状態を返す判定を渡します。
     */
    val guard: ((Player, GestureGuiGesture) -> Boolean)? = null,
    val cx: Double,
    /** カード中心の垂直位置です。未選択時下部は2行配置のため呼び出し側で指定します。 */
    val cy: Double = DeclarativeSettings.SCRIPT_CARD_CENTER_Y,
    val w: Double,
)

/**
 * 設定系画面の宣言区画です。
 *
 * 旧 `buildSettings` 系 helper の座標・寸法・素材・要素 ID・ホバー位置をそのまま転記し、
 * 文言・素材・ガード・ホバー解決値だけを呼び出し側から受けます。
 * i18n・候補算出・スクリプト参照は含まず、実行時 adapter が解決します。
 */
object DeclarativeSettings {
    /** プログラム全体設定カードの中心高さです（1行配置時の既定値）。 */
    internal const val SCRIPT_CARD_CENTER_Y = -0.02

    /** プログラム全体設定カードの高さです（旧 `addScriptSetting` と同一）。行別ホバー配置の基準にも使います。 */
    internal const val SCRIPT_CARD_HEIGHT = 0.17

    /** 旧ホバー文を宣言仕様へ変換します。置換先は宣言ノード ID 基準で解決します。 */
    internal fun hoverSpecOf(hover: GestureGuiHoverText): GestureGuiHover = GestureGuiHover(
        text = hover.text,
        size = hover.size,
        lineWidth = hover.lineWidth,
        position = GestureGuiHoverPosition.Fixed(hover.x, hover.y),
        replacement = hover.replacesVisualId?.let(GestureGuiHoverTarget::Node)
            ?: GestureGuiHoverTarget.None,
    )

    /**
     * 左タブ列と固定操作（複製・削除）を配置します。
     * 設定項目なしの案内画面でも同じ固定操作を用います。
     *
     * 実行位置タブは `executionTabIndex` で指定し、タブ列から抽出して
     * 下端の操作行の直上へ固定配置します。タブ列の番号は全項目基準の
     * まま変えず、既存の選択記憶とずらしません。
     * 削除・複製は下端1行に半幅で並べます（複製不可時は削除だけ全幅）。
     */
    fun tabsSection(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        tabs: List<SettingsTabEntry>,
        duplicateLabel: Component?,
        duplicateBackground: BlockData?,
        deleteLabel: Component,
        deleteBackground: BlockData,
        executionTabIndex: Int? = null,
    ) {
        val executionTab = executionTabIndex?.let { tabs.getOrNull(it) }
        tabs.forEachIndexed { index, tab ->
            if (index == executionTabIndex) return@forEachIndexed
            val position = if (executionTabIndex != null && index > executionTabIndex) index - 1 else index
            val cy = GestureLowerPanel.SETTINGS_TAB_TOP_Y - position * GestureLowerPanel.SETTINGS_TAB_PITCH
            val width = GestureSettingVisualPolicy.selectedTabWidth(GestureLowerPanel.SETTINGS_TAB_WIDTH, tab.selected)
            val cx = GestureSettingVisualPolicy.selectedTabCenterX(
                GestureLowerPanel.SETTINGS_TAB_CENTER_X,
                GestureLowerPanel.SETTINGS_TAB_WIDTH,
                tab.selected,
            )
            nodes += GestureGuiBlock(
                blockData = tab.background,
                width = GestureGuiSizeSpec.Fixed(width),
                height = GestureGuiSizeSpec.Fixed(GestureLowerPanel.SETTINGS_TAB_HEIGHT),
                id = "lower-tab:$index",
                actionId = "lower-tab:$index",
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                hover = tab.attentionHover?.let(::hoverSpecOf),
                absolute = placed(frame, cx, cy, width, GestureLowerPanel.SETTINGS_TAB_HEIGHT),
            )
            nodes += GestureGuiText(
                text = tab.label,
                size = 0.0055,
                lineWidth = 90,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "tab-$index",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, GestureLowerPanel.SETTINGS_TAB_CENTER_X, cy - 0.02, 1.0, 0.0055 * 3.0),
            )
        }
        executionTab?.let { tab ->
            // 実行位置行は幅を広げるアニメーションを行わず、選択色・ホバー・注意表示だけを残します。
            val width = GestureLowerPanel.SETTINGS_TAB_WIDTH
            val cx = GestureLowerPanel.SETTINGS_TAB_CENTER_X
            nodes += GestureGuiBlock(
                blockData = tab.background,
                width = GestureGuiSizeSpec.Fixed(width),
                height = GestureGuiSizeSpec.Fixed(GestureLowerPanel.SETTINGS_TAB_HEIGHT),
                id = "lower-tab:exec",
                actionId = "lower-tab:exec",
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                hover = tab.attentionHover?.let(::hoverSpecOf),
                absolute = placed(frame, cx, GestureLowerPanel.SETTINGS_TAB_EXECUTION_Y, width, GestureLowerPanel.SETTINGS_TAB_HEIGHT),
            )
            nodes += GestureGuiText(
                text = tab.label,
                size = 0.0055,
                lineWidth = 90,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "tab-exec",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, GestureLowerPanel.SETTINGS_TAB_CENTER_X, GestureLowerPanel.SETTINGS_TAB_EXECUTION_Y - 0.02, 1.0, 0.0055 * 3.0),
            )
        }
        val halfWidth = GestureLowerPanel.SETTINGS_TAB_WIDTH / 2.0
        if (duplicateLabel != null && duplicateBackground != null) {
            appendActionCard(
                nodes,
                frame,
                SettingsActionCard(
                    elementId = "lower-delete",
                    label = deleteLabel,
                    background = deleteBackground,
                    cx = GestureLowerPanel.SETTINGS_TAB_CENTER_X - halfWidth / 2.0,
                    cy = GestureLowerPanel.SETTINGS_TAB_ACTION_Y,
                    w = halfWidth,
                    h = GestureLowerPanel.SETTINGS_TAB_HEIGHT,
                    labelSize = 0.0049,
                    labelWidth = 45,
                    gestures = GestureGuiClickPolicy.CLICK,
                    guard = null,
                    hover = null,
                    visualTag = "delete",
                ),
            )
            appendActionCard(
                nodes,
                frame,
                SettingsActionCard(
                    elementId = "lower-duplicate",
                    label = duplicateLabel,
                    background = duplicateBackground,
                    cx = GestureLowerPanel.SETTINGS_TAB_CENTER_X + halfWidth / 2.0,
                    cy = GestureLowerPanel.SETTINGS_TAB_ACTION_Y,
                    w = halfWidth,
                    h = GestureLowerPanel.SETTINGS_TAB_HEIGHT,
                    labelSize = 0.0049,
                    labelWidth = 45,
                    gestures = GestureGuiClickPolicy.CLICK,
                    guard = null,
                    hover = null,
                    visualTag = "duplicate",
                ),
            )
            return
        }
        appendActionCard(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-delete",
                label = deleteLabel,
                background = deleteBackground,
                cx = GestureLowerPanel.SETTINGS_TAB_CENTER_X,
                cy = GestureLowerPanel.SETTINGS_TAB_ACTION_Y,
                w = GestureLowerPanel.SETTINGS_TAB_WIDTH,
                h = GestureLowerPanel.SETTINGS_TAB_HEIGHT,
                labelSize = 0.0049,
                labelWidth = 90,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "delete",
            ),
        )
    }

    /** 設定項目なしの案内文を配置します。固定操作は tabsSection 側で配置します。 */
    fun hintSection(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        hint: Component,
        x: Double = 0.28,
        y: Double = 0.20,
        id: String = "lower-hint",
    ) {
        nodes += GestureGuiText(
            text = hint,
            size = 0.010,
            lineWidth = 160,
            alignment = GestureGuiTextAlignment.CENTER,
            id = id,
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, x, y, 1.0, 0.010 * 3.0),
        )
    }

    /** 説明ブロック（常設説明＋詳細2行目）を配置します。 */
    fun descriptionSection(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        body: Component,
        detail: DescriptionDetail?,
        centerX: Double = 0.28,
        hoverY: Double = GestureLowerPanel.ACTION_DESCRIPTION_Y,
    ) {
        nodes += GestureGuiText(
            text = body,
            size = GestureLowerPanel.DESCRIPTION_TEXT_SIZE,
            lineWidth = 280,
            alignment = GestureGuiTextAlignment.CENTER,
            id = GestureLowerPanel.SETTING_DESCRIPTION_HOVER_ID,
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, centerX, hoverY, 1.0, 0.0043 * 3.0),
        )
        detail?.let {
            nodes += GestureGuiText(
                text = it.text,
                size = 0.0041,
                lineWidth = 280,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "setting-description-detail",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, centerX, it.y, 1.0, 0.0041 * 3.0),
            )
        }
    }

    /** 現在値行を配置します。単行・複合行とも同じ形です。 */
    fun valueRowsSection(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        rows: List<SettingsValueRow>,
        labelX: Double = -0.08,
        valueX: Double = 0.52,
    ) {
        rows.forEach { row ->
            nodes += GestureGuiText(
                text = Component.text(row.label),
                size = 0.0060,
                lineWidth = 120,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "${row.id}-label",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, labelX, row.y, 1.0, 0.0060 * 3.0),
            )
            nodes += GestureGuiText(
                text = row.value,
                size = 0.0060,
                lineWidth = 170,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "${row.id}-value",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, valueX, row.y, 1.0, 0.0060 * 3.0),
            )
        }
    }

    /**
     * 設定候補・対象分類カードを配置します。
     * 無効な候補は操作面なしの消費面にし、ホバーは維持します。
     *
     * 文言ノードのIDは要素ID由来とし、visualTag/visualIndexの連番に依存しません。
     * 同一文書へ候補列を複数回追加しても重複診断にならないための全体最適です。
     * 初回表示時のstale状態で同一visualIndexが再利用されても、要素IDが異なれば衝突しません。
     */
    fun choiceCardsSection(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        cards: List<SettingsChoiceCard>,
    ) {
        cards.forEach { card ->
            nodes += GestureGuiBlock(
                blockData = card.background,
                // 選択肢の切替時に旧カードの表示位置が残らないよう即時反映します。
                interpolationTicks = 0,
                width = GestureGuiSizeSpec.Fixed(card.w),
                height = GestureGuiSizeSpec.Fixed(card.h),
                outline = card.outline?.let { GestureGuiOutline(it) },
                id = card.elementId,
                actionId = if (card.enabled) card.elementId else null,
                acceptedGestures = if (card.enabled) GestureGuiClickPolicy.CLICK else emptySet(),
                consumeInput = !card.enabled,
                hover = card.hover?.let {
                    hoverSpecOf(it).copy(
                        replacement = if (card.replacesDescription) {
                            GestureGuiHoverTarget.Node(GestureLowerPanel.SETTING_DESCRIPTION_HOVER_ID)
                        } else {
                            GestureGuiHoverTarget.None
                        },
                    )
                },
                absolute = placed(frame, card.cx, card.cy, card.w, card.h),
            )
            nodes += GestureGuiText(
                text = card.label,
                interpolationTicks = 0,
                size = card.labelSize,
                lineWidth = card.labelWidth,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "${card.elementId}-label",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, card.cx, card.cy + card.labelDy, 1.0, card.labelSize * 3.0),
            )
        }
    }

    /** 汎用操作カード1件（背景＋文言＋操作面）を配置します。 */
    fun actionCardSection(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        card: SettingsActionCard,
    ) {
        appendActionCard(nodes, frame, card)
    }

    /**
     * issue #28 のID候補グリッドを配置します。
     *
     * 各セルは Block＋Item＋GestureGuiElement の基本構成です。
     * ID文字列は常時表示せず、ホバーで表示名とNamespaced IDを示します。
     */
    fun resourcePickerSection(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        cards: List<ResourcePickerCard>,
    ) {
        cards.forEach { card ->
            nodes += GestureGuiBlock(
                blockData = card.background,
                interpolationTicks = 0,
                width = GestureGuiSizeSpec.Fixed(card.w),
                height = GestureGuiSizeSpec.Fixed(card.h),
                outline = card.outline?.let { GestureGuiOutline(it) },
                id = card.elementId,
                actionId = card.elementId,
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                hover = card.hover?.let(::hoverSpecOf),
                absolute = placed(frame, card.cx, card.cy, card.w, card.h),
            )
            card.icon?.let { icon ->
                // 図柄が枠をはみ出さないよう、セル短辺の70%で表示します。
                val iconSize = minOf(card.w, card.h) * 0.70
                nodes += GestureGuiItem(
                    item = icon,
                    interpolationTicks = 0,
                    scale = iconSize,
                    width = GestureGuiSizeSpec.Fixed(iconSize),
                    height = GestureGuiSizeSpec.Fixed(iconSize),
                    id = "${card.elementId}-icon",
                    absolute = placed(frame, card.cx, card.cy + 0.005, iconSize, iconSize),
                )
            }
        }
    }

    /**
     * ページャ（左右矢印＋ページ表示）を配置します。
     * 利用不可方向は背景・文言だけを描画し、操作面を作りません。
     */
    fun pagerSection(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        idPrefix: String,
        elementPrefix: String,
        page: Int,
        pageCount: Int,
        centerX: Double,
        centerY: Double,
        arrowBackgrounds: List<BlockData>,
        arrowGlyphs: List<Component>,
        status: Component,
    ) {
        require(arrowBackgrounds.size == 2 && arrowGlyphs.size == 2) {
            "pager requires exactly two arrows"
        }
        listOf(page - 1 to 0, page + 1 to 1).forEach { (targetPage, arrowIndex) ->
            val available = targetPage in 0 until pageCount
            val x = centerX + if (arrowIndex == 0) -0.12 else 0.12
            appendActionCard(
                nodes,
                frame,
                SettingsActionCard(
                    elementId = if (available) "$elementPrefix:$targetPage" else null,
                    label = arrowGlyphs[arrowIndex],
                    background = arrowBackgrounds[arrowIndex],
                    cx = x,
                    cy = centerY,
                    w = 0.18,
                    h = 0.10,
                    labelSize = 0.005,
                    labelWidth = 60,
                    gestures = if (available) {
                        GestureGuiClickPolicy.CLICK
                    } else {
                        emptySet()
                    },
                    guard = null,
                    hover = null,
                    visualTag = "$idPrefix-page-$targetPage",
                ),
            )
        }
        nodes += GestureGuiText(
            text = status,
            size = 0.004,
            lineWidth = 80,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "$idPrefix-page-status",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, centerX, centerY, 1.0, 0.004 * 3.0),
        )
    }

    /** 戻る操作を配置します。 */
    fun backSection(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        label: Component,
        background: BlockData,
        centerX: Double,
        width: Double,
    ) {
        appendActionCard(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-setting-back",
                label = label,
                background = background,
                cx = centerX,
                cy = -0.43,
                w = width,
                h = 0.10,
                labelSize = 0.0048,
                labelWidth = 100,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "setting-back",
            ),
        )
    }

    private fun appendActionCard(nodes: MutableList<GestureGuiNode>, frame: ContentFrame, card: SettingsActionCard) {
        // 背景と文言のIDは要素ID由来とし、同一visualTagの再利用による重複診断を防ぎます。
        // 要素なし（無効ページャ等）はvisualTag由来のままですが、呼び出し側でtargetPage等を含めて一意化しています。
        val baseId = card.elementId ?: "${card.visualTag}-bg"
        nodes += GestureGuiBlock(
            blockData = card.background,
            width = GestureGuiSizeSpec.Fixed(card.w),
            height = GestureGuiSizeSpec.Fixed(card.h),
            outline = card.outline?.let { GestureGuiOutline(it) },
            id = baseId,
            actionId = card.elementId,
            acceptedGestures = card.gestures,
            consumeInput = card.consume,
            gestureGuard = card.guard,
            hover = card.hover?.let(::hoverSpecOf),
            absolute = placed(frame, card.cx, card.cy, card.w, card.h),
        )
        card.label?.let { label ->
            nodes += GestureGuiText(
                text = label,
                size = card.labelSize,
                lineWidth = card.labelWidth,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "$baseId-label",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, card.cx, card.cy + card.labelDy, 1.0, card.labelSize * 3.0),
            )
        }
    }

    /** プログラム全体設定（ノード未選択時）の1枚です。背景・図柄・2行文言・操作面を配置します。 */
    fun scriptSettingSection(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        card: ScriptSettingCard,
    ) {
        // プログラム全体設定はタブではないため、未完了でも警告表示を付けません。
        // 未完了の状態名はホバーで伝え、警告表示の対象はノード設定タブへ限定します（旧実装どおり）。
        // ノード ID は要素 ID と同一にし、操作振分けの照合対象を維持します。
        nodes += GestureGuiBlock(
            blockData = card.background,
            width = GestureGuiSizeSpec.Fixed(card.w),
            height = GestureGuiSizeSpec.Fixed(SCRIPT_CARD_HEIGHT),
            id = card.elementId,
            actionId = card.elementId,
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            gestureGuard = card.guard,
            hover = card.attentionHover?.let(::hoverSpecOf),
            absolute = placed(frame, card.cx, card.cy, card.w, SCRIPT_CARD_HEIGHT),
        )
        card.icon?.let { icon ->
            nodes += GestureGuiItem(
                item = icon,
                scale = 0.08,
                width = GestureGuiSizeSpec.Fixed(0.08),
                height = GestureGuiSizeSpec.Fixed(0.08),
                id = "${card.elementId}-icon",
                absolute = placed(frame, card.cx - card.w / 2.0 + 0.07, card.cy, 0.08, 0.08),
            )
        }
        nodes += GestureGuiText(
            text = Component.text(card.label),
            size = 0.0052,
            lineWidth = 120,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "${card.elementId}-label",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, card.cx, card.cy - 0.025, 1.0, 0.0052 * 3.0),
        )
        nodes += GestureGuiText(
            text = card.value,
            size = 0.0044,
            lineWidth = 170,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "${card.elementId}-value",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, card.cx, card.cy + 0.015, 1.0, 0.0044 * 3.0),
        )
    }

    /** プログラム全体設定画面の文書です。案内文言と4枚のカードからなります。 */
    fun scriptSettingsDocument(
        hint: Component,
        cards: List<ScriptSettingCard>,
        panel: GestureGuiPanel = GestureGuiPanel(),
    ): GestureGuiDocument {
        val frame = contentFrame(panel)
        val nodes = mutableListOf<GestureGuiNode>()
        nodes += GestureGuiText(
            text = hint,
            size = 0.008,
            lineWidth = 230,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "lower-hint",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, 0.0, 0.27, 1.0, 0.008 * 3.0),
        )
        cards.forEach { scriptSettingSection(nodes, frame, it) }
        return document(nodes, panel)
    }

    fun document(nodes: List<GestureGuiNode>, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument =
        GestureGuiDocument(
            GestureGuiBox(
                children = nodes,
                id = "settings-root",
                width = GestureGuiSizeSpec.Percent(1.0),
                height = GestureGuiSizeSpec.Percent(1.0),
            ),
            panel,
        )
}
