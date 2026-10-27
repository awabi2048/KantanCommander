package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutDiagnostic
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode

/**
 * 親画面骨格 `gesture-gui/parent-screen.html` の共有組立口です。
 *
 * 骨格は「左タブ列・右ペインの見出し／本文／帯／下端操作行」の領域配置だけを
 * 持ち、各画面は実行時データを載せたノードを領域 ID へ注入して構築します。
 * 注入ノードは従来の画面座標（列中心・行位置）のまま `placed` へ渡し、
 * 変換基準だけを各領域の枠（[RegionFrames]）へ替えることで、旧レイアウトの
 * 実効座標をそのまま維持します。
 */
internal object DeclarativeParentScreen {
    const val RESOURCE_PATH = "gesture-gui/parent-screen.html"

    /** HTMLで解決した名前付き領域です。位置・寸法はHTMLだけで管理します。 */
    class RegionFrames(frames: Map<String, ContentFrame>) {
        val tabs = frames.getValue("parent-tabs")
        val header = frames.getValue("parent-header")
        val content = frames.getValue("parent-content")
        val band = frames.getValue("parent-band")
        val footer = frames.getValue("parent-footer")
    }

    /** 領域座標は共有Scaffold APIを通して取得します。 */
    fun regions(panel: GestureGuiPanel): RegionFrames = RegionFrames(
        KantanLayoutBridge.regionFrames(RESOURCE_PATH, panel, setOf("parent-tabs", "parent-header", "parent-content", "parent-band", "parent-footer")),
    )

    /**
     * 親画面骨格へ各領域のノードを注入し、解決・変換まで一続きで処理します。
     *
     * 領域に注入するノードは [regions] の枠を基準に `placed` 変換しておきます。
     * 空の領域は注入しないため、帯や下端操作行は使用画面だけが現れます。
     * 骨格は環境解決を持たない空容器だけで構成されるため、解析結果は
     * `compileScaffoldResource` で共有します。
     */
    fun compile(
        tabs: List<GestureGuiNode> = emptyList(),
        header: List<GestureGuiNode> = emptyList(),
        content: List<GestureGuiNode> = emptyList(),
        band: List<GestureGuiNode> = emptyList(),
        footer: List<GestureGuiNode> = emptyList(),
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        panel: GestureGuiPanel = GestureGuiPanel(),
    ): KantanLayoutBridge.Compiled = KantanLayoutBridge.compileScaffoldResource(
        resourcePath = RESOURCE_PATH,
        onAction = onAction,
        onDiagnostics = onDiagnostics,
        panel = panel,
        injections = buildMap {
            if (tabs.isNotEmpty()) put("parent-tabs", tabs)
            if (header.isNotEmpty()) put("parent-header", header)
            if (content.isNotEmpty()) put("parent-content", content)
            if (band.isNotEmpty()) put("parent-band", band)
            if (footer.isNotEmpty()) put("parent-footer", footer)
        },
    )
}
