package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.gui.GraphChangeKind
import me.awabi2048.kantancommander.model.CommandType

/**
 * 検証の増分化条件を一箇所へ集約します。
 *
 * issue #15 の検証分離基盤であり、構造検証と項目検証の再実行要否を
 * 呼び出し側で推測しないために使います。検証器自体の走査は全量のままですが、
 * 構造署名が変わらない保存で構造エラーを再計算しません。
 * Bukkitへ依存しないため単体テストで検証します。
 */
enum class FieldImpact {
    /** 接続構造へ影響し得ます。全量の再検証が必要です。 */
    STRUCTURE,

    /** 単一項目の値に閉じます。構造エラーの再利用が可能です。 */
    FIELD_LOCAL,

    /** 明示設定フラグ等の表示用付帯情報です。検証結果を変えません。 */
    METADATA,
}

object IncrementalValidationPolicy {
    /**
     * 項目変更の影響範囲を分類します。
     *
     * ノード種別は生成後に変わらず、接続は構造操作でだけ変わるため、
     * 通常のfield編集は [FieldImpact.FIELD_LOCAL] となります。
     * 将来「種別切替え」等の構造的項目が増えた場合はここへ追加し、
     * 全量再検証へ寄せます。
     */
    fun classifyField(nodeType: CommandType, fieldKey: String): FieldImpact {
        // 明示設定の記録自体は検証結果を変えないため下部表示の更新に留めます。
        if (fieldKey.isEmpty() || fieldKey.startsWith("ui.") || fieldKey == "__configured") {
            return FieldImpact.METADATA
        }
        // 現行の全CommandTypeで、params／Specの値変更だけでは接続構造は変わりません。
        // 構造操作（insert／delete／swap／fold／merge）は別経路のためここでは扱いません。
        return when (nodeType) {
            else -> FieldImpact.FIELD_LOCAL
        }
    }

    /** 構造検証の再計算が必要かを判定します。 */
    fun requiresStructuralRevalidation(change: GraphChangeKind): Boolean =
        change == GraphChangeKind.STRUCTURE

    /** 値変更だけの保存で構造エラーを再利用できるかを判定します。 */
    fun canReuseStructuralErrors(change: GraphChangeKind): Boolean =
        change == GraphChangeKind.FIELD_VALUE || change == GraphChangeKind.METADATA_ONLY

    /** 対象ノード集合に関わるエラーのみを抽出します。差分同期の要点化に使います。 */
    fun filterErrorsForNodes(
        errors: List<ScriptValidationError>,
        nodeIds: Set<java.util.UUID>,
    ): List<ScriptValidationError> {
        if (nodeIds.isEmpty()) return errors
        return errors.filter { it.nodeId == null || it.nodeId in nodeIds }
    }
}
