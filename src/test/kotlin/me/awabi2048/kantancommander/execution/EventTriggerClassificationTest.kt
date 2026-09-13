package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.model.EventTriggerKind
import org.bukkit.block.data.AnaloguePowerable
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Powerable
import org.bukkit.block.data.type.Switch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/**
 * issue #7 の信号変化の分類テストです。
 *
 * ボタン・レバーを素材名で区別し、感圧板は加重板または感圧板素材で判定します。
 * オン／オフの変化辺が対応するイベント種別へ写り、感圧板は離した時を提供しない
 * ことを固定します。サーバーなしで実行できます。
 */
class EventTriggerClassificationTest {
    private fun data(vararg types: Class<*>): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        (listOf(BlockData::class.java) + types.toList()).distinct().toTypedArray(),
    ) { _, _, _ -> null } as BlockData

    @Test
    fun `buttons and levers are separated by material`() {
        assertEquals(
            RedstoneTriggerCategory.BUTTON,
            EventTriggerClassification.redstoneCategory(data(Switch::class.java), "OAK_BUTTON"),
        )
        assertEquals(
            RedstoneTriggerCategory.LEVER,
            EventTriggerClassification.redstoneCategory(data(Switch::class.java), "LEVER"),
        )
    }

    @Test
    fun `plates are classified as pressure plate`() {
        // 加重板は AnaloguePowerable で判定します。
        assertEquals(
            RedstoneTriggerCategory.PRESSURE_PLATE,
            EventTriggerClassification.redstoneCategory(
                data(AnaloguePowerable::class.java),
                "HEAVY_WEIGHTED_PRESSURE_PLATE",
            ),
        )
        // 石・木の感圧板は Powerable＋素材名で判定します。
        assertEquals(
            RedstoneTriggerCategory.PRESSURE_PLATE,
            EventTriggerClassification.redstoneCategory(data(Powerable::class.java), "STONE_PRESSURE_PLATE"),
        )
    }

    @Test
    fun `unrelated powered blocks are ignored`() {
        // 感圧板でない Powerable（ドア等）は対象外です。
        assertNull(
            EventTriggerClassification.redstoneCategory(data(Powerable::class.java), "OAK_DOOR"),
        )
        assertNull(
            EventTriggerClassification.redstoneCategory(data(BlockData::class.java), "REDSTONE_WIRE"),
        )
    }

    @Test
    fun `edges map to the configured event kinds`() {
        assertEquals(
            EventTriggerKind.BUTTON_ON,
            EventTriggerClassification.eventFor(RedstoneTriggerCategory.BUTTON, on = true),
        )
        assertEquals(
            EventTriggerKind.BUTTON_OFF,
            EventTriggerClassification.eventFor(RedstoneTriggerCategory.BUTTON, on = false),
        )
        assertEquals(
            EventTriggerKind.LEVER_ON,
            EventTriggerClassification.eventFor(RedstoneTriggerCategory.LEVER, on = true),
        )
        assertEquals(
            EventTriggerKind.LEVER_OFF,
            EventTriggerClassification.eventFor(RedstoneTriggerCategory.LEVER, on = false),
        )
        assertEquals(
            EventTriggerKind.PRESSURE_PLATE_ON,
            EventTriggerClassification.eventFor(RedstoneTriggerCategory.PRESSURE_PLATE, on = true),
        )
        // 感圧板は離した時を提供しません。
        assertNull(EventTriggerClassification.eventFor(RedstoneTriggerCategory.PRESSURE_PLATE, on = false))
    }
}
