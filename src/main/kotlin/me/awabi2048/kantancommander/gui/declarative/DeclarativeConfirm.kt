package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiAbsoluteOffsets
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBlock
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBox
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.data.BlockData

/** 確認系子画面の文言です。解決済みの表示文を呼び出し側が渡します。 */
data class ConfirmTexts(
    val title: Component,
    val warn: Component,
    val yes: Component,
    val no: Component,
)

/**
 * 確認系子画面（削除・上書き確認）の宣言文書です。
 *
 * 旧 `buildConfirm` の座標・寸法・素材・要素 ID をそのまま転記し、見た目と操作を維持します。
 * 要素 ID（confirm-delete / confirm-cancel）をノード ID へ継承するため、
 * 共有 onAction への振分けは旧実装と同一です。表示 ID と層のみ compiler 規約に従います。
 */
object DeclarativeConfirm {
    /** 旧実装の確定ボタン背景素材です。 */
    val YES_BACKGROUND_MATERIAL: Material = Material.RED_CONCRETE

    /** 旧実装の取消ボタン背景素材です。 */
    val NO_BACKGROUND_MATERIAL: Material = Material.CYAN_TERRACOTTA

    fun document(
        texts: ConfirmTexts,
        yesBackground: BlockData,
        noBackground: BlockData,
        panel: GestureGuiPanel = GestureGuiPanel(),
    ): GestureGuiDocument {
        // 内容域の原点を旧来の全画面座標へ換算します。frame 分だけ内側へ寄ります。
        val contentMinX = -panel.width / 2.0 + panel.frameWidth
        val contentMaxY = panel.height / 2.0 - panel.frameWidth

        fun placed(cx: Double, cy: Double, w: Double, h: Double): GestureGuiAbsoluteOffsets =
            GestureGuiAbsoluteOffsets(
                left = (cx - w / 2.0) - contentMinX,
                top = contentMaxY - (cy + h / 2.0),
            )

        fun label(id: String, cx: Double, cy: Double, size: Double, lineWidth: Int, text: Component): GestureGuiNode =
            GestureGuiText(
                text = text,
                // 確認面はタブ列と異なり補完なしで即時反映します。
                interpolationTicks = 0,
                size = size,
                lineWidth = lineWidth,
                alignment = GestureGuiTextAlignment.CENTER,
                id = id,
                // 文言の表示位置だけを再現します。幅は描画へ影響しません。
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(cx, cy, 1.0, size * 3.0),
            )

        val root = GestureGuiBox(
            children = listOf(
                label("confirm-title", 0.0, 0.11, 0.005, 180, texts.title),
                label("confirm-warn", 0.0, 0.05, 0.004, 180, texts.warn),
                GestureGuiBlock(
                    blockData = yesBackground,
                    interpolationTicks = 0,
                    width = GestureGuiSizeSpec.Fixed(0.48),
                    height = GestureGuiSizeSpec.Fixed(0.12),
                    // 要素 ID を継承し、旧来の振分けをそのまま利用します。
                    id = "confirm-delete",
                    actionId = "confirm-delete",
                    acceptedGestures = GestureGuiClickPolicy.CLICK,
                    absolute = placed(-0.27, -0.08, 0.48, 0.12),
                ),
                label("confirm-yes", -0.27, -0.08, 0.004, 100, texts.yes),
                GestureGuiBlock(
                    blockData = noBackground,
                    interpolationTicks = 0,
                    width = GestureGuiSizeSpec.Fixed(0.48),
                    height = GestureGuiSizeSpec.Fixed(0.12),
                    id = "confirm-cancel",
                    actionId = "confirm-cancel",
                    acceptedGestures = GestureGuiClickPolicy.CLICK,
                    absolute = placed(0.27, -0.08, 0.48, 0.12),
                ),
                label("confirm-no", 0.27, -0.08, 0.004, 100, texts.no),
            ),
            id = "confirm-root",
            width = GestureGuiSizeSpec.Percent(1.0),
            height = GestureGuiSizeSpec.Percent(1.0),
        )
        return GestureGuiDocument(root, panel)
    }
}
