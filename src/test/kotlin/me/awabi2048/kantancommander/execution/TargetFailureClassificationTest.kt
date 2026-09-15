package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.error.ErrorCode
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.RegionAnchorKind
import me.awabi2048.kantancommander.model.RegionResolution
import me.awabi2048.kantancommander.model.SearchOriginSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #34-5: 対象解決失敗の分類試験です。
 *
 * 成功・正常な0件・未解決・不正を区別し、Target絞り込みとCondition判定で
 * 同じ原因に矛盾した意味付けをしないことを固定します。
 */
class TargetFailureClassificationTest {
    @Test
    fun `resolved region has no failure`() {
        assertNull(
            TargetFailureClassification.regionFailure(
                RegionResolution.Resolved(
                    worldName = "world",
                    origin = me.awabi2048.kantancommander.model.SpatialVector(0.0, 0.0, 0.0),
                    dx = 1.0,
                    dy = 1.0,
                    dz = 1.0,
                    minimumDistance = null,
                    maximumDistance = null,
                ),
                RegionAnchorKind.CONTROL_BLOCK,
            ),
        )
    }

    @Test
    fun `unresolved anchor is unresolved and temporary anchor keeps its code`() {
        val plain = requireNotNull(
            TargetFailureClassification.regionFailure(RegionResolution.UnresolvedAnchor, RegionAnchorKind.CONTROL_BLOCK),
        )
        assertTrue(plain.unresolved)
        assertEquals(ErrorCode.TARGET_SPEC_INVALID, plain.code)

        val temporary = requireNotNull(
            TargetFailureClassification.regionFailure(RegionResolution.UnresolvedAnchor, RegionAnchorKind.TEMPORARY),
        )
        assertTrue(temporary.unresolved)
        assertEquals(ErrorCode.TEMPORARY_UNRESOLVED, temporary.code)
    }

    @Test
    fun `world mismatch and invalid spec are invalid like condition evaluation`() {
        // Condition判定は同じ原因を INVALID とします。Target絞り込みも不正とし、
        // 正常な不一致（FALSE／0件）へ変換しません。
        val mismatch = requireNotNull(
            TargetFailureClassification.regionFailure(RegionResolution.WorldMismatch, RegionAnchorKind.FIXED),
        )
        assertFalse(mismatch.unresolved)
        assertEquals(ErrorCode.TARGET_SPEC_INVALID, mismatch.code)

        val invalid = requireNotNull(
            TargetFailureClassification.regionFailure(RegionResolution.InvalidSpec, RegionAnchorKind.CONTROL_BLOCK),
        )
        assertFalse(invalid.unresolved)
        assertEquals(ErrorCode.TARGET_SPEC_INVALID, invalid.code)
    }

    @Test
    fun `selection origin failure follows the position kind`() {
        val target = TargetFailureClassification.selectionOriginFailure(PositionKind.TARGET)
        assertFalse(target.unresolved)
        assertEquals(ErrorCode.TARGET_SPEC_INVALID, target.code)

        val temporary = TargetFailureClassification.selectionOriginFailure(PositionKind.TEMPORARY)
        assertTrue(temporary.unresolved)
        assertEquals(ErrorCode.TEMPORARY_UNRESOLVED, temporary.code)

        val coordinates = TargetFailureClassification.selectionOriginFailure(PositionKind.COORDINATES)
        assertTrue(coordinates.unresolved)
        assertEquals(ErrorCode.TARGET_SPEC_INVALID, coordinates.code)
    }

    @Test
    fun `search origin failure keeps temporary causes apart`() {
        val temporary = TargetFailureClassification.searchOriginFailure(SearchOriginSpec(positionTemp = "point"))
        assertTrue(temporary.unresolved)
        assertEquals(ErrorCode.TEMPORARY_UNRESOLVED, temporary.code)

        val plain = TargetFailureClassification.searchOriginFailure(SearchOriginSpec())
        assertTrue(plain.unresolved)
        assertEquals(ErrorCode.TARGET_SPEC_INVALID, plain.code)
    }
}
