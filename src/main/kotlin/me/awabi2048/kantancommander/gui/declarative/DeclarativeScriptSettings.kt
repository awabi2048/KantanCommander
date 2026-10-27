package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutDiagnostic
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode

/**
 * プログラム全体設定画面の骨格 `gesture-gui/script-settings.html` の組立口です。
 *
 * 骨格は「案内文言・設定カード列」の領域配置だけを持ち、文言とカードは
 * 実行時データを載せたノードを領域 ID へ注入して構築します。注入ノードは
 * 従来の画面座標（列中心・行位置）のまま `placed` へ渡し、変換基準だけを
 * 各領域の枠（[RegionFrames]）へ替えることで、旧レイアウトの実効座標を
 * そのまま維持します。
 */
internal object DeclarativeScriptSettings {
    const val RESOURCE_PATH = "gesture-gui/script-settings.html"

    /** HTMLで解決した名前付き領域です。位置・寸法はHTMLだけで管理します。 */
    class RegionFrames(frames: Map<String, ContentFrame>) {
        val hint = frames.getValue("script-hint")
        val cards = frames.getValue("script-cards")
    }

    /** 領域座標は共有Scaffold APIを通して取得します。 */
    fun regions(panel: GestureGuiPanel): RegionFrames = RegionFrames(
        KantanLayoutBridge.regionFrames(RESOURCE_PATH, panel, setOf("script-hint", "script-cards")),
    )

    /**
     * 骨格へ各領域のノードを注入し、解決・変換まで一続きで処理します。
     *
     * 領域に注入するノードは [regions] の枠を基準に `placed` 変換しておきます。
     * 骨格は環境解決を持たない空容器だけで構成されるため、解析結果は
     * `compileScaffoldResource` で共有します。
     */
    fun compile(
        hint: List<GestureGuiNode> = emptyList(),
        cards: List<GestureGuiNode> = emptyList(),
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        panel: GestureGuiPanel = GestureGuiPanel(),
    ): KantanLayoutBridge.Compiled = KantanLayoutBridge.compileScaffoldResource(
        resourcePath = RESOURCE_PATH,
        injections = buildMap {
            if (hint.isNotEmpty()) put("script-hint", hint)
            if (cards.isNotEmpty()) put("script-cards", cards)
        },
        onAction = onAction,
        onDiagnostics = onDiagnostics,
        panel = panel,
    )
}
