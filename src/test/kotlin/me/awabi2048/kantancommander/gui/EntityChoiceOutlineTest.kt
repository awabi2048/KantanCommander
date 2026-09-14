package me.awabi2048.kantancommander.gui

import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * エンティティ操作の選択・設定済み表示規則のテストです。
 *
 * 「選択している状態」は排他的に1件、「値を設定済みである状態」は
 * 非排他的に複数件へ付与できることを、縁色規則の真理値表で固定します。
 */
class EntityChoiceOutlineTest {
    @Test
    fun `outline follows the selection first rule`() {
        // 選択中は設定済み有無にかかわらず白縁とします。
        assertEquals(
            Material.WHITE_CONCRETE,
            GestureSettingVisualPolicy.entityChoiceOutlineMaterial(selected = true, configured = false),
        )
        assertEquals(
            Material.WHITE_CONCRETE,
            GestureSettingVisualPolicy.entityChoiceOutlineMaterial(selected = true, configured = true),
        )
        // 未選択の設定済みは空色縁とします。偽値も入力として扱うため、
        // 真偽値の種類では区別しません。
        assertEquals(
            Material.LIGHT_BLUE_CONCRETE,
            GestureSettingVisualPolicy.entityChoiceOutlineMaterial(selected = false, configured = true),
        )
        assertNull(
            GestureSettingVisualPolicy.entityChoiceOutlineMaterial(selected = false, configured = false),
        )
    }

    @Test
    fun `tag row fits three frames within the column pitch`() {
        // 名札＋モード＋削除の3枠総幅を列ピッチ以内に収めます。
        // モード枠は削除枠と同一寸法とします。
        assertEquals(
            GestureLowerPanel.WORLD_VARIABLE_DELETE_WIDTH,
            GestureLowerPanel.TAG_MODE_WIDTH,
        )
        val total = GestureLowerPanel.TAG_CARD_WIDTH +
            GestureLowerPanel.TAG_ICON_GAP +
            GestureLowerPanel.TAG_MODE_WIDTH +
            GestureLowerPanel.TAG_ICON_GAP +
            GestureLowerPanel.WORLD_VARIABLE_DELETE_WIDTH
        assertEquals(1.00, total, 1e-9)
    }
}
