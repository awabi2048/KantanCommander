package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType

/**
 * ノード選択中に表示する入れ替えボタンの配置を純関数で計算します。
 *
 * Bukkit表示へ依存しないため単体試験で寸法・配置を検証できます。
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

    /** グリフの縮小率。ボタンに対する詰まり具合の調整として50%とします。 */
    const val GLYPH_SIZE_SCALE: Double = 0.5

    /** 変更前のグリフ中心補正。グリフ倍率に応じて比例拡大します。 */
    const val LEGACY_GLYPH_DY: Double = 0.006

    /** ボタンの一辺を、ノードアイコン一辺の50%として求めます。 */
    fun buttonSize(iconSize: Double): Double = iconSize * BUTTON_SIZE_RATIO

    /** ボタン列の中心高さ。アイコン上面から隙間を空けて積み上げます。 */
    fun buttonY(cy: Double, iconSize: Double, buttonSize: Double): Double =
        cy + iconSize / 2.0 + buttonSize / 2.0 + BUTTON_GAP

    /**
     * 中心から左右ボタンまでの距離です。
     * 左右ボタンを隙間だけ空けて隣接させ、対全体を中央揃え（左右対称）にします。
     */
    fun sideOffset(buttonSize: Double): Double = buttonSize / 2.0 + BUTTON_GAP / 2.0

    /** ホバー文言の高さ。ボタン列のさらに上へ退避させます。 */
    fun hoverY(buttonY: Double, buttonSize: Double): Double =
        buttonY + buttonSize / 2.0 + HOVER_LIFT

    /**
     * グリフ寸法。ボタン倍率へ比例させたうえで50%に縮小し、
     * ボタン内の余白を保ちます。アイコン比率の変更に追従するよう、
     * 固定値ではなく実寸比で換算します。
     */
    fun glyphSize(buttonSize: Double, minPitch: Double): Double =
        LEGACY_GLYPH_SIZE * (buttonSize / (minPitch * LEGACY_BUTTON_PITCH_RATIO)) * GLYPH_SIZE_SCALE

    /** グリフ中心補正。グリフ倍率へ比例させます。 */
    fun glyphDy(glyphSize: Double): Double =
        LEGACY_GLYPH_DY * (glyphSize / LEGACY_GLYPH_SIZE)

    /**
     * 折り畳み／展開の切り替え操作ができるかを判定します。
     *
     * ホバー案内の表示条件です。折り畳み操作自体はノードの再クリックを
     * 唯一の経路とし、専用の操作ボタンは設けません。
     * 折り畳みノードは常時展開可、FOR開始はペア有り、条件分岐は合流済みのみ、
     * それ以外（通常ノード・合流・FOR終了・未合流条件）は対象外です。
     */
    fun isFoldToggleAvailable(node: CommandNode, graph: CommandGraph): Boolean = when (node.type) {
        CommandType.FOLDED_FOR, CommandType.FOLDED_IF -> true
        CommandType.FOR_START -> node.pairedNodeId != null
        CommandType.CONDITION -> GraphEditor.canFoldCondition(graph, node.id)
        else -> false
    }
}
