package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TestLogParamPresentationTest {
    @Test
    fun `resolves editor field labels per command type`() {
        // WAITのsecondsはエディタと同じ秒数項目へ対応します。
        val wait = CommandNode(type = CommandType.WAIT)
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS,
            TestLogParamPresentation.labelKey(CommandType.WAIT, wait, "seconds"),
        )
    }

    @Test
    fun `returns null for unknown parameter keys`() {
        // 対応表にない内部キーは呼び出し側で原文表示へ退避します。
        val wait = CommandNode(type = CommandType.WAIT)
        assertNull(TestLogParamPresentation.labelKey(CommandType.WAIT, wait, "unknown_key"))
        assertNull(TestLogParamPresentation.labelKey(CommandType.WAIT, null, "unknown_key"))
    }
}
