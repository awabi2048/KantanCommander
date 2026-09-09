package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #15 の差分更新基盤の回帰テストです。
 *
 * 構造変更と値変更の分類、署名の安定性、再描画計画を純粋関数で検証します。
 * BukkitやCC-Systemへ依存しないため、保存・描画の抑止条件をここで担保します。
 */
class GestureEditorDirtyStateTest {
    @Test
    fun `identical graphs are none`() {
        val graph = CommandGraph.empty()
        val node = GraphEditor.append(graph, CommandType.WAIT)
        graph.nodes.getValue(node.id).params["seconds"] = "1"

        val copy = graph.deepCopy()

        assertEquals(GraphChangeKind.NONE, GraphChangeClassifier.classify(graph, copy))
        assertEquals(GraphStructureFingerprint.fingerprint(graph), GraphStructureFingerprint.fingerprint(copy))
    }

    @Test
    fun `param change is field value and keeps signature`() {
        val before = CommandGraph.empty()
        val node = GraphEditor.append(before, CommandType.WAIT)
        val after = before.deepCopy()
        after.nodes.getValue(node.id).params["seconds"] = "5"

        assertEquals(GraphChangeKind.FIELD_VALUE, GraphChangeClassifier.classify(before, after))
        // 値変更だけでは配置を使い回すため署名は変わりません。
        assertEquals(GraphStructureFingerprint.fingerprint(before), GraphStructureFingerprint.fingerprint(after))
    }

    @Test
    fun `configured flag change is metadata only`() {
        val before = CommandGraph.empty()
        val node = GraphEditor.append(before, CommandType.WAIT)
        val after = before.deepCopy()
        after.nodes.getValue(node.id).markConfigured("seconds")

        assertEquals(GraphChangeKind.METADATA_ONLY, GraphChangeClassifier.classify(before, after))
        assertEquals(GraphStructureFingerprint.fingerprint(before), GraphStructureFingerprint.fingerprint(after))
    }

    @Test
    fun `appended node is structure and changes signature`() {
        val before = CommandGraph.empty()
        GraphEditor.append(before, CommandType.WAIT)
        val after = before.deepCopy()
        GraphEditor.append(after, CommandType.DISPLAY_TEXT)

        assertEquals(GraphChangeKind.STRUCTURE, GraphChangeClassifier.classify(before, after))
        assertNotEquals(GraphStructureFingerprint.fingerprint(before), GraphStructureFingerprint.fingerprint(after))
    }

    @Test
    fun `edge change is structure`() {
        val before = CommandGraph.empty()
        val first = GraphEditor.append(before, CommandType.WAIT)
        val second = GraphEditor.append(before, CommandType.DISPLAY_TEXT)
        val after = before.deepCopy()
        // 接続の付け替えは配置を変えるため構造差分とします。
        after.nodes.getValue(first.id).next = null
        after.nodes.getValue(second.id).next = first.id

        assertEquals(GraphChangeKind.STRUCTURE, GraphChangeClassifier.classify(before, after))
    }

    @Test
    fun `render plan covers structure field metadata and idle`() {
        assertEquals(RenderPlan.FULL, EditorRenderPlanner.planFor(GraphChangeKind.STRUCTURE))
        assertEquals(RenderPlan.FULL, EditorRenderPlanner.planFor(GraphChangeKind.FIELD_VALUE))
        assertEquals(RenderPlan.LOWER_ONLY, EditorRenderPlanner.planFor(GraphChangeKind.METADATA_ONLY))
        assertEquals(RenderPlan.NONE, EditorRenderPlanner.planFor(GraphChangeKind.NONE))
        // 選択・視点の変更だけでも上部を作り直します。
        assertEquals(
            RenderPlan.UPPER_ONLY,
            EditorRenderPlanner.planFor(GraphChangeKind.NONE, selectionChanged = true),
        )
    }

    @Test
    fun `pre-save layout only for signature changes`() {
        // 値変更だけでは配置が変わらないため保存前の検証レイアウトを省きます。
        assertFalse(EditorRenderPlanner.needsPreSaveLayout("sig", "sig"))
        assertTrue(EditorRenderPlanner.needsPreSaveLayout("before", "after"))
    }

    @Test
    fun `field value change keeps signature so pre-save layout is skipped`() {
        val before = CommandGraph.empty()
        val node = GraphEditor.append(before, CommandType.WAIT)
        val after = before.deepCopy()
        after.nodes.getValue(node.id).params["seconds"] = "5"

        // 分類と署名と保存前判定が一貫していることを検証します。
        assertEquals(GraphChangeKind.FIELD_VALUE, GraphChangeClassifier.classify(before, after))
        assertFalse(
            EditorRenderPlanner.needsPreSaveLayout(
                GraphStructureFingerprint.fingerprint(before),
                GraphStructureFingerprint.fingerprint(after),
            ),
        )
    }
}
