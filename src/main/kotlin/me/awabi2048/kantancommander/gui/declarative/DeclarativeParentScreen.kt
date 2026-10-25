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

    /**
     * 骨格の各注入領域が覆う画面内範囲です。`placed` のオフセット変換基準として使います。
     *
     * 各値は parent-screen.html の `position: absolute` オフセットと対応します
     * （領域の左上 = 内容域原点 + markup の left/top）。マークアップ側の位置を
     * 変えるときは、こちらの換算値も同じだけ動かしてください。
     */
    class RegionFrames(frame: ContentFrame) {
        /** 左タブ列（markup: left 0 / top 0）。全高を覆い、列下端の固定操作も含みます。 */
        val tabs = ContentFrame(minX = frame.minX, maxY = frame.maxY)

        /** 見出し（markup: left 0.55 / top 0）。常設説明・詳細案内・現在値行を置きます。 */
        val header = ContentFrame(minX = frame.minX + 0.55, maxY = frame.maxY)

        /** 本文（markup: left 0.55 / top 0.3453）。候補カード・入力カード・境界線を置きます。 */
        val content = ContentFrame(minX = frame.minX + 0.55, maxY = frame.maxY - 0.3453)

        /** 帯（markup: left 0.55 / top 0.7253）。下端操作行の直上へ下揃え候補を置きます。 */
        val band = ContentFrame(minX = frame.minX + 0.55, maxY = frame.maxY - 0.7253)

        /** 下端操作行（markup: left 0.55 / top 0.8453）。ページャ・戻る・確定を置きます。 */
        val footer = ContentFrame(minX = frame.minX + 0.55, maxY = frame.maxY - 0.8453)
    }

    /** 骨格の各注入領域の枠を求めます。 */
    fun regions(panel: GestureGuiPanel): RegionFrames = RegionFrames(contentFrame(panel))

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
