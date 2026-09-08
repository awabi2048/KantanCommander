package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiAbsoluteOffsets
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBox
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustom
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiOverflow
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiViewport
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy

/** グラフ領域の矩形です。旧来の上部グラフ領域と同一です。 */
data class UpperViewportRect(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
)

/**
 * 上部画面の外枠の宣言文書です。
 *
 * グラフ本体は `Viewport > Custom` に載せ、操作列は旧来座標のまま絶対配置で
 * 転記します。要素 ID・受付集合・ガード・ホバーは旧実装と同一です。
 * compiler が Custom 出力を切断するため、独自の切断処理は不要です。
 */
object DeclarativeUpper {
    /** Custom ノードの宣言 ID です。操作面の名前空間接頭辞になります。 */
    const val GRAPH_NODE_ID = "graph"

    /** グラフ描画口の renderer ID です。 */
    const val GRAPH_RENDERER_ID = "kantan-graph"

    /** ビューポートの宣言 ID です。 */
    const val VIEWPORT_NODE_ID = "upper-graph"

    /** 余白解除の要素 ID です。旧実装と同一です。 */
    const val VIEWPORT_EMPTY_ID = "viewport-empty"

    /**
     * Custom由来の要素 ID から名前空間接頭辞を外します。
     * 外枠要素はそのまま返し、共有の振分けへ接続します。
     */
    fun stripGraphPrefix(elementId: String): String =
        if (elementId.startsWith("$GRAPH_NODE_ID/")) {
            elementId.removePrefix("$GRAPH_NODE_ID/")
        } else {
            elementId
        }

    fun document(
        viewport: UpperViewportRect,
        emptyRect: UpperViewportRect,
        chrome: List<SettingsActionCard>,
        includeViewportEmpty: Boolean,
        panel: GestureGuiPanel,
    ): GestureGuiDocument {
        val frame = contentFrame(panel)
        val nodes = mutableListOf<GestureGuiNode>()
        if (includeViewportEmpty) {
            nodes += GestureGuiBox(
                children = emptyList(),
                id = VIEWPORT_EMPTY_ID,
                actionId = VIEWPORT_EMPTY_ID,
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                width = GestureGuiSizeSpec.Fixed(emptyRect.maxX - emptyRect.minX),
                height = GestureGuiSizeSpec.Fixed(emptyRect.maxY - emptyRect.minY),
                absolute = GestureGuiAbsoluteOffsets(
                    left = emptyRect.minX - frame.minX,
                    top = frame.maxY - emptyRect.maxY,
                ),
            )
        }
        nodes += GestureGuiViewport(
            content = GestureGuiCustom(
                rendererId = GRAPH_RENDERER_ID,
                width = GestureGuiSizeSpec.Percent(1.0),
                height = GestureGuiSizeSpec.Percent(1.0),
                id = GRAPH_NODE_ID,
            ),
            id = VIEWPORT_NODE_ID,
            width = GestureGuiSizeSpec.Fixed(viewport.maxX - viewport.minX),
            height = GestureGuiSizeSpec.Fixed(viewport.maxY - viewport.minY),
            absolute = GestureGuiAbsoluteOffsets(
                left = viewport.minX - frame.minX,
                top = frame.maxY - viewport.maxY,
            ),
            overflow = GestureGuiOverflow.CLIP,
        )
        chrome.forEach { DeclarativeSettings.actionCardSection(nodes, frame, it) }
        return GestureGuiDocument(
            GestureGuiBox(
                children = nodes,
                id = "upper-root",
                width = GestureGuiSizeSpec.Percent(1.0),
                height = GestureGuiSizeSpec.Percent(1.0),
            ),
            panel,
        )
    }
}
