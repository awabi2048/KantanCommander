package me.awabi2048.kantancommander.model

import me.awabi2048.kantancommander.data.GraphEditor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #8 の呼び出し先ディスク選択のテストです。
 *
 * ディスク選択時に内容スナップショットと引数宣言を転記し、宣言から消えた
 * 引数の古い値・変数指定を除去することを固定します。転記漏れがあると、
 * 呼び出し側で引数を設定できず実行時に不足します。
 */
class DiskCallSelectionTest {
    private fun disk(name: String, arguments: List<DiskArgument>): DiskScript =
        DiskScript(name = name).apply {
            this.arguments = arguments
            graph = CommandGraph.empty()
        }

    @Test
    fun `selection copies snapshot and declared arguments`() {
        val script = DiskScript(name = "caller")
        val node = GraphEditor.append(script.graph, CommandType.DISK_CALL)
        val calledId = UUID.randomUUID()
        val called = disk(
            "callee",
            listOf(
                DiskArgument("count", ValueType.NUMBER),
                DiskArgument("where", ValueType.LOCATION),
            ),
        )

        node.applyCalledDisk(calledId, called)

        assertEquals(calledId.toString(), node.string("diskId"))
        assertNotNull(node.snapshot)
        val declared = node.declaredArguments()
        assertEquals(listOf("count", "where"), declared.map { it.name })
        assertEquals(listOf(ValueType.NUMBER, ValueType.LOCATION), declared.map { it.type })
        assertTrue(node.isExplicitlyConfigured("declaredArgs"))
    }

    @Test
    fun `reselecting removes arguments that disappeared`() {
        val script = DiskScript(name = "caller")
        val node = GraphEditor.append(script.graph, CommandType.DISK_CALL)
        node.applyCalledDisk(
            UUID.randomUUID(),
            disk("first", listOf(DiskArgument("count", ValueType.NUMBER), DiskArgument("label", ValueType.STRING))),
        )
        node.params["arg_count"] = "1"
        node.params["argvar_label"] = "somewhere"

        node.applyCalledDisk(UUID.randomUUID(), disk("second", listOf(DiskArgument("count", ValueType.NUMBER))))

        // 残った引数の宣言と値は維持し、消えた引数の古い指定は除去します。
        assertEquals(listOf("count"), node.declaredArguments().map { it.name })
        assertEquals("1", node.string("arg_count"))
        assertFalse(node.params.containsKey("argvar_label"))
    }

    @Test
    fun `disk without arguments clears declared list`() {
        val script = DiskScript(name = "caller")
        val node = GraphEditor.append(script.graph, CommandType.DISK_CALL)
        node.applyCalledDisk(UUID.randomUUID(), disk("first", listOf(DiskArgument("count", ValueType.NUMBER))))

        node.applyCalledDisk(UUID.randomUUID(), disk("empty", emptyList()))

        assertTrue(node.declaredArguments().isEmpty())
        assertNull(node.params["declaredArgs"])
    }
}
