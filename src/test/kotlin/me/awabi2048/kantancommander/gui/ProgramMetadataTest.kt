package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.item.KantanItemService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.util.UUID

/**
 * issue #13 の所有者・作成情報の表示を固定します。
 *
 * 一覧・lore・注視表示で同じ書式を使うため、境界の対応を検証します。
 * 日時はタイムゾーンを明示して検証し、表示ごとの解釈分岐を防ぎます。
 */
class ProgramMetadataTest {
    @Test
    fun `created at uses a unified format`() {
        assertEquals(
            "2026/09/15 20:00",
            ProgramMetadata.formatCreatedAt(1_789_470_000_000L, ZoneId.of("Asia/Tokyo")),
        )
    }

    @Test
    fun `owner names fall back to a short uuid`() {
        val owner = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        assertEquals("Alice", ProgramMetadata.ownerName(owner) { "Alice" })
        assertEquals("123e4567", ProgramMetadata.ownerName(owner) { null })
        assertEquals("123e4567", ProgramMetadata.ownerName(owner) { "" })
    }

    @Test
    fun `program lore lines keep label order`() {
        val lines = KantanItemService.programLoreLines(
            scriptName = "hello",
            nodeCount = 3,
            ownerName = "Alice",
            createdAt = "2026/09/15 20:00",
            programNameLabel = "Program name",
            commandsLabel = "Commands",
            ownerLabel = "Owner",
            createdAtLabel = "Created",
        )

        assertEquals(4, lines.size)
        assertTrue(lines[2].toString().contains("Alice"))
        assertTrue(lines[3].toString().contains("2026/09/15 20:00"))
    }
}
