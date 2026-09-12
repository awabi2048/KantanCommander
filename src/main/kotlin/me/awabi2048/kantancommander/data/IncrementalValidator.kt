package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.error.ValidationPhase
import me.awabi2048.kantancommander.gui.GraphChangeClassifier
import me.awabi2048.kantancommander.gui.GraphChangeKind
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.TypedValue
import java.util.UUID

/**
 * 値変更時の検証結果を増分更新します。
 *
 * issue #15 の増分検証として、保存ごとの全量再検証を、変更ノードだけの
 * 再検証＋不変部分の再利用へ置き換えます。無効化条件は以下へ一元化します。
 *
 * - 構造差分（接続・種別・入口の変化）では使わず全量へ委譲する
 * - 一時変数の可視性が変わり得る変更（TEMP_SET絡み）では全量へ委譲する
 * - snapshot内部の変更では全量へ委譲する（表示・保存の別正本のため）
 * - 全体設定（タイマー等）は毎回再計算する（安価でstaleにしない）
 * - 構造由来エラーは接続不変のため再利用する
 *
 * 全量へ委譲すべき場合は null を返し、呼び出し側は従来の全量検証を使います。
 * Bukkitへ依存しないため単体テストで全量一致を検証します。
 */
object IncrementalValidator {
    /**
     * 前回検証結果から新世代の検証結果を求めます。
     *
     * [previousErrors] は [previousGraph] に対する全量検証の結果でなければ
     * なりません。[script] は新世代の正本です。項目検証に必要な変数定義は
     * 全量側と同じもの（未配置編集時は null）を渡します。
     */
    fun refresh(
        previousGraph: CommandGraph,
        previousErrors: List<ScriptValidationError>,
        script: DiskScript,
        limits: GraphLimits = GraphLimits(),
        variableDefinitions: Map<String, TypedValue>? = null,
    ): List<ScriptValidationError>? {
        val change = GraphChangeClassifier.classify(previousGraph, script.graph)
        if (IncrementalValidationPolicy.requiresStructuralRevalidation(change)) return null
        val changed = GraphChangeClassifier.changedNodeIds(previousGraph, script.graph)
        // snapshot内部は表示・保存の別正本であり、単一ノード検証の対象外のため
        // 変更が及ぶ場合は全量へ委譲し、staleも欠落も作りません。
        if (changed.any { !containsStructuredId(script.graph, it) }) return null
        // 一時変数の可視性は下流へ波及するため、TEMP_SET絡みは全量へ委譲します。
        if (changed.any { involvesTemporaryAvailability(script.graph, it) }) return null
        val phase = if (variableDefinitions == null) ValidationPhase.EDIT_TIME else ValidationPhase.PREFLIGHT
        val errors = mutableListOf<ScriptValidationError>()
        errors += ExecutableScriptValidator.validateScriptRoot(script, phase)
        // 構造由来は接続不変のため再利用し、変更ノードの項目エラーだけを落とします。
        // 空の除外集合でも全件残留しないよう、否定条件で直接絞ります。
        // 全体設定由来（nodeIdなし・非構造）は上で再計算済みのため落とします。
        errors += previousErrors.filter { previous ->
            previous.phase != ValidationPhase.PERSISTED_STRUCTURAL &&
                previous.nodeId != null &&
                previous.nodeId !in changed
        }
        changed.forEach { nodeId ->
            errors += ExecutableScriptValidator.validateNodeLocal(script.graph, nodeId, limits, variableDefinitions)
        }
        return errors
    }

    /**
     * 単一ノード検証の解決範囲に含まれるかを判定します。
     *
     * 外側グラフと折り畳み内部が対象です。snapshot内部は別正本のため対象外です。
     */
    private fun containsStructuredId(graph: CommandGraph, id: UUID): Boolean {
        if (id in graph.nodes) return true
        return graph.nodes.values.any { node ->
            node.foldedGraph?.let { containsStructuredId(it, id) } == true
        }
    }

    /**
     * 指定変更が一時変数の可視性へ波及し得るかを判定します。
     *
     * 変更ノード自体がTEMP_SETの場合と、折り畳み内部にTEMP_SETを含む外側が
     * 変わった場合に真を返します。snapshot内部は別データフローのため見ません。
     */
    private fun involvesTemporaryAvailability(graph: CommandGraph, changedId: UUID): Boolean {
        val node = findStructuredNode(graph, changedId) ?: return true
        if (node.type == CommandType.TEMP_SET) return true
        return node.foldedGraph?.let(::subtreeHasTempSet) == true
    }

    private fun findStructuredNode(graph: CommandGraph, id: UUID): CommandNode? {
        graph.nodes[id]?.let { return it }
        graph.nodes.values.forEach { node ->
            node.foldedGraph?.let { findStructuredNode(it, id) }?.let { return it }
        }
        return null
    }

    private fun subtreeHasTempSet(graph: CommandGraph): Boolean =
        graph.nodes.values.any { node ->
            node.type == CommandType.TEMP_SET || node.foldedGraph?.let(::subtreeHasTempSet) == true
        }
}
