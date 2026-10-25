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

    /**
     * 骨格の各注入領域が覆う画面内範囲です。`placed` のオフセット変換基準として使います。
     *
     * 各値は test-screen.html の `position: absolute` オフセットと対応します
     * （領域の左上 = 内容域原点 + markup の left/top）。マークアップ側の位置を
     * 変えるときは、こちらの換算値も同じだけ動かしてください。
     */
    class RegionFrames(frame: ContentFrame) {
        /** 見出し（markup: left 0 / top 0.08）。タイトル・説明の文言行です。 */
        val header = ContentFrame(minX = frame.minX, maxY = frame.maxY - 0.08)

        /** 本文（markup: left 0 / top 0.30）。切替カード・状態行・結果行です。 */
        val content = ContentFrame(minX = frame.minX, maxY = frame.maxY - 0.30)

        /** 下端操作行（markup: left 0 / top 0.62）。開始・取消・案内・完了です。 */
        val footer = ContentFrame(minX = frame.minX, maxY = frame.maxY - 0.62)
    }

    /** 骨格の各注入領域の枠を求めます。 */
    fun regions(panel: GestureGuiPanel): RegionFrames = RegionFrames(contentFrame(panel))

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
