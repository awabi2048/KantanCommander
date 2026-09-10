package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #15 と #18 の共有契約である preview／commit の回帰テストです。
 *
 * previewは保存・revisionへ触れず、commit時にだけ正本へ反映することを
 * session-localな保管場所の振る舞いとして検証します。他editorへの共有は
 * 低頻度・明示channelに限定し、常時全送信しないことを間引き条件で担保します。
 */
class GesturePreviewContractTest {
    @Test
    fun `preview does not commit until consumed`() {
        val store = TransientPreviewStore()
        val nodeId = UUID.randomUUID()

        store.begin(7L)
        store.update(nodeId, "seconds", "5")

        assertTrue(store.hasPreview)
        assertEquals("5", store.current(nodeId, "seconds"))
        assertEquals(1, store.count)

        val committed = store.consumeForCommit()

        assertEquals("5", committed[FieldPreviewKey(nodeId, "seconds")])
        assertFalse(store.hasPreview)
    }

    @Test
    fun `cancel discards preview`() {
        val store = TransientPreviewStore()
        val nodeId = UUID.randomUUID()
        store.begin(3L)
        store.update(nodeId, "seconds", "9")
        store.cancel()

        assertFalse(store.hasPreview)
        assertEquals(null, store.current(nodeId, "seconds"))
    }

    @Test
    fun `removeNode drops related previews`() {
        val store = TransientPreviewStore()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        store.begin(1L)
        store.update(first, "seconds", "1")
        store.update(second, "seconds", "2")
        store.removeNode(first)

        assertEquals(null, store.current(first, "seconds"))
        assertEquals("2", store.current(second, "seconds"))
    }

    @Test
    fun `sharing is throttled to low frequency`() {
        val store = TransientPreviewStore()
        store.begin(1L)
        store.update(UUID.randomUUID(), "seconds", "4")

        // 初回は送り、直後は抑止し、間隔経過後に再送します。
        assertTrue(store.shouldSharePreview(nowMillis = 1_000L, lastBroadcastAtMillis = 0L))
        assertFalse(store.shouldSharePreview(nowMillis = 1_100L, lastBroadcastAtMillis = 1_000L))
        assertTrue(store.shouldSharePreview(nowMillis = 1_300L, lastBroadcastAtMillis = 1_000L))
        // 空のpreviewは送りません。
        store.cancel()
        assertFalse(store.shouldSharePreview(nowMillis = 2_000L, lastBroadcastAtMillis = 1_000L))
    }

    @Test
    fun `throttle helper matches store policy`() {
        assertTrue(SharedPreviewThrottle.shouldBroadcast(1_000L, 0L))
        assertFalse(SharedPreviewThrottle.shouldBroadcast(1_100L, 1_000L))
        assertTrue(SharedPreviewThrottle.shouldBroadcast(1_250L, 1_000L))
    }

    @Test
    fun `commit applies all and clears the store`() {
        val store = TransientPreviewStore()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        store.begin(4L)
        store.update(first, "seconds", "1")
        store.update(second, "seconds", "2")
        val applied = mutableListOf<FieldPreviewKey>()

        val result = commitPreviews(store) { key, _ ->
            applied += key
            true
        }

        assertTrue(result.isSuccess)
        assertEquals(2, result.appliedCount)
        assertTrue(result.failed.isEmpty())
        assertEquals(
            setOf(FieldPreviewKey(first, "seconds"), FieldPreviewKey(second, "seconds")),
            applied.toSet(),
        )
        assertFalse(store.hasPreview)
    }

    @Test
    fun `commit restores failures without losing final values`() {
        val store = TransientPreviewStore()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        store.begin(4L)
        store.update(first, "seconds", "1")
        store.update(second, "seconds", "2")

        val result = commitPreviews(store) { key, _ -> key.nodeId == first }

        assertFalse(result.isSuccess)
        assertEquals(1, result.appliedCount)
        assertEquals(mapOf(FieldPreviewKey(second, "seconds") to "2"), result.failed)
        // 失敗分は保管場所へ戻り、最終値を失いません。
        assertEquals("2", store.current(second, "seconds"))
        assertEquals(null, store.current(first, "seconds"))
    }

    @Test
    fun `commit exception counts as failure`() {
        val store = TransientPreviewStore()
        val nodeId = UUID.randomUUID()
        store.begin(4L)
        store.update(nodeId, "seconds", "1")

        val result = commitPreviews(store) { _, _ -> error("保存境界の例外") }

        assertFalse(result.isSuccess)
        assertEquals("1", store.current(nodeId, "seconds"))
    }

    @Test
    fun `commit without preview is empty`() {
        val store = TransientPreviewStore()

        val result = commitPreviews(store) { _, _ -> false }

        assertEquals(PreviewCommitResult.EMPTY, result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `command type reference keeps contract compilation anchor`() {
        // 将来#18側で種別別の既定方針を分岐する際の錨です。現段階では
        // 値変更が構造へ影響しないことを型参照で明示します。
        assertEquals(CommandType.WAIT, CommandType.valueOf("WAIT"))
    }
}
