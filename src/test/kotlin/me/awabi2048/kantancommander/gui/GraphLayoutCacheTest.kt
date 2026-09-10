package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * issue #15 のレイアウト再利用の回帰テストです。
 *
 * 値変更だけの保存で配置計算を繰り返さないことを、署名キー単位で検証します。
 * 無効化条件は構造署名の変化へ一元化し、設定上限の変更時だけ破棄します。
 */
class GraphLayoutCacheTest {
    @Test
    fun `same signature reuses layout without recompute`() {
        val cache = GraphLayoutCache()
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val key = GraphStructureFingerprint.fingerprint(graph)

        var computations = 0
        val first = cache.getOrCompute(key) {
            computations++
            GraphLayoutEngine.layout(graph)
        }
        val second = cache.getOrCompute(key) {
            computations++
            GraphLayoutEngine.layout(graph)
        }

        assertSame(first, second)
        assertEquals(1, computations)
        assertEquals(1, cache.size())
    }

    @Test
    fun `different signature computes separately`() {
        val cache = GraphLayoutCache()
        val before = CommandGraph.empty()
        GraphEditor.append(before, CommandType.WAIT)
        val after = before.deepCopy()
        GraphEditor.append(after, CommandType.DISPLAY_TEXT)

        val first = cache.getOrCompute(GraphStructureFingerprint.fingerprint(before)) {
            GraphLayoutEngine.layout(before)
        }
        val second = cache.getOrCompute(GraphStructureFingerprint.fingerprint(after)) {
            GraphLayoutEngine.layout(after)
        }

        assertEquals(2, cache.size())
        // 配置が変わるため内容も変わります。
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second)
    }

    @Test
    fun `evicts oldest beyond capacity`() {
        val cache = GraphLayoutCache(maxEntries = 2)
        val graphs = (1..3).map { count ->
            CommandGraph.empty().also { graph ->
                repeat(count) { GraphEditor.append(graph, CommandType.WAIT) }
            }
        }
        graphs.forEach { graph ->
            cache.getOrCompute(GraphStructureFingerprint.fingerprint(graph)) {
                GraphLayoutEngine.layout(graph)
            }
        }

        assertEquals(2, cache.size())
    }

    @Test
    fun `clear drops all entries`() {
        val cache = GraphLayoutCache()
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        cache.getOrCompute(GraphStructureFingerprint.fingerprint(graph)) {
            GraphLayoutEngine.layout(graph)
        }
        cache.clear()

        assertEquals(0, cache.size())
    }
}
