package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.gui.GraphChangeKind
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

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

    @Test
    fun `structural errors are reusable for value only changes`() {
        assertTrue(IncrementalValidationPolicy.canReuseStructuralErrors(GraphChangeKind.FIELD_VALUE))
        assertTrue(IncrementalValidationPolicy.canReuseStructuralErrors(GraphChangeKind.METADATA_ONLY))
        assertFalse(IncrementalValidationPolicy.canReuseStructuralErrors(GraphChangeKind.STRUCTURE))
        assertFalse(IncrementalValidationPolicy.canReuseStructuralErrors(GraphChangeKind.NONE))
    }

    @Test
    fun `error filtering keeps global errors and drops unrelated nodes`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val errors = listOf(
            ScriptValidationError("root", null, setOf("timer"), "timer"),
            ScriptValidationError("root/1", first, setOf("seconds"), "first"),
            ScriptValidationError("root/2", second, setOf("seconds"), "second"),
        )

        val filtered = IncrementalValidationPolicy.filterErrorsForNodes(errors, setOf(first))

        // 全体エラーは残し、無関係ノードのエラーだけを落とします。
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.nodeId == null })
        assertTrue(filtered.any { it.nodeId == first })
    }
}
