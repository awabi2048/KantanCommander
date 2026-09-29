package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.error.ErrorCode
import me.awabi2048.kantancommander.model.PositionKind

/**
 * issue #34-5: 対象解決失敗の分類です。
 *
 * 成功・正常な0件・未解決・不正を区別し、#6の共通エラー体系へ接続します。
 * 詳細文言は呼出側で付与し、ここではコードと区分だけを確定します。
 */
internal data class TargetFailure(val code: ErrorCode, val unresolved: Boolean)

internal object TargetFailureClassification {
    /**
     * 探索基準位置の解決失敗を分類します。
     *
     * TARGET種別は設定不正、一時変数位置は一時変数未解決、
     * それ以外は対象指定不正の未解決とします。
     */
    fun selectionOriginFailure(kind: PositionKind?): TargetFailure {
        if (kind == PositionKind.TARGET) return TargetFailure(ErrorCode.TARGET_SPEC_INVALID, unresolved = false)
        if (kind == PositionKind.TEMPORARY) return TargetFailure(ErrorCode.TEMPORARY_UNRESOLVED, unresolved = true)
        return TargetFailure(ErrorCode.TARGET_SPEC_INVALID, unresolved = true)
    }
}
