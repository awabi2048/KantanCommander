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
            event = EventTriggerKind.LEVER_ON,
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
        assertEquals(EventTriggerKind.LEVER_ON, EventTriggerKind.parse("lever_on"))
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
    fun `switch events carry block and position without actor`() {
        // 信号変化の観測であり操作者を特定できないため、操作者を持ちません。
        listOf(
            EventTriggerKind.BUTTON_ON,
            EventTriggerKind.BUTTON_OFF,
            EventTriggerKind.LEVER_ON,
            EventTriggerKind.LEVER_OFF,
            EventTriggerKind.PRESSURE_PLATE_ON,
        ).forEach { event ->
            val table = EventTriggerArguments.seedTypesFor(event)
            assertEquals(ValueType.BLOCK, table[EventTriggerArguments.BLOCK])
            assertEquals(ValueType.LOCATION, table[EventTriggerArguments.POSITION])
            assertNull(table[EventTriggerArguments.ACTOR])
        }
    }

    @Test
    fun `clicks are separated by left and right kinds`() {
        // 左右はイベント種別そのもので選び分けます。
        val left = EventTriggerArguments.seedTypesFor(EventTriggerKind.ENTITY_LEFT_CLICK)
        assertEquals(ValueType.ENTITY, left[EventTriggerArguments.ACTOR])
        assertEquals(ValueType.ENTITY, left[EventTriggerArguments.TARGET])

        val right = EventTriggerArguments.seedTypesFor(EventTriggerKind.ENTITY_RIGHT_CLICK)
        assertEquals(ValueType.ENTITY, right[EventTriggerArguments.TARGET])

        val blockLeft = EventTriggerArguments.seedTypesFor(EventTriggerKind.BLOCK_LEFT_CLICK)
        assertEquals(ValueType.ENTITY, blockLeft[EventTriggerArguments.ACTOR])
        assertEquals(ValueType.BLOCK, blockLeft[EventTriggerArguments.BLOCK])
        assertEquals(ValueType.LOCATION, blockLeft[EventTriggerArguments.POSITION])

        val blockRight = EventTriggerArguments.seedTypesFor(EventTriggerKind.BLOCK_RIGHT_CLICK)
        assertEquals(ValueType.BLOCK, blockRight[EventTriggerArguments.BLOCK])
    }

    @Test
    fun `initial event kinds are exactly eleven`() {
        assertEquals(11, EventTriggerKind.entries.size)
        // ボタンとレバーを区別し、オン／オフと左右を別種別で持つことを固定します。
        assertTrue(EventTriggerKind.entries.containsAll(
            listOf(
                EventTriggerKind.BUTTON_ON,
                EventTriggerKind.BUTTON_OFF,
                EventTriggerKind.LEVER_ON,
                EventTriggerKind.LEVER_OFF,
                EventTriggerKind.ENTITY_LEFT_CLICK,
                EventTriggerKind.ENTITY_RIGHT_CLICK,
                EventTriggerKind.BLOCK_LEFT_CLICK,
                EventTriggerKind.BLOCK_RIGHT_CLICK,
            ),
        ))
    }
}
