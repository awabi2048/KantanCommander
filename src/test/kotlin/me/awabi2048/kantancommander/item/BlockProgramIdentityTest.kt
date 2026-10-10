package me.awabi2048.kantancommander.item

import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.DiskScript
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class BlockProgramIdentityTest {
    private fun script(name: String = "program") = DiskScript(name = name)

    @Test
    fun `copies with new id keep the same content`() {
        val source = script()
        // 破壊時の出力は毎回copyForItemでIDが変わるため、それだけでは別物にしない。
        val copied = source.copy(id = UUID.randomUUID(), revision = 5L)
        assertTrue(BlockProgramIdentity.sameContent(source, copied))
    }

    @Test
    fun `renamed or reconfigured programs are different`() {
        val source = script()
        assertFalse(BlockProgramIdentity.sameContent(source, script(name = "other")))
        assertFalse(
            BlockProgramIdentity.sameContent(
                source,
                source.copy(trigger = ScriptTrigger(TriggerKind.TIMER, intervalSeconds = 5.0)),
            ),
        )
        // 引数定義の差も別物として扱います。
        assertFalse(
            BlockProgramIdentity.sameContent(
                source,
                source.copy(
                    arguments = mutableListOf(
                        me.awabi2048.kantancommander.model.DiskArgument(
                            name = "amount",
                            type = me.awabi2048.kantancommander.model.ValueType.NUMBER,
                        ),
                    ),
                ),
            ),
        )
    }
}
