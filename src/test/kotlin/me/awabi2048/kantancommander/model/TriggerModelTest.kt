package me.awabi2048.kantancommander.model

import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.ScriptTrigger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #7 の起動方法モデルのテストです。
 *
 * 起動方法の正本は `trigger` 欄です。既定値・明示指定の正規化、
 * イベント引数契約の名称・型を固定します。引数名はすべて実行開始時に
 * 定義できる変数名であり、実行層と検証層で同じ表を共有します。
 */
class TriggerModelTest {
    @Test
    fun `timer trigger keeps its interval`() {
        val script = DiskScript(name = "timer")
        script.trigger = ScriptTrigger(TriggerKind.TIMER, intervalSeconds = 5.0)
        val effective = script.effectiveTrigger()
        assertEquals(TriggerKind.TIMER, effective.kind)
        assertEquals(5.0, effective.intervalSeconds)
        assertNull(effective.event)
    }

    @Test
    fun `default trigger is redstone`() {
        val script = DiskScript(name = "redstone")
        val effective = script.effectiveTrigger()
        assertEquals(TriggerKind.REDSTONE, effective.kind)
        assertNull(effective.event)
    }

    @Test
    fun `event trigger keeps its kind`() {
        val script = DiskScript(name = "event")
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
            event = EventTriggerKind.LEVER_CLICK,
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
        assertEquals(EventTriggerKind.LEVER_CLICK, EventTriggerKind.parse("lever_click"))
        assertNull(EventTriggerKind.parse("lever_on"))
        assertNull(EventTriggerKind.parse("redstone"))
        // 廃止した種別は読み替えず、保存時の検証で破損として隔離します。
        assertNull(EventTriggerKind.parse("BUTTON_OFF"))
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
        // ボタンは押した時だけを提供します（戻った時は提供しません）。
        listOf(
            EventTriggerKind.BUTTON_ON,
            EventTriggerKind.LEVER_CLICK,
            EventTriggerKind.PRESSURE_PLATE_ON,
        ).forEach { event ->
            val table = EventTriggerArguments.seedTypesFor(event)
            assertEquals(ValueType.BLOCK, table[EventTriggerArguments.BLOCK])
            assertEquals(ValueType.LOCATION, table[EventTriggerArguments.POSITION])
            assertNull(table[EventTriggerArguments.ACTOR])
        }
    }

    @Test
    fun `clicks share kinds and retain event arguments`() {
        val entity = EventTriggerArguments.seedTypesFor(EventTriggerKind.ENTITY_CLICK)
        assertEquals(ValueType.ENTITY, entity[EventTriggerArguments.ACTOR])
        assertEquals(ValueType.ENTITY, entity[EventTriggerArguments.TARGET])

        val block = EventTriggerArguments.seedTypesFor(EventTriggerKind.BLOCK_CLICK)
        assertEquals(ValueType.ENTITY, block[EventTriggerArguments.ACTOR])
        assertEquals(ValueType.BLOCK, block[EventTriggerArguments.BLOCK])
        assertEquals(ValueType.LOCATION, block[EventTriggerArguments.POSITION])
    }

    @Test
    fun `forEvent normalizes to single candidate meaning on save`() {
        // 慣用的な探索語彙・探索条件は読み込み互換のため受け付けるが、
        // 保存時は単体代表へ寄せ、探索文脈を除去します。
        val idiomatic = EventTriggerConditions(
            actorTarget = TargetSpec(
                TargetKind.NEARBY_PLAYERS,
                name = "steve",
                maximumDistance = 5.0,
                limit = 3,
            ),
            clickSide = TriggerClickSide.LEFT,
        )
        val normalized = idiomatic.forEvent(EventTriggerKind.BLOCK_CLICK)
        assertEquals(TargetKind.NEAREST_PLAYER, normalized.actorTarget?.kind)
        assertEquals("steve", normalized.actorTarget?.name)
        assertNull(normalized.actorTarget?.maximumDistance)
        assertNull(normalized.actorTarget?.limit)
        assertEquals(TriggerClickSide.LEFT, normalized.clickSide)

        // 種別外の条件は保存時に除去されます。
        val crossKind = EventTriggerConditions(
            entityId = UUID.randomUUID(),
            leverState = TriggerLeverState.ON,
        ).forEvent(EventTriggerKind.BLOCK_CLICK)
        assertNull(crossKind.entityId)
        assertNull(crossKind.leverState)
    }

    @Test
    fun `validation accepts normalizable conditions but rejects invalid values`() {
        // 正規化で除去される差はエラーにせず、互換読み込みを妨げません。
        val idiomatic = EventTriggerConditions(
            actorTarget = TargetSpec(TargetKind.ALL_PLAYERS, tag = "crew"),
        )
        assertNull(idiomatic.validationProblem(EventTriggerKind.BLOCK_BREAK))
        assertEquals(
            TriggerNormalizationIssue.TARGET_KIND_NORMALIZED,
            idiomatic.normalizationWarning(EventTriggerKind.BLOCK_BREAK),
        )

        // 正規化後も残る値の不正だけをエラーにします。
        assertTrue(
            EventTriggerConditions(blockType = "NOT_A_BLOCK")
                .validationProblem(EventTriggerKind.BLOCK_CLICK) != null,
        )
        assertTrue(
            EventTriggerConditions(actorTarget = TargetSpec(TargetKind.TEMPORARY))
                .validationProblem(EventTriggerKind.BLOCK_BREAK) != null,
        )
        assertTrue(
            EventTriggerConditions(actorTarget = TargetSpec(TargetKind.NEAREST_ENTITY))
                .validationProblem(EventTriggerKind.BLOCK_BREAK) != null,
        )
        // 正規形は警告なしです。
        assertNull(
            EventTriggerConditions(actorTarget = TargetSpec(TargetKind.NEAREST_PLAYER, tag = "crew"))
                .normalizationWarning(EventTriggerKind.BLOCK_BREAK),
        )
    }

    @Test
    fun `individual identifiers count as configured filters`() {
        val conditions = EventTriggerConditions(
            actorId = UUID.randomUUID(),
            entityId = UUID.randomUUID(),
        )

        assertTrue(conditions.hasActorFilter())
        assertTrue(conditions.hasEntityFilter())
        assertTrue(!conditions.hasBlockFilter())
    }

    @Test
    fun `event kinds are exactly seven`() {

        assertEquals(7, EventTriggerKind.entries.size)
        // 設置と破壊は別種別、レバー状態とクリック方向は詳細条件で選びます。
        assertTrue(EventTriggerKind.entries.containsAll(
            listOf(
                EventTriggerKind.BLOCK_BREAK,
                EventTriggerKind.BLOCK_PLACE,
                EventTriggerKind.BUTTON_ON,
                EventTriggerKind.LEVER_CLICK,
                EventTriggerKind.PRESSURE_PLATE_ON,
                EventTriggerKind.ENTITY_CLICK,
                EventTriggerKind.BLOCK_CLICK,
            ),
        ))
    }
}
