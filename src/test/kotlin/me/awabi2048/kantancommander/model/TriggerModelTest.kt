package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #7 の起動方法モデルのテストです。
 *
 * 旧保存の読み替え（タイマー有効→TIMER、無効→REDSTONE）、明示指定の正規化、
 * イベント引数契約の名称・型を固定します。引数名はすべて実行開始時に
 * 定義できる変数名であり、実行層と検証層で同じ表を共有します。
 */
class TriggerModelTest {
    @Test
    fun `legacy timer maps to timer trigger`() {
        val script = DiskScript(name = "legacy-timer", owner = UUID.randomUUID())
        script.timer.enabled = true
        script.timer.intervalSeconds = 5.0
        val effective = script.effectiveTrigger()
        assertEquals(TriggerKind.TIMER, effective.kind)
        assertEquals(5.0, effective.intervalSeconds)
        assertNull(effective.event)
    }

    @Test
    fun `legacy redstone maps to redstone trigger`() {
        val script = DiskScript(name = "legacy-redstone", owner = UUID.randomUUID())
        script.timer.enabled = false
        script.activation = ActivationMode.NEEDS_REDSTONE
        val effective = script.effectiveTrigger()
        assertEquals(TriggerKind.REDSTONE, effective.kind)
        assertNull(effective.event)
    }

    @Test
    fun `explicit trigger wins over legacy fields`() {
        val script = DiskScript(name = "explicit", owner = UUID.randomUUID())
        script.timer.enabled = true
        script.timer.intervalSeconds = 5.0
        script.trigger = ScriptTrigger(TriggerKind.EVENT, EventTriggerKind.BLOCK_BREAK)
        val effective = script.effectiveTrigger()
        assertEquals(TriggerKind.EVENT, effective.kind)
        assertEquals(EventTriggerKind.BLOCK_BREAK, effective.event)
    }

    @Test
    fun `normalization drops stale settings`() {
        val timer = ScriptTrigger(
            TriggerKind.TIMER,
            event = EventTriggerKind.BLOCK_BREAK,
            intervalSeconds = -1.0,
        ).normalized()
        assertNull(timer.event)
        assertEquals(DEFAULT_TIMER_SECONDS, timer.intervalSeconds)

        val redstone = ScriptTrigger(
            TriggerKind.REDSTONE,
            event = EventTriggerKind.SWITCH,
            intervalSeconds = 10.0,
        ).normalized()
        assertNull(redstone.event)
        assertEquals(DEFAULT_TIMER_SECONDS, redstone.intervalSeconds)

        // EVENT の種別欠落は検証層で拒否するため、ここでは補完しません。
        val event = ScriptTrigger(TriggerKind.EVENT, event = null).normalized()
        assertNull(event.event)
    }

    @Test
    fun `trigger kinds parse leniently`() {
        assertEquals(TriggerKind.TIMER, TriggerKind.parse("timer"))
        assertEquals(TriggerKind.EVENT, TriggerKind.parse(" event "))
        assertNull(TriggerKind.parse("fancy"))
        assertNull(TriggerKind.parse(null))
        assertEquals(EventTriggerKind.SWITCH, EventTriggerKind.parse("switch"))
        assertNull(EventTriggerKind.parse("redstone"))
    }

    @Test
    fun `event arguments are valid variable names`() {
        EventTriggerKind.entries.forEach { event ->
            val arguments = EventTriggerArguments.argumentsFor(event)
            // 空の引数表は作りません。呼び出し側の受取検査と実行層の生成がずれます。
            assertTrue(arguments.isNotEmpty())
            arguments.forEach { argument ->
                assertTrue(
                    CommandValueRules.isVariableName(argument.name),
                    "${event.name} の引数名が不正です: ${argument.name}",
                )
            }
            // 検証層の定義表と実行層の生成は同じ表から導出します。
            assertEquals(
                arguments.associate { it.name to it.type },
                EventTriggerArguments.seedTypesFor(event),
            )
        }
    }

    @Test
    fun `block events carry actor block and position`() {
        val names = EventTriggerArguments.argumentsFor(EventTriggerKind.BLOCK_BREAK).map { it.name }
        assertEquals(
            listOf(EventTriggerArguments.ACTOR, EventTriggerArguments.BLOCK, EventTriggerArguments.POSITION),
            names,
        )
        assertEquals(names, EventTriggerArguments.argumentsFor(EventTriggerKind.BLOCK_PLACE).map { it.name })
    }

    @Test
    fun `switch and plate carry state without actor`() {
        // 信号変化の観測であり操作者を特定できないため、操作者を持ちません。
        EventTriggerKind.entries
            .filter { it == EventTriggerKind.SWITCH || it == EventTriggerKind.PRESSURE_PLATE }
            .forEach { event ->
                val table = EventTriggerArguments.seedTypesFor(event)
                assertEquals(ValueType.BLOCK, table[EventTriggerArguments.BLOCK])
                assertEquals(ValueType.LOCATION, table[EventTriggerArguments.POSITION])
                assertEquals(ValueType.STRING, table[EventTriggerArguments.STATE])
                assertNull(table[EventTriggerArguments.ACTOR])
            }
    }

    @Test
    fun `clicks carry left-right action`() {
        val entity = EventTriggerArguments.seedTypesFor(EventTriggerKind.ENTITY_CLICK)
        assertEquals(ValueType.ENTITY, entity[EventTriggerArguments.ACTOR])
        assertEquals(ValueType.ENTITY, entity[EventTriggerArguments.TARGET])
        assertEquals(ValueType.STRING, entity[EventTriggerArguments.ACTION])

        val block = EventTriggerArguments.seedTypesFor(EventTriggerKind.BLOCK_CLICK)
        assertEquals(ValueType.ENTITY, block[EventTriggerArguments.ACTOR])
        assertEquals(ValueType.BLOCK, block[EventTriggerArguments.BLOCK])
        assertEquals(ValueType.LOCATION, block[EventTriggerArguments.POSITION])
        assertEquals(ValueType.STRING, block[EventTriggerArguments.ACTION])
    }
}
