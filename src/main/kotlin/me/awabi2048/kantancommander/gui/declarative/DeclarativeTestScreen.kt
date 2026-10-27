package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutDiagnostic
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode

/**
 * テスト実行系画面の骨格 `gesture-gui/test-screen.html` の組立口です。
 *
 * 骨格は「見出し・本文・下端操作行」の領域配置だけを持ち、各画面は
 * 実行時データを載せたノードを領域 ID へ注入して構築します。注入ノードは
 * 従来の画面座標（中央揃えの行位置）のまま `placed` へ渡し、変換基準だけを
 * 各領域の枠（[RegionFrames]）へ替えることで、旧レイアウトの実効座標を
 * そのまま維持します。
 *
 * 実行状態画面は毎tick再生成されるため、骨格は環境解決要素を持たない
 * 空容器だけで構成し、解析結果は `compileScaffoldResource` で共有します。
 */
internal object DeclarativeTestScreen {
    const val RESOURCE_PATH = "gesture-gui/test-screen.html"

    /** HTMLで解決した名前付き領域です。位置・寸法はHTMLだけで管理します。 */
    class RegionFrames(frames: Map<String, ContentFrame>) {
        val header = frames.getValue("test-header")
        val content = frames.getValue("test-content")
        val footer = frames.getValue("test-footer")
    }

    /** 領域座標は共有Scaffold APIを通して取得します。 */
    fun regions(panel: GestureGuiPanel): RegionFrames = RegionFrames(
        KantanLayoutBridge.regionFrames(RESOURCE_PATH, panel, setOf("test-header", "test-content", "test-footer")),
    )

    /**
     * 骨格へ各領域のノードを注入し、解決・変換まで一続きで処理します。
     *
     * 領域に注入するノードは [regions] の枠を基準に `placed` 変換しておきます。
     * 空の領域は注入しません。
     */
    fun compile(
        header: List<GestureGuiNode> = emptyList(),
        content: List<GestureGuiNode> = emptyList(),
        footer: List<GestureGuiNode> = emptyList(),
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        panel: GestureGuiPanel = GestureGuiPanel(),
    ): KantanLayoutBridge.Compiled = KantanLayoutBridge.compileScaffoldResource(
        resourcePath = RESOURCE_PATH,
        injections = buildMap {
            if (header.isNotEmpty()) put("test-header", header)
            if (content.isNotEmpty()) put("test-content", content)
            if (footer.isNotEmpty()) put("test-footer", footer)
        },
        onAction = onAction,
        onDiagnostics = onDiagnostics,
        panel = panel,
    )
}
