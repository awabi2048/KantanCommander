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

    /** HTMLで解決した名前付き領域です。位置・寸法はHTMLだけで管理します。 */
    class RegionFrames(frames: Map<String, ContentFrame>) {
        val content = frames.getValue("child-content")
        val band = frames.getValue("child-band")
        val footerLeft = frames.getValue("child-footer-left")
        val footerRight = frames.getValue("child-footer-right")
    }

    /** 領域座標は共有Scaffold APIを通して取得します。 */
    fun regions(panel: GestureGuiPanel): RegionFrames = RegionFrames(
        KantanLayoutBridge.regionFrames(RESOURCE_PATH, panel, setOf("child-content", "child-band", "child-footer-left", "child-footer-right")),
    )

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
