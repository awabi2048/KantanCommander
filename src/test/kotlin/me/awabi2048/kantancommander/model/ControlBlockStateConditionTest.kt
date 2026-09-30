package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 制御ブロック条件の赤石信号強度指定テストです。
 *
 * `redstoneSignal` 欄は `4`・`4..6`・`3..`・`..2` の形式を受理し、
 * 空欄のみ「条件なし」として扱います。0だけを含む指定は無信号を判定する
 * 有効な範囲です。
 */
class ControlBlockStateConditionTest {
    private fun signalSpec(raw: String?): IntRange? =
        CommandNode(type = CommandType.CONDITION).apply {
            raw?.let { params["redstoneSignal"] = it }
        }.redstoneSignalSpec()

    @Test
    fun `blank signal means no condition`() {
        assertNull(signalSpec(null))
        assertNull(signalSpec(""))
    }

    @Test
    fun `zero signal range tests for no signal`() {
        // 0だけを含む指定は無信号を判定する有効な範囲です。
        assertEquals(0..0, signalSpec("0"))
        assertEquals(0..0, signalSpec("..0"))
        assertEquals(0..0, signalSpec("0..0"))
    }

    @Test
    fun `signal accepts single open and closed ranges`() {
        assertEquals(3..15, signalSpec("3.."))
        assertEquals(0..2, signalSpec("..2"))
        assertEquals(4..6, signalSpec("4..6"))
        assertEquals(4..4, signalSpec("4"))
        assertEquals(15..15, signalSpec("15"))
        // 区切りの前後空白は乱数範囲と同じく受理します。
        assertEquals(4..6, signalSpec("4 .. 6"))
    }

    @Test
    fun `malformed or out of range signal is rejected`() {
        // 構文上受理できない値は「条件なし」と区別できるよう拒否します。
        assertNull(CommandValueRules.parseSignalStrengthRange("abc"))
        assertNull(CommandValueRules.parseSignalStrengthRange("16"))
        assertNull(CommandValueRules.parseSignalStrengthRange("-1"))
        assertNull(CommandValueRules.parseSignalStrengthRange("3.5"))
        assertNull(CommandValueRules.parseSignalStrengthRange("5..3"))
        assertNull(CommandValueRules.parseSignalStrengthRange("1..16"))
        assertNull(CommandValueRules.parseSignalStrengthRange("..16"))
        assertNull(CommandValueRules.parseSignalStrengthRange("1..2..3"))
        assertNull(CommandValueRules.parseSignalStrengthRange(".."))
        // 空白だけの両端省略は受理しません。
        assertNull(CommandValueRules.parseSignalStrengthRange(" .. "))
    }

    @Test
    fun `deep copy keeps the signal specification`() {
        val original = CommandNode(type = CommandType.CONDITION).apply {
            params["redstoneSignal"] = "4..6"
        }
        val copy = CommandGraph(nodes = linkedMapOf(original.id to original)).deepCopy()
            .nodes.getValue(original.id)

        assertEquals("4..6", copy.params["redstoneSignal"])
    }
}
