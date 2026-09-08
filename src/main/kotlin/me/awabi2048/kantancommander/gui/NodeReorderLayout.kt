package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType

/**
 * ノード選択中に表示する入れ替え・折り畳みボタンの配置を純関数で計算します。
 *
 * Bukkit表示へ依存しないため単体試験で寸法・配置・可否を検証できます。
 * 実際の表示・入力生成は `GestureSequenceEditor` がこの計算だけを使い、
 * 寸法の導出式を二重に持ちません。
 */
internal object NodeReorderLayout {
    /** ボタンの一辺（ノードアイコン一辺に対する比率）。要求どおり50%とします。 */
    const val BUTTON_SIZE_RATIO: Double = 0.5

    /**
     * ボタン同士・ボタンとアイコンの隙間（ブロック単位）。
     * 従来の縦方向の隙間（0.008）と共通化し、縦横で均一な余白にします。
     */
    const val BUTTON_GAP: Double = 0.008

    /** ホバー退避の高さ（ボタン上面からの距離）。従来値（0.03）を維持します。 */
    const val HOVER_LIFT: Double = 0.03

    /** 変更前のボタン寸法（対ピッチ比）。グリフ寸法の換算基準として残します。 */
    const val LEGACY_BUTTON_PITCH_RATIO: Double = 0.20

    /** 変更前のグリフ寸法。ボタン倍率に応じて比例拡大します。 */
    const val LEGACY_GLYPH_SIZE: Double = 0.007

    /** 変更前のグリフ中心補正。グリフ倍率に応じて比例拡大します。 */
    const val LEGACY_GLYPH_DY: Double = 0.006

    /** 展開中（折り畳み可能）に示すグリフです。 */
    const val FOLD_GLYPH: String = "▼"

    /** 折り畳み中（展開可能）に示すグリフです。 */
    const val UNFOLD_GLYPH: String = "▲"

    /** ボタンの一辺を、ノードアイコン一辺の50%として求めます。 */
    fun buttonSize(iconSize: Double): Double = iconSize * BUTTON_SIZE_RATIO

    /** ボタン列の中心高さ。アイコン上面から隙間を空けて積み上げます。 */
    fun buttonY(cy: Double, iconSize: Double, buttonSize: Double): Double =
        cy + iconSize / 2.0 + buttonSize / 2.0 + BUTTON_GAP

    /**
     * 中心から左右ボタンまでの距離です。
     * 2個時・3個時で共通化するため、中央の有無で左右ボタンが動きません。
     * 2個時も中央揃え（左右対称）となり、従来と同じ見た目を保ちます。
     */
    fun sideOffset(buttonSize: Double): Double = buttonSize + BUTTON_GAP

    /** ホバー文言の高さ。ボタン列のさらに上へ退避させます。 */
    fun hoverY(buttonY: Double, buttonSize: Double): Double =
        buttonY + buttonSize / 2.0 + HOVER_LIFT

    /**
     * グリフ寸法。ボタン倍率へ比例させ、変更前と同じ詰まり具合を保ちます。
     * アイコン比率の変更に追従するよう、固定倍率ではなく実寸比で換算します。
     */
    fun glyphSize(buttonSize: Double, minPitch: Double): Double =
        LEGACY_GLYPH_SIZE * (buttonSize / (minPitch * LEGACY_BUTTON_PITCH_RATIO))

    /** グリフ中心補正。グリフ倍率へ比例させます。 */
    fun glyphDy(glyphSize: Double): Double =
        LEGACY_GLYPH_DY * (glyphSize / LEGACY_GLYPH_SIZE)

    /**
     * 折り畳み／展開の切り替え操作ができるかを判定します。
     *
     * ホバー案内・中央ボタンの表示・入力可否の条件を一致させる唯一の判定です。
     * 折り畳みノードは常時展開可、FOR開始はペア有り、条件分岐は合流済みのみ、
     * それ以外（通常ノード・合流・FOR終了・未合流条件）は対象外です。
     */
    fun isFoldToggleAvailable(node: CommandNode, graph: CommandGraph): Boolean = when (node.type) {
        CommandType.FOLDED_FOR, CommandType.FOLDED_IF -> true
        CommandType.FOR_START -> node.pairedNodeId != null
        CommandType.CONDITION -> GraphEditor.canFoldCondition(graph, node.id)
        else -> false
    }

    /**
     * 中央ボタンに示すグリフです。
     * 展開中は折り畳む（▼）、折り畳み中は展開する（▲）を示します。
     */
    fun foldGlyph(node: CommandNode): String =
        if (node.type == CommandType.FOLDED_FOR || node.type == CommandType.FOLDED_IF) UNFOLD_GLYPH
        else FOLD_GLYPH
}
