package me.awabi2048.kantancommander.placement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * undo／redo 後の台帳再照合試験です。
 *
 * 失われた台帳セルと出現セルの両集合が単一の平行移動で結ばれる場合だけ
 * 受理し、件数不一致や形状変化は曖昧（null）とします。Bukkit非依存の純粋判定です。
 */
class WorldEditUndoRematchTest {
    @Test
    fun `複数セルが単一変位で結ばれる場合に受理する`() {
        val offset = WorldEditUndoRematch.matchTranslation(
            lost = listOf(Triple(10, 64, 0), Triple(12, 64, 0)),
            appeared = listOf(Triple(0, 64, 5), Triple(2, 64, 5)),
        )
        assertEquals(-10, offset?.x)
        assertEquals(0, offset?.y)
        assertEquals(5, offset?.z)
    }

    @Test
    fun `単独セルも受理する`() {
        val offset = WorldEditUndoRematch.matchTranslation(
            lost = listOf(Triple(10, 64, 0)),
            appeared = listOf(Triple(0, 64, 0)),
        )
        assertEquals(-10, offset?.x)
    }

    @Test
    fun `同一配置の復元は恒等変位で受理する`() {
        val offset = WorldEditUndoRematch.matchTranslation(
            lost = listOf(Triple(1, 64, 1), Triple(3, 64, 1)),
            appeared = listOf(Triple(1, 64, 1), Triple(3, 64, 1)),
        )
        assertEquals(0, offset?.x)
        assertEquals(0, offset?.y)
        assertEquals(0, offset?.z)
    }

    @Test
    fun `件数不一致は曖昧にする`() {
        assertNull(
            WorldEditUndoRematch.matchTranslation(
                lost = listOf(Triple(10, 64, 0), Triple(12, 64, 0)),
                appeared = listOf(Triple(0, 64, 0)),
            ),
        )
    }

    @Test
    fun `空集合は曖昧にする`() {
        assertNull(WorldEditUndoRematch.matchTranslation(emptyList(), emptyList()))
    }

    @Test
    fun `平行移動で結べない形状変化は曖昧にする`() {
        // 回転等で間隔が変わった場合は受理せず、警告と猶予へ委ねます。
        assertNull(
            WorldEditUndoRematch.matchTranslation(
                lost = listOf(Triple(10, 64, 0), Triple(12, 64, 0)),
                appeared = listOf(Triple(0, 64, 0), Triple(1, 65, 0)),
            ),
        )
    }
}
