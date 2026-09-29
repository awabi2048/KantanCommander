package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.error.ErrorCode
import me.awabi2048.kantancommander.model.PositionKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #34-5: 対象解決失敗の分類試験です。
 *
 * 成功・正常な0件・未解決・不正を区別し、探索基準位置の解決失敗を
 * 原因別に分類する契約を固定します。
 */
class TargetFailureClassificationTest {
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
}
