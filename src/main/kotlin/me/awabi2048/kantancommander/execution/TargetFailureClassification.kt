package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.error.ErrorCode
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.RegionAnchorKind
import me.awabi2048.kantancommander.model.RegionResolution
import me.awabi2048.kantancommander.model.SearchOriginSpec

/**
 * issue #34-5: 対象解決失敗の分類です。
 *
 * 成功・正常な0件・未解決・不正を区別し、#6の共通エラー体系へ接続します。
 * 領域判定の意味付けは Condition判定（基準未解決→未解決、world不一致・
 * 不正指定→不正）と一致させ、同じ原因に矛盾した意味付けをしません。
 * 詳細文言は呼出側で付与し、ここではコードと区分だけを確定します。
 */
internal data class TargetFailure(val code: ErrorCode, val unresolved: Boolean)

internal object TargetFailureClassification {
    /**
     * 領域解決の失敗を分類します。解決成功時はnullを返します。
     *
     * 一時変数基準の未解決は一時変数未解決、それ以外の基準未解決は
     * 対象指定不正の未解決、world不一致・不正指定は不正とします。
     */
    fun regionFailure(resolution: RegionResolution, anchorKind: RegionAnchorKind): TargetFailure? = when (resolution) {
        is RegionResolution.Resolved -> null
        RegionResolution.UnresolvedAnchor -> if (anchorKind == RegionAnchorKind.TEMPORARY) {
            TargetFailure(ErrorCode.TEMPORARY_UNRESOLVED, unresolved = true)
        } else {
            TargetFailure(ErrorCode.TARGET_SPEC_INVALID, unresolved = true)
        }
        RegionResolution.WorldMismatch ->
            TargetFailure(ErrorCode.TARGET_SPEC_INVALID, unresolved = false)
        RegionResolution.InvalidSpec ->
            TargetFailure(ErrorCode.TARGET_SPEC_INVALID, unresolved = false)
    }

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

    /**
     * 絞り込み用探索基準の解決失敗を分類します。
     *
     * 一時変数基準は一時変数未解決、それ以外は対象指定不正の未解決とします。
     */
    fun searchOriginFailure(searchOrigin: SearchOriginSpec?): TargetFailure {
        if (searchOrigin?.positionTemp?.takeIf(String::isNotBlank) != null) {
            return TargetFailure(ErrorCode.TEMPORARY_UNRESOLVED, unresolved = true)
        }
        return TargetFailure(ErrorCode.TARGET_SPEC_INVALID, unresolved = true)
    }
}
