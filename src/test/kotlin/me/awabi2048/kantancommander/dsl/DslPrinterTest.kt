package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphValidator
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ValueType
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * モデル→DSLプリンタの検証です。
 *
 * 各ケースで DSL テキスト → モデル → DSL テキスト の往復を行い、
 * 生成グラフの構造・実行可能性の検証と、印刷テキストの不動点
 * （canonical 形へ再印刷しても変わらないこと）を確認します。
 */
class DslPrinterTest {
    private fun compile(
        src: String,
        worldTypes: Map<String, ValueType> = emptyMap(),
    ): CommandGraph = DslCompiler(worldTypes).compile(DslParser.parse(src))

    private fun assertValid(graph: CommandGraph) {
        assertEquals(emptyList<String>(), GraphValidator.validate(graph), "構造検証")
        val script = DiskScript(name = "t", owner = UUID.randomUUID(), graph = graph)
        val errors = ExecutableScriptValidator.validate(script)
        assertEquals(emptyList<String>(), errors.map { "${it.code}:${it.message}" }, "実行検証")
    }

    /**
     * 往復変換の不動点検証です。DSL→モデル→DSL→モデル→DSL と変換し、
     * 1回目と2回目の印刷結果が一致することを確認します。
     * 往路・復路のグラフはいずれも検証を通る必要があります。
     */
    private fun roundTrip(src: String, worldTypes: Map<String, ValueType> = emptyMap()): String {
        val g1 = compile(src, worldTypes)
        assertValid(g1)
        val text1 = DslPrinter().print(g1, "t")
        val g2 = compile(text1, worldTypes)
        assertValid(g2)
        val text2 = DslPrinter().print(g2, "t")
        assertEquals(text1, text2, "印刷テキストは不動点であるべきです")
        return text1
    }

    @Test
    fun `直列文が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var x: number = 1;
                x += 2;
                wait(1);
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("var x: number = 1;"))
        assertTrue(text.contains("x += 2;"))
        assertTrue(text.contains("wait(1);"))
    }

    @Test
    fun `if-else が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var x: number = 1;
                if (x > 2) {
                    x = 10;
                } else {
                    x = 0;
                }
                wait(1);
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("if (x > 2) {"))
        assertTrue(text.contains("} else {"))
    }

    @Test
    fun `else-if 連結が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var x: number = 0;
                if (x < 1) {
                    x = 1;
                } else if (x < 5) {
                    x = 2;
                } else {
                    x = 3;
                }
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("} else if (x < 5) {"))
    }

    @Test
    fun `and-or の条件が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var x: number = 1;
                var y: number = 2;
                if (x > 0 && y > 0 || x < -5) {
                    wait(1);
                }
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("if ("))
        assertTrue(text.contains("&&") || text.contains("||"))
    }

    @Test
    fun `否定条件が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var flag: boolean = true;
                if (!flag) {
                    wait(1);
                }
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("if (!flag) {"))
    }

    @Test
    fun `repeat-break-continue が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var x: number = 0;
                repeat(5) {
                    if (x > 3) {
                        break;
                    }
                    x += 1;
                    continue;
                }
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("repeat(5) {"))
        assertTrue(text.contains("break;"))
        assertTrue(text.contains("continue;"))
    }

    @Test
    fun `return が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var x: number = 7;
                return x + 1;
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("return x + 1;"))
    }

    @Test
    fun `裸の return が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                wait(1);
                return;
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("return;"))
    }

    @Test
    fun `ワールド変数が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var x: number = world.score + 1;
                world.score = x;
            }
            """.trimIndent(),
            worldTypes = mapOf("score" to ValueType.NUMBER),
        )
        assertTrue(text.contains("world.score + 1"))
        assertTrue(text.contains("world.score = x;"))
    }

    @Test
    fun `詳細値の条件が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var p: entity = target(kind = "nearest_player");
                if (p.has_item(item(id = "minecraft:diamond"))) {
                    wait(1);
                }
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("has_item"))
    }

    @Test
    fun `ブロック条件が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var pos: location = location(x = 0, y = 64, z = 0);
                if (pos.block == block(id = "minecraft:stone")) {
                    wait(1);
                }
            }
            """.trimIndent(),
        )
        assertTrue(text.contains(".block == block(id = \"minecraft:stone\")"))
    }

    @Test
    fun `位置リテラルが往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var base: location = location(x = 100, y = 64, z = 0);
                teleport(target = target(kind = "nearest_player"), to = base ~0 ~1 ~0);
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("~0"))
    }

    @Test
    fun `文字列補間が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var x: number = 5;
                display_text(text = "count is ${'$'}{x}", target = target(kind = "all_players"));
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("count is \${x}"))
    }

    @Test
    fun `命令の引数が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                give_item(target = target(kind = "nearest_player"), item = item(id = "minecraft:diamond"), count = 3);
                play_sound(sound = "minecraft:entity.experience_orb.pickup", at = ~0 ~1 ~0);
                summon(entity = "minecraft:zombie", at = ~0 ~0 ~0);
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("give_item("))
        assertTrue(text.contains("play_sound("))
        assertTrue(text.contains("summon("))
    }

    @Test
    fun `gamemode 定数の比較が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var p: entity = target(kind = "nearest_player");
                if (p.gamemode == "CREATIVE") {
                    wait(1);
                }
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("\"CREATIVE\""))
    }

    @Test
    fun `終端のみのループ本体と後続文が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                repeat(3) {
                    break;
                }
                wait(1);
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("repeat(3) {"))
        assertTrue(text.contains("break;"))
        assertTrue(text.contains("wait(1);"))
    }

    @Test
    fun `if 内の return と else が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var x: number = 0;
                if (x > 0) {
                    return;
                } else {
                    wait(1);
                }
                wait(2);
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("return;"))
        assertTrue(text.contains("wait(2);"))
    }

    @Test
    fun `ループ内で全経路が終端の分岐が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var x: number = 0;
                repeat(3) {
                    if (x > 0) {
                        break;
                    } else {
                        continue;
                    }
                }
                wait(1);
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("break;"))
        assertTrue(text.contains("continue;"))
        assertTrue(text.contains("wait(1);"))
    }

    @Test
    fun `ネストした repeat と if が往復する`() {
        val text = roundTrip(
            """
            program "t" {
                var i: number = 0;
                repeat(3) {
                    repeat(2) {
                        if (i > 0) {
                            i += 1;
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        assertTrue(text.contains("repeat(3) {"))
        assertTrue(text.contains("repeat(2) {"))
    }
}
