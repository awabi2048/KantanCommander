package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * issue #3 と #10 のEntity property定義の対応テストです。
 *
 * 読み出し側（[ValueProperties]）と書き込み側（[EntityDataSchema]）で、
 * 同じ概念に別IDを無言で増殖させないことを固定します。完全な記述子共通化は
 * #10 の作業であり、ここでは現行の対応関係を凍結して #10 の起点にします。
 * #10 で共通化を進める場合は、このテストの許可対応を意図的に更新します。
 */
class EntityPropertyAlignmentTest {
    @Test
    fun `read and write ids do not collide silently`() {
        val readIds = ValueProperties.ALL.map { it.id }.toSet()
        val overlap = readIds intersect EntityDataSchema.ids()
        // 現行の対応は空集合です。読み出し `name` と書き込み `custom_name` のように
        // 同概念は意図的に別IDで維持し、無言の衝突も無言の統合も許しません。
        assertEquals(emptySet<String>(), overlap)
    }

    @Test
    fun `overlapping concepts keep their documented stable ids`() {
        // 読み出し側の name（STRING）と書き込み側の custom_name は同概念ですが、
        // ID・結果型・責務を変えずに維持します。#10 の共通化時にここを起点に見直します。
        assertEquals(ValueType.STRING, ValueProperties.definition("name")?.result)
        assertNotNull(EntityDataSchema.definition(EntityDataSchema.PROPERTY_CUSTOM_NAME))
        // #3 が #10 へ渡す読み出し側の基準（ID・取得元・結果型）を固定します。
        assertEquals(
            mapOf(
                "name" to ValueType.STRING,
                "type" to ValueType.STRING,
                "health" to ValueType.NUMBER,
                "location" to ValueType.LOCATION,
                "gamemode" to ValueType.STRING,
                "distance" to ValueType.NUMBER,
                "has_item" to ValueType.BOOLEAN,
                "x" to ValueType.NUMBER,
                "y" to ValueType.NUMBER,
                "z" to ValueType.NUMBER,
                "yaw" to ValueType.NUMBER,
                "pitch" to ValueType.NUMBER,
                "block" to ValueType.BLOCK,
                "redstone_power" to ValueType.NUMBER,
                "amount" to ValueType.NUMBER,
            ),
            ValueProperties.ALL.associate { it.id to it.result },
        )
    }
}
