package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiBounds
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustomRenderer
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustomRenderResult
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustomElement
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiElement
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustom
import com.awabi2048.ccsystem.api.gesturegui.layout.ResolvedGestureGuiNode

/** グラフ描画の中核出力です。旧来経路とCustom経路で共有します。 */
internal data class GraphCellOutput(
    val visuals: List<GestureGuiVisual>,
    val elements: List<GestureGuiElement>,
)

/** ズーム後の拡縮済み出力です。 */
internal data class ScaledGraphOutput(
    val visuals: List<GestureGuiVisual>,
    val elements: List<GestureGuiElement>,
)

/** グラフIDの名前空間です。表示・入力の拡縮切断対象を判定します。 */
internal fun isMapVisual(visual: GestureGuiVisual): Boolean =
    visual.visualId.startsWith("node-") ||
        visual.visualId.startsWith("add-") ||
        visual.visualId.startsWith("path-")

/**
 * グラフの入力要素を表示と同じ変換対象へ揃えます。
 * node-reorder・node-foldもグラフ上の操作要素であるため、通常ノードと同じ縮尺・クリップを
 * 適用します。IDの名前空間を追加するときは、生成側も同じ規則に従います。
 */
internal fun isMapElement(element: GestureGuiElement): Boolean =
    element.elementId.startsWith("node:") ||
        element.elementId.startsWith("node-reorder:") ||
        element.elementId.startsWith("node-fold:") ||
        element.elementId.startsWith("add:") ||
        element.elementId.startsWith("path:")

/** 境界矩形を倍率で変換します。 */
internal fun scaleGraphBounds(bounds: GestureGuiBounds, scale: Double): GestureGuiBounds =
    GestureGuiBounds(
        bounds.minX * scale,
        bounds.minY * scale,
        bounds.maxX * scale,
        bounds.maxY * scale,
    )

/**
 * ズーム倍率をグラフ内容へ適用します。旧来の後段処理と同一式です。
 * グラフ外（ナビ等）は対象外のままにします。
 */
internal fun scaleGraphOutput(
    visuals: List<GestureGuiVisual>,
    elements: List<GestureGuiElement>,
    zoomScale: Double,
): ScaledGraphOutput {
    val scaledVisuals = visuals.map { visual ->
        if (isMapVisual(visual)) {
            when (visual) {
                is GestureGuiVisual.Block -> visual.copy(
                    x = visual.x * zoomScale,
                    y = visual.y * zoomScale,
                    width = visual.width * zoomScale,
                    height = visual.height * zoomScale,
                )
                is GestureGuiVisual.Item -> visual.copy(
                    x = visual.x * zoomScale,
                    y = visual.y * zoomScale,
                    scale = visual.scale * zoomScale,
                )
                is GestureGuiVisual.Text -> visual.copy(
                    x = visual.x * zoomScale,
                    y = visual.y * zoomScale,
                    size = visual.size * zoomScale,
                )
            }
        } else visual
    }
    val scaledElements = elements.map { element ->
        if (isMapElement(element)) {
            val hover = element.hoverText
            element.copy(
                bounds = scaleGraphBounds(element.bounds, zoomScale),
                hoverText = hover?.copy(
                    x = hover.x * zoomScale,
                    y = hover.y * zoomScale,
                    size = hover.size * zoomScale,
                ),
            )
        } else element
    }
    return ScaledGraphOutput(scaledVisuals, scaledElements)
}

/**
 * グラフ出力をCustom描画口へ変換します。
 *
 * visual・要素IDはそのまま引き継ぎ、名前空間付与はcompilerへ任せます。
 * 受付なし要素は無音消費面にし、旧来の空gesture要素と等価にします。
 * Text・Itemの表示矩形は旧切断処理と同じ仮定寸法で申告し、切断判定は
 * compilerへ任せます。Blockは自身の幅・高さから求まるため申告不要です。
 */
internal fun toCustomResult(
    output: ScaledGraphOutput,
    frame: GraphViewportFrame,
): GestureGuiCustomRenderResult {
    val visualBounds = mutableMapOf<String, GestureGuiBounds>()
    output.visuals.forEach { visual ->
        when (visual) {
            is GestureGuiVisual.Block -> Unit
            is GestureGuiVisual.Item -> {
                val half = frame.iconSize * frame.zoomScale / 2.0
                visualBounds[visual.visualId] = GestureGuiBounds(
                    visual.x - half,
                    visual.y - half,
                    visual.x + half,
                    visual.y + half,
                )
            }
            is GestureGuiVisual.Text -> {
                val halfWidth = 0.06 * frame.zoomScale
                val halfHeight = 0.04 * frame.zoomScale
                visualBounds[visual.visualId] = GestureGuiBounds(
                    visual.x - halfWidth,
                    visual.y - halfHeight,
                    visual.x + halfWidth,
                    visual.y + halfHeight,
                )
            }
        }
    }
    val elements = output.elements.map { element ->
        val consumeOnly = element.acceptedGestures.isEmpty()
        GestureGuiCustomElement(
            elementId = element.elementId,
            bounds = element.bounds,
            acceptedGestures = element.acceptedGestures,
            actionId = if (consumeOnly) null else element.elementId,
            targetVisualId = element.targetVisualId,
            hoverText = element.hoverText,
            gestureGuard = element.gestureGuard,
            consumeInput = consumeOnly,
        )
    }
    return GestureGuiCustomRenderResult(
        visuals = output.visuals,
        elements = elements,
        visualBounds = visualBounds,
    )
}

