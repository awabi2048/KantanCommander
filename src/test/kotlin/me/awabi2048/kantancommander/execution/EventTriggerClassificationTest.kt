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
 * ボタン・レバーは Switch 型、感圧板は加重板または感圧板素材で判定し、
 * ドア等の無関係な信号変化を対象外とします。サーバーなしで実行できます。
 */
class EventTriggerClassificationTest {
    private fun data(vararg types: Class<*>): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        (listOf(BlockData::class.java) + types.toList()).distinct().toTypedArray(),
    ) { _, _, _ -> null } as BlockData

    @Test
    fun `switch is classified as switch`() {
        assertEquals(
            EventTriggerKind.SWITCH,
            EventTriggerClassification.switchOrPlate(data(Switch::class.java), "OAK_BUTTON"),
        )
        assertEquals(
            EventTriggerKind.SWITCH,
            EventTriggerClassification.switchOrPlate(data(Switch::class.java), "LEVER"),
        )
    }

    @Test
    fun `plates are classified as pressure plate`() {
        // 加重板は AnaloguePowerable で判定します。
        assertEquals(
            EventTriggerKind.PRESSURE_PLATE,
            EventTriggerClassification.switchOrPlate(
                data(AnaloguePowerable::class.java),
                "HEAVY_WEIGHTED_PRESSURE_PLATE",
            ),
        )
        // 石・木の感圧板は Powerable＋素材名で判定します。
        assertEquals(
            EventTriggerKind.PRESSURE_PLATE,
            EventTriggerClassification.switchOrPlate(data(Powerable::class.java), "STONE_PRESSURE_PLATE"),
        )
    }

    @Test
    fun `unrelated powered blocks are ignored`() {
        // 感圧板でない Powerable（ドア等）は対象外です。
        assertNull(
            EventTriggerClassification.switchOrPlate(data(Powerable::class.java), "OAK_DOOR"),
        )
        assertNull(
            EventTriggerClassification.switchOrPlate(data(BlockData::class.java), "REDSTONE_WIRE"),
        )
    }
}
