package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutDiagnostic
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiTheme
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiThemes

/**
 * 設定候補子画面の骨格 `gesture-gui/setting-choices-child.html` の組立口です。
 *
 * 骨格は「見出し・候補・下端操作行」の領域配置だけを持ち、各画面は実行時データを
 * 載せたノードを領域 ID へ注入して構築します。注入ノードは従来の画面座標
 * （列中心・行位置）のまま `placed` へ渡し、変換基準だけを各領域の枠
 * （[RegionFrames]）へ替えることで、旧レイアウトの実効座標をそのまま維持します。
 *
 * 見出しは「設定項目なしの案内文」または「項目名・現在値・常設説明・詳細案内」の
 * どちらかで、どちらを出すかは実行時の画面状態が決めるため注入で切り替えます。
 */
internal object DeclarativeSettingChoicesChild {
    const val RESOURCE_PATH = "gesture-gui/setting-choices-child.html"

    /** HTMLで解決した名前付き領域です。位置・寸法はHTMLだけで管理します。 */
    class RegionFrames(frames: Map<String, ContentFrame>) {
        val header = frames.getValue("setting-choice-header")
        val cards = frames.getValue("setting-choice-cards")
        val footer = frames.getValue("setting-choice-footer")
    }

    /** 領域座標は共有Scaffold APIを通して取得します。 */
    fun regions(panel: GestureGuiPanel): RegionFrames = RegionFrames(
        KantanLayoutBridge.regionFrames(RESOURCE_PATH, panel, setOf("setting-choice-header", "setting-choice-cards", "setting-choice-footer")),
    )

    /**
     * 骨格へ各区画のノードを注入し、解析・解決・コンパイルまで一続きで処理します。
     *
     * 領域に注入するノードは [regions] の枠を基準に `placed` 変換しておきます。
     * 空の区画は注入しません。
     */
    fun compile(
        header: List<GestureGuiNode> = emptyList(),
        cards: List<GestureGuiNode> = emptyList(),
        footer: List<GestureGuiNode> = emptyList(),
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        panel: GestureGuiPanel = GestureGuiPanel(),
        theme: GestureGuiTheme = GestureGuiThemes.standard(),
    ): KantanLayoutBridge.Compiled = KantanLayoutBridge.compileHtmlResource(
        resourcePath = RESOURCE_PATH,
        environment = KantanGuiTheme.environment(theme = theme),
        onAction = onAction,
        panel = panel,
        onDiagnostics = onDiagnostics,
        injections = buildMap {
            if (header.isNotEmpty()) put("setting-choice-header", header)
            if (cards.isNotEmpty()) put("setting-choice-cards", cards)
            if (footer.isNotEmpty()) put("setting-choice-footer", footer)
        },
    )
}
