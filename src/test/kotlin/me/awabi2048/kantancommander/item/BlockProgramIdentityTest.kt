package me.awabi2048.kantancommander.item

import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.TimerSetting
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class BlockProgramIdentityTest {
    private fun script(name: String = "program") = DiskScript(name = name, owner = UUID.randomUUID())

    @Test
    fun `copies with new id and timestamp keep the same content`() {
        val source = script()
        // 破壊時の出力は毎回copyForItemでID・作成日時が変わるため、それだけでは別物にしない。
        val copied = source.copy(id = UUID.randomUUID(), createdAt = 1L, revision = 5L)
        assertTrue(BlockProgramIdentity.sameContent(source, copied))
    }

    @Test
    fun `renamed or reconfigured programs are different`() {
        val source = script()
        assertFalse(BlockProgramIdentity.sameContent(source, script(name = "other")))
        assertFalse(
            BlockProgramIdentity.sameContent(
                source,
                source.copy(timer = TimerSetting(enabled = true, intervalSeconds = 5.0)),
            ),
        )
        assertFalse(BlockProgramIdentity.sameContent(source, source.copy(contentModified = true)))
    }
}
