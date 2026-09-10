package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.gui.GraphChangeKind
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #15 の検証分離条件の回帰テストです。
 *
 * 構造検証の再実行要否と構造エラーの再利用可否を一箇所へ集約し、
 * 推測による再検証のばらつきを防ぎます。検証器自体の走査は全量のままです。
 */
class IncrementalValidationPolicyTest {
    @Test
    fun `field edits are local and metadata stays silent`() {
        assertEquals(
            FieldImpact.FIELD_LOCAL,
            IncrementalValidationPolicy.classifyField(CommandType.WAIT, "seconds"),
        )
        assertEquals(
            FieldImpact.FIELD_LOCAL,
            IncrementalValidationPolicy.classifyField(CommandType.PLAY_SOUND, "volume"),
        )
        assertEquals(
            FieldImpact.METADATA,
            IncrementalValidationPolicy.classifyField(CommandType.WAIT, "__configured"),
        )
    }

    @Test
    fun `structural revalidation only for structure changes`() {
        assertTrue(IncrementalValidationPolicy.requiresStructuralRevalidation(GraphChangeKind.STRUCTURE))
        assertFalse(IncrementalValidationPolicy.requiresStructuralRevalidation(GraphChangeKind.FIELD_VALUE))
        assertFalse(IncrementalValidationPolicy.requiresStructuralRevalidation(GraphChangeKind.METADATA_ONLY))
        assertFalse(IncrementalValidationPolicy.requiresStructuralRevalidation(GraphChangeKind.NONE))
    }
}