/**
 * グラフCustom描画口です。解決済み出力を描画へ変換します。
 *
 * 座標基準・ズーム・出力は構築時に受けます。上部画面の切替え時は、
 * 解決済み配置の内容域から求めた frame で構築します。
 */
internal class KantanGraphCustomRenderer internal constructor(
    private val output: GraphCellOutput,
    private val zoomScale: Double,
    private val frame: GraphViewportFrame,
) : GestureGuiCustomRenderer {
    override fun render(
        node: GestureGuiCustom,
        layout: ResolvedGestureGuiNode,
    ): GestureGuiCustomRenderResult = convertForShadow()

    /** shadow検証・将来の切替えで共用する変換本体です。 */
    fun convertForShadow(): GestureGuiCustomRenderResult {
        val scaled = scaleGraphOutput(output.visuals, output.elements, zoomScale)
        return toCustomResult(scaled, frame)
    }
}

/**
 * 旧来結果とCustom変換結果の差分を求めます。空なら等価です。
 *
 * 表示ID・要素IDの集合一致、切断除去の決定境界、ガード・ホバーの有無を
 * 検証します。文言内容・素材の同一性は同一関数由来のため対象外です。
 * shadow検証用の一時口であり、上部画面の切替え時に除去します。
 */
internal fun diffGraphShadow(
    scaledVisuals: List<GestureGuiVisual>,
    scaledElements: List<GestureGuiElement>,
    clippedVisuals: List<GestureGuiVisual>,
    finalElements: List<GestureGuiElement>,
    result: GestureGuiCustomRenderResult,
    clip: GestureGuiBounds,
): List<String> {
    val diffs = mutableListOf<String>()
    fun boundsOf(visual: GestureGuiVisual): GestureGuiBounds = when (visual) {
        is GestureGuiVisual.Block -> GestureGuiBounds(
            visual.x - visual.width / 2.0,
            visual.y - visual.height / 2.0,
            visual.x + visual.width / 2.0,
            visual.y + visual.height / 2.0,
        )
        else -> GestureGuiBounds(visual.x, visual.y, visual.x, visual.y)
    }
    fun disjoint(bounds: GestureGuiBounds): Boolean =
        bounds.maxX <= clip.minX || bounds.minX >= clip.maxX ||
            bounds.maxY <= clip.minY || bounds.minY >= clip.maxY

    fun contains(bounds: GestureGuiBounds): Boolean =
        bounds.minX >= clip.minX && bounds.maxX <= clip.maxX &&
            bounds.minY >= clip.minY && bounds.maxY <= clip.maxY

    val scaledIds = scaledVisuals.mapTo(hashSetOf(), GestureGuiVisual::visualId)
    val resultIds = result.visuals.mapTo(hashSetOf(), GestureGuiVisual::visualId)
    (scaledIds - resultIds).sorted().take(5).forEach { diffs += "missing visual: $it" }
    (resultIds - scaledIds).sorted().take(5).forEach { diffs += "extra visual: $it" }

    val scaledElementIds = scaledElements.mapTo(hashSetOf(), GestureGuiElement::elementId)
    val resultElementIds = result.elements.map { it.elementId }.toHashSet()
    (scaledElementIds - resultElementIds).sorted().take(5).forEach { diffs += "missing element: $it" }
    (resultElementIds - scaledElementIds).sorted().take(5).forEach { diffs += "extra element: $it" }

    val clippedIds = clippedVisuals.mapTo(hashSetOf(), GestureGuiVisual::visualId)
    val declared = result.visualBounds
    scaledVisuals.filter { it.visualId !in clippedIds }.forEach { visual ->
        // 除去された表示は、申告矩形が領域内に収まらない場合に限り compiler と一致します。
        // Blockは幅・高さから求まるため申告対象外です。
        if (visual is GestureGuiVisual.Text || visual is GestureGuiVisual.Item) {
            val bounds = declared[visual.visualId]
            if (bounds == null) {
                diffs += "dropped visual without declared bounds: ${visual.visualId}"
            } else if (contains(bounds)) {
                diffs += "dropped visual declared inside clip: ${visual.visualId}"
            }
        }
    }

    val finalIds = finalElements.mapTo(hashSetOf(), GestureGuiElement::elementId)
    val byId = scaledElements.associateBy(GestureGuiElement::elementId)
    scaledElements.filter { it.elementId !in finalIds }.forEach { element ->
        // 除去された操作面は、切断で消えるか、対象表示の消滅に伴うものでなければずれです。
        val boundsGone = disjoint(element.bounds)
        val targetGone = element.targetVisualId != null && element.targetVisualId !in clippedIds
        if (!boundsGone && !targetGone) {
            diffs += "dropped element without clip reason: ${element.elementId}"
        }
    }

    val legacyGuards = scaledElements.associate { it.elementId to (it.gestureGuard != null) }
    result.elements.forEach { custom ->
        if (custom.elementId !in legacyGuards) {
            diffs += "extra custom element: ${custom.elementId}"
            return@forEach
        }
        if (legacyGuards.getValue(custom.elementId) != (custom.gestureGuard != null)) {
            diffs += "guard mismatch: ${custom.elementId}"
        }
        val legacy = byId.getValue(custom.elementId)
        if ((legacy.hoverText != null) != (custom.hoverText != null)) {
            diffs += "hover mismatch: ${custom.elementId}"
        }
    }
    return diffs
}
