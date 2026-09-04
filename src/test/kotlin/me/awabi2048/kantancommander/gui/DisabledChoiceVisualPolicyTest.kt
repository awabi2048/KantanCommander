package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DisabledChoiceVisualPolicyTest {
    @Test
    fun `無効な選択肢は赤コンクリートと灰色文字へ統一されます`() {
        assertEquals(Material.RED_CONCRETE, DisabledChoiceVisualPolicy.material)
        assertEquals(NamedTextColor.GRAY, DisabledChoiceVisualPolicy.textColor)
        assertEquals(GuiNameStyle.MUTED, DisabledChoiceVisualPolicy.nameStyle)
    }

    @Test
    fun `無効理由ホバーは通常ホバーを上書きします`() {
        assertEquals(
            "制御ブロックのある位置は操作できません",
            DisabledChoiceVisualPolicy.hoverText(
                enabled = false,
                normal = "通常の説明",
                disabled = CommandSettingAvailabilityPolicy.CONTROL_BLOCK_POSITION_DISABLED_HOVER,
            ),
        )
        assertEquals(
            listOf("制御ブロックのある位置は操作できません"),
            DisabledChoiceVisualPolicy.hoverLines(
                enabled = false,
                normal = listOf("通常の説明"),
                disabled = listOf(CommandSettingAvailabilityPolicy.CONTROL_BLOCK_POSITION_DISABLED_HOVER),
            ),
        )
    }

    @Test
    fun `有効な選択肢は通常ホバーを維持します`() {
        assertEquals(
            "通常の説明",
            DisabledChoiceVisualPolicy.hoverText(
                enabled = true,
                normal = "通常の説明",
                disabled = "無効理由",
            ),
        )
        assertEquals(
            listOf("通常の説明"),
            DisabledChoiceVisualPolicy.hoverLines(
                enabled = true,
                normal = listOf("通常の説明"),
                disabled = listOf("無効理由"),
            ),
        )
    }
}
