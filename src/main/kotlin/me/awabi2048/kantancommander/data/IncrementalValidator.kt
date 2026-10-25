package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.error.ValidationPhase
import me.awabi2048.kantancommander.gui.GraphChangeClassifier
import me.awabi2048.kantancommander.gui.GraphChangeKind
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.TypedValue
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import java.util.UUID

/**
 * 値変更時の検証結果を増分更新します。
 *
 * issue #15 の増分検証として、保存ごとの全量再検証を、変更ノードだけの
 * 再検証＋不変部分の再利用へ置き換えます。無効化条件は以下へ一元化します。
 *
 * - 構造差分（接続・種別・入口の変化）では使わず全量へ委譲する
 * - 一時変数の可視性が変わり得る変更（TEMP_SET絡み）では全量へ委譲する
 * - 全体設定（タイマー等）は毎回再計算する（安価でstaleにしない）
 * - 構造由来エラーは接続不変のため再利用する
 *
 * DISK_CALL の参照先は diskId で別正本を解決するため、呼出し先ディスクの
 * 編集は本グラフの差分に現れません。参照先の変化は呼出し元の保存・再検証時に
 * 反映されます。
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
     * [previousSeeds] は前回検証時の実行開始時定義表です。引数定義・イベント種別の
     * 変更は全ノードの定義可視性に波及するため、不一致時は全量へ委譲します。
     */
    fun refresh(
        previousGraph: CommandGraph,
        previousErrors: List<ScriptValidationError>,
        script: DiskScript,
        limits: GraphLimits = GraphLimits(),
        variableDefinitions: Map<String, TypedValue>? = null,
        previousSeeds: Map<String, ValueType> = emptyMap(),
        diskResolver: (UUID) -> DiskScript? = { null },
    ): List<ScriptValidationError>? {
        val change = GraphChangeClassifier.classify(previousGraph, script.graph)
        if (IncrementalValidationPolicy.requiresStructuralRevalidation(change)) return null
        val changed = GraphChangeClassifier.changedNodeIds(previousGraph, script.graph)
        // 単一ノード検証の対象は外側グラフと折り畳み内部です。それ以外への
        // 変更が及ぶ場合は全量へ委譲し、staleも欠落も作りません。
        if (changed.any { !containsStructuredId(script.graph, it) }) return null
        // 一時変数の可視性は下流へ波及するため、定義に影響する変更では全量へ委譲します。
        // 新旧どちらの世代で定義寄与があっても委譲し、寄与の消失を見逃しません。
        if (changed.any {
                involvesTemporaryAvailability(script.graph, it) ||
                    involvesTemporaryAvailability(previousGraph, it)
            }
        ) return null
        // issue #8: 戻り値の型一貫性はグラフ全体で判定するため、
        // RETURN_VALUE絡みの変更では全量へ委譲します。
        if (changed.any { findStructuredNode(script.graph, it)?.type == CommandType.RETURN_VALUE }) return null
        // issue #7/#8: 実行開始時定義（イベント引数・自ディスク引数）の変更は
        // 全ノードの定義可視性に波及するため、全量へ委譲します。
        val seeds = ExecutableScriptValidator.seedTemporaries(script)
        if (seeds != previousSeeds) return null
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
            errors += ExecutableScriptValidator.validateNodeLocal(
                script.graph,
                nodeId,
                limits,
                variableDefinitions,
                seeds,
                diskResolver,
            )
        }
        return errors
    }

    /**
     * 単一ノード検証の解決範囲に含まれるかを判定します。
     *
     * 外側グラフと折り畳み内部が対象です。
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
     * 変更ノード自体がTEMP_SETまたはEXECUTION定義（DEFINE／SET／DELETE）の場合と、
     * 折り畳み内部にそれらを含む外側が変わった場合に真を返します。
     * 判定条件は `analyzeTemporaryAvailability` のtransferと同一であり、
     * 定義に影響する変更の見逃しを作りません。DISK_CALL の参照先は別
     * データフローのため見ません。
     */
    private fun involvesTemporaryAvailability(graph: CommandGraph, changedId: UUID): Boolean {
        val node = findStructuredNode(graph, changedId) ?: return true
        if (node.type == CommandType.TEMP_SET) return true
        if (isExecutionDefinition(node)) return true
        val folded = node.foldedGraph ?: return false
        return subtreeHasTempSet(folded) || subtreeHasExecutionDefinition(folded)
    }

    /**
     * EXECUTION変数の定義寄与を持つノードかを判定します。
     *
     * scope・操作の解釈は `analyzeTemporaryAvailability` のtransferと同一です。
     * CHANGE／COPY は定義の有無を変えないため対象外です。
     */
    private fun isExecutionDefinition(node: CommandNode): Boolean {
        if (node.type != CommandType.VARIABLE) return false
        if (VariableScope.parse(node.string("scope")) != VariableScope.EXECUTION) return false
        return when (runCatching { VariableOperation.valueOf(node.string("operation")) }.getOrNull()) {
            VariableOperation.DEFINE, VariableOperation.SET, VariableOperation.DELETE -> true
            else -> false
        }
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

    private fun subtreeHasExecutionDefinition(graph: CommandGraph): Boolean =
        graph.nodes.values.any { node ->
            isExecutionDefinition(node) || node.foldedGraph?.let(::subtreeHasExecutionDefinition) == true
        }
}
