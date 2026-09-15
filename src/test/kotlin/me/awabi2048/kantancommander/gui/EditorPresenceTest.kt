package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #13 の共同編集 presence を固定します。
 *
 * 開閉・退室相当・複数編集者の記録を検証します。内容更新（revision）とは
 * 無関係であり、保存・検証へは触れません。
 */
class EditorPresenceTest {
    @Test
    fun `open editors are listed per script`() {
        val presence = EditorPresenceTracker()
        val script = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        presence.open(script, first)
        presence.open(script, second)

        assertEquals(listOf(first, second), presence.editorsFor(script))
        assertEquals(script, presence.scriptFor(first))
    }

    @Test
    fun `closed editors disappear immediately`() {
        val presence = EditorPresenceTracker()
        val script = UUID.randomUUID()
        val player = UUID.randomUUID()

        presence.open(script, player)
        presence.close(player)

        assertTrue(presence.editorsFor(script).isEmpty())
        assertNull(presence.scriptFor(player))
    }

    @Test
    fun `reopening moves the player to the new script`() {
        val presence = EditorPresenceTracker()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val player = UUID.randomUUID()

        presence.open(first, player)
        presence.open(second, player)

        assertTrue(presence.editorsFor(first).isEmpty())
        assertEquals(listOf(player), presence.editorsFor(second))
    }

    @Test
    fun `close all clears every record`() {
        val presence = EditorPresenceTracker()
        val script = UUID.randomUUID()

        presence.open(script, UUID.randomUUID())
        presence.open(script, UUID.randomUUID())
        presence.closeAll()

        assertTrue(presence.editorsFor(script).isEmpty())
    }

    @Test
    fun `unknown scripts and players stay empty`() {
        val presence = EditorPresenceTracker()

        assertTrue(presence.editorsFor(UUID.randomUUID()).isEmpty())
        assertNull(presence.scriptFor(UUID.randomUUID()))
        // 開いていない Player の終了は何もしない。
        presence.close(UUID.randomUUID())
    }
}
