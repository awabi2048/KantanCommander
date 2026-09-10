package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #31 の複数クエリ候補検索の回帰テストです。
 *
 * Registry取得を含まない純粋処理（クエリ解析・単一ランキング・統合）を直接検証するため、
 * サーバーなしの単体環境で実行できます。Registry由来の実ID取得は [CommandDialogSpecs.registeredIds] の
 * 契約（部分集合・空欄空列）に委ね、ここでは件数上限だけを確認します。
 */
class SuggestionQueriesTest {
    private val values = listOf(
        "minecraft:smoke",
        "minecraft:big_flame",
        "minecraft:flame_extra",
        "minecraft:flame",
    )

    @Test
    fun `single query keeps exact prefix substring order`() {
        val ranked = CommandDialogSpecs.rankCandidates(values, "minecraft:flame")
        assertEquals(
            listOf("minecraft:flame", "minecraft:flame_extra", "minecraft:big_flame", "minecraft:smoke"),
            ranked,
        )
    }

    @Test
    fun `single query keeps prefix before substring`() {
        val ranked = CommandDialogSpecs.rankCandidates(values, "minecraft:flam")
        assertEquals(
            listOf("minecraft:flame", "minecraft:flame_extra", "minecraft:big_flame", "minecraft:smoke"),
            ranked,
        )
    }

    @Test
    fun `whitespace splits input into queries`() {
        assertEquals(
            listOf("flame", "smoke", "bubble"),
            CommandDialogSpecs.parseSuggestionQueries("flame smoke bubble"),
        )
    }

    @Test
    fun `comma splits input into queries`() {
        assertEquals(
            listOf("flame", "smoke", "bubble"),
            CommandDialogSpecs.parseSuggestionQueries("flame, smoke, bubble"),
        )
    }

    @Test
    fun `ideographic comma splits input into queries`() {
        assertEquals(
            listOf("flame", "smoke", "bubble"),
            CommandDialogSpecs.parseSuggestionQueries("flame、smoke、bubble"),
        )
    }

    @Test
    fun `empty fragments are ignored`() {
        assertEquals(
            listOf("flame", "smoke", "bubble"),
            CommandDialogSpecs.parseSuggestionQueries("  flame,,  smoke  、、 bubble  "),
        )
        assertTrue(CommandDialogSpecs.parseSuggestionQueries("   ").isEmpty())
        assertTrue(CommandDialogSpecs.parseSuggestionQueries(",,、  ,").isEmpty())
    }

    @Test
    fun `duplicate queries are removed case insensitively`() {
        assertEquals(listOf("flame"), CommandDialogSpecs.parseSuggestionQueries("flame flame FLAME"))
        assertEquals(
            listOf("flame", "smoke"),
            CommandDialogSpecs.parseSuggestionQueries("flame, smoke, flame"),
        )
    }

    @Test
    fun `query count has an upper bound`() {
        val input = (1..20).joinToString(" ") { "query$it" }
        val parsed = CommandDialogSpecs.parseSuggestionQueries(input)
        assertEquals(CommandDialogSpecs.MAX_SUGGESTION_QUERIES, parsed.size)
        assertEquals((1..8).map { "query$it" }, parsed)
    }

    @Test
    fun `round robin merges queries without bias to the first query`() {
        val merged = CommandDialogSpecs.mergeSuggestionRounds(
            listOf(
                listOf("A1", "A2", "A3", "A4"),
                listOf("B1", "B2", "B3"),
                listOf("C1", "C2"),
            ),
            12,
        )
        assertEquals(listOf("A1", "B1", "C1", "A2", "B2", "C2", "A3", "B3", "A4"), merged)
    }

    @Test
    fun `duplicate ids appear only once`() {
        val merged = CommandDialogSpecs.mergeSuggestionRounds(
            listOf(
                listOf("minecraft:flame", "minecraft:flame_extra"),
                listOf("minecraft:flame", "minecraft:smoke"),
            ),
            12,
        )
        assertEquals(listOf("minecraft:flame", "minecraft:smoke", "minecraft:flame_extra"), merged)
    }

    @Test
    fun `merged candidates never exceed twelve`() {
        val ranked = listOf(
            (1..20).map { "minecraft:a$it" },
            (1..20).map { "minecraft:b$it" },
        )
        assertEquals(12, CommandDialogSpecs.mergeSuggestionRounds(ranked, 12).size)
        assertTrue(CommandDialogSpecs.mergeSuggestionRounds(ranked, 0).isEmpty())
    }

    @Test
    fun `selected candidate is a single id replacing the whole input`() {
        // 候補選択は複数クエリの結合ではなく単一IDへの置換です。
        // Dialogの開き直し自体はGUI対話のため、ここでは選択結果が単一IDである契約を固定します。
        // 実画面の置換動作は sound Dialog と同一方式であることをレビューで確認します。
        val merged = CommandDialogSpecs.mergeSuggestionRounds(
            listOf(
                listOf("minecraft:flame", "minecraft:flame_extra"),
                listOf("minecraft:smoke"),
            ),
            12,
        )
        val selected = merged.first()
        assertEquals("minecraft:flame", selected)
        assertTrue(' ' !in selected && ',' !in selected && '、' !in selected)
    }

    @Test
    fun `multi query search stays within the shared registry boundary`() {
        // サーバーなしではRegistryが使えないため空列ですが、契約は維持します。
        listOf("entity", "sound", "effect").forEach { fieldKey ->
            assertTrue(CommandDialogSpecs.suggestions(fieldKey, listOf("flame", "smoke")).size <= 12, fieldKey)
            assertTrue(CommandDialogSpecs.suggestionsFromInput(fieldKey, "flame smoke").size <= 12, fieldKey)
            assertTrue(CommandDialogSpecs.suggestionsFromInput(fieldKey, "   ").isEmpty(), fieldKey)
        }
        assertTrue(CommandDialogSpecs.suggestions("text", "flame").isEmpty())
        assertTrue(CommandDialogSpecs.suggestionsFromInput("text", "flame smoke").isEmpty())
    }

    @Test
    fun `single query overload stays compatible with multi query search`() {
        // 区切り文字を含まない単一クエリは、従来と同じ1クエリ検索になります。
        listOf("sound" to "note", "effect" to "speed", "entity" to "cow").forEach { (fieldKey, query) ->
            assertEquals(
                CommandDialogSpecs.suggestions(fieldKey, listOf(query)),
                CommandDialogSpecs.suggestions(fieldKey, query),
                "$fieldKey:$query",
            )
        }
    }
}
