package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutDiagnostic
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiTheme
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiThemes
import net.kyori.adventure.text.Component

/**
 * 子画面骨格 `gesture-gui/child-screen.html` の共有組立口です。
 *
 * 骨格は「タイトル・常設説明・本文領域・帯・下端左右セル」の
 * 領域配置だけを持ち、各画面は実行時データを載せたノードを領域 ID へ注入して
 * 構築します。注入ノードは従来の画面座標（列中心・行位置）のまま `placed` へ渡し、
 * 変換基準だけを各領域の枠（[RegionFrames]）へ替えることで、旧レイアウトの
 * 実効座標をそのまま維持します。
 */
internal object DeclarativeChildScreen {
    const val RESOURCE_PATH = "gesture-gui/child-screen.html"

    /**
     * 骨格の各注入領域が覆う画面内範囲です。`placed` のオフセット変換基準として使います。
     *
     * 各値は child-screen.html の `position: absolute` オフセットと対応します
     * （領域の左上 = 内容域原点 + markup の left/top）。マークアップ側の位置を
     * 変えるときは、こちらの換算値も同じだけ動かしてください。
     */
    class RegionFrames(frame: ContentFrame) {
        /** 本文領域（markup: left 0 / top 0.3553）。全幅・上端は先頭候補行の上端です。 */
        val content = ContentFrame(minX = frame.minX, maxY = frame.maxY - 0.3553)

        /** 帯（markup: left 0.5257 / top 0.7253）。ページャ・戻り値行など、下端操作行の直上です。 */
        val band = ContentFrame(minX = frame.minX + 0.5257, maxY = frame.maxY - 0.7253)

        /** 下端左セル（markup: left 0.0257 / top 0.8653）。主操作またはページャを置きます。 */
        val footerLeft = ContentFrame(minX = frame.minX + 0.0257, maxY = frame.maxY - 0.8653)

        /** 下端右セル（markup: left 1.0857 / top 0.8653）。戻る・取消を置きます。 */
        val footerRight = ContentFrame(minX = frame.minX + 1.0857, maxY = frame.maxY - 0.8653)
    }

    /** 骨格の各注入領域の枠を求めます。 */
    fun regions(panel: GestureGuiPanel): RegionFrames = RegionFrames(contentFrame(panel))

    /**
     * 子画面骨格へ各領域のノードを注入し、解決・変換まで一続きで処理します。
     *
     * タイトル・常設説明は環境解決へ渡し、それ以外の領域は空なら注入しません。
     * 領域に注入するノードは [regions] の枠を基準に `placed` 変換しておきます。
     */
    fun compile(
        title: Component,
        description: Component,
        content: List<GestureGuiNode> = emptyList(),
        band: List<GestureGuiNode> = emptyList(),
        footerLeft: List<GestureGuiNode> = emptyList(),
        footerRight: List<GestureGuiNode> = emptyList(),
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        panel: GestureGuiPanel = GestureGuiPanel(),
        theme: GestureGuiTheme = GestureGuiThemes.standard(),
    ): KantanLayoutBridge.Compiled = KantanLayoutBridge.compileHtmlResource(
        resourcePath = RESOURCE_PATH,
        environment = KantanGuiTheme.environment(
            components = mapOf(
                "child-title" to title,
                "setting-description-hover" to description,
            ),
            theme = theme,
        ),
        onAction = onAction,
        onDiagnostics = onDiagnostics,
        panel = panel,
        injections = buildMap {
            if (content.isNotEmpty()) put("child-content", content)
            if (band.isNotEmpty()) put("child-band", band)
            if (footerLeft.isNotEmpty()) put("child-footer-left", footerLeft)
            if (footerRight.isNotEmpty()) put("child-footer-right", footerRight)
        },
    )
}
