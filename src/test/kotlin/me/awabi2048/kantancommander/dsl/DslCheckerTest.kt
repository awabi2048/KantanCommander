package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.ValueType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * DSL 意味検証（DslChecker）のテストです。
 *
 * 変数定義順・型整合・詳細値・組み込み呼出し・制御配置の規則を固定します。
 */
class DslCheckerTest {
    private fun check(text: String, worldTypes: Map<String, ValueType> = emptyMap()) {
        DslChecker(worldTypes).check(DslParser.parse(text))
    }

    private fun fail(text: String, worldTypes: Map<String, ValueType> = emptyMap()): DslCheckException =
        assertThrows(DslCheckException::class.java) { check(text, worldTypes) }

    @Test
    fun `variable must be declared before use`() {
        fail("""program "p" { x = 1; }""")
        fail("""program "p" { var y: number = x; }""")
        assertDoesNotThrow { check("""program "p" { var x: number = 1; x = 2; }""") }
    }

    @Test
    fun `variable redeclaration is rejected`() {
        fail("""program "p" { var x: number = 1; var x: number = 2; }""")
    }

    @Test
    fun `var declaration type must match initializer`() {
        fail("""program "p" { var x: number = "a"; }""")
        fail("""program "p" { var s: string = 1; }""")
        assertDoesNotThrow { check("""program "p" { var x: number = 1 + 2; }""") }
    }

    @Test
    fun `assignment type must match`() {
        fail("""program "p" { var x: number = 1; x = "a"; }""")
        assertDoesNotThrow { check("""program "p" { var x: number = 1; x = 2; }""") }
    }

    @Test
    fun `compound assignment requires number`() {
        fail("""program "p" { var s: string = "a"; s += "b"; }""")
        assertDoesNotThrow { check("""program "p" { var x: number = 1; x += 2; }""") }
    }

    @Test
    fun `if condition must be boolean`() {
        fail("""program "p" { var n: number = 1; if (n) { } }""")
        assertDoesNotThrow { check("""program "p" { var b: boolean = true; if (b) { } }""") }
    }

    @Test
    fun `break and continue require enclosing repeat`() {
        fail("""program "p" { break; }""")
        fail("""program "p" { continue; }""")
        assertDoesNotThrow {
            check("""program "p" { repeat(3) { if (true) { break; } continue; } }""")
        }
    }

    @Test
    fun `repeat count rules`() {
        fail("""program "p" { repeat("x") { } }""")
        fail("""program "p" { repeat(0) { } }""")
        fail("""program "p" { repeat(2.5) { } }""")
        assertDoesNotThrow { check("""program "p" { var n: number = 3; repeat(n) { } }""") }
    }

    @Test
    fun `binary operator type rules`() {
        fail("""program "p" { var x: number = 1 + "a"; }""")
        fail("""program "p" { var b: boolean = true && 1; }""")
        fail("""program "p" { var b: boolean = 1 == "a"; }""")
        fail("""program "p" { var b: boolean = 1 < true; }""")
        assertDoesNotThrow {
            check("""program "p" { var a: boolean = 1 + 2 * 3 == 7; var b: boolean = !a; }""")
        }
    }

    @Test
    fun `unary operator type rules`() {
        fail("""program "p" { var x: number = -"a"; }""")
        fail("""program "p" { var b: boolean = !1; }""")
    }

    @Test
    fun `property reads follow the registry`() {
        fail("""program "p" { var n: number = 1; var g: string = n.gamemode; }""")
        fail("""program "p" { var e: entity = target(kind = "nearest_player"); var u: number = e.unknown; }""")
        fail("""program "p" { var e: entity = target(kind = "nearest_player"); var d: number = e.distance; }""")
        fail("""program "p" { var e: entity = target(kind = "nearest_player"); var d: number = e.distance(1); }""")
        fail("""program "p" { var l: location = ~0 ~0 ~0; var a: number = l.amount; }""")
        fail("""program "p" { var e: entity = target(kind = "nearest_player"); var n: string = e.name(1); }""")
        assertDoesNotThrow {
            check(
                """program "p" {
                    var e: entity = target(kind = "nearest_player");
                    var l: location = ~0 ~0 ~0;
                    var g: string = e.gamemode;
                    var d: number = e.distance(l);
                    var b: block = l.block;
                    var p: number = l.redstone_power;
                }""",
            )
        }
    }

    @Test
    fun `property arg type must match`() {
        fail(
            """program "p" {
                var e: entity = target(kind = "nearest_player");
                var h: boolean = e.has_item(1);
            }""",
        )
        assertDoesNotThrow {
            check(
                """program "p" {
                    var e: entity = target(kind = "nearest_player");
                    var it: item = item(id = "minecraft:stone");
                    var h: boolean = e.has_item(it);
                }""",
            )
        }
    }

    @Test
    fun `constructors require named args and required params`() {
        fail("""program "p" { var l: location = location(100, 64, 0); }""")
        fail("""program "p" { var l: location = location(x = 1, y = 2); }""")
        fail("""program "p" { var l: location = location(x = 1, y = 2, z = "a"); }""")
        fail("""program "p" { var l: location = location(x = 1, y = 2, z = 3, foo = 1); }""")
        fail("""program "p" { var i: item = item("minecraft:stone"); }""")
        // モデルが item の amount を値として保持できないため、amount は拒否します。
        fail("""program "p" { var i: item = item(id = "minecraft:stone", amount = 2); }""")
        assertDoesNotThrow {
            check(
                """program "p" {
                    var l: location = location(x = 1, y = 2, z = 3, yaw = 0);
                    var i: item = item(id = "minecraft:stone");
                    var b: block = block(id = "minecraft:dirt");
                }""",
            )
        }
    }

    @Test
    fun `target query validation`() {
        fail("""program "p" { var e: entity = target(); }""")
        fail("""program "p" { var e: entity = target(kind = "bogus"); }""")
        fail("""program "p" { var e: entity = target(kind = "nearest_player", within = "x"); }""")
        fail("""program "p" { var e: entity = target(kind = "nearest_player", anchor = 1); }""")
        assertDoesNotThrow {
            check(
                """program "p" {
                    var e: entity = target(kind = "nearest_player", within = 8.0);
                    var m: entity = target(kind = "nearby_entities", type = "minecraft:zombie", limit = 3, sort = "random");
                }""",
            )
        }
    }

    @Test
    fun `position literal base must be location`() {
        fail("""program "p" { var n: number = 1; var v: location = n ~0 ~0 ~0; }""")
        fail("""program "p" { var s: string = "a"; var v: location = s ~0 ~0 ~0; }""")
        assertDoesNotThrow {
            check("""program "p" { var l: location = ~0 ~0 ~0; var v: location = l ~0 ~1 ~0; }""")
        }
    }

    @Test
    fun `world variables use the provided type table`() {
        fail("""program "p" { world.money = 1; }""")
        fail("""program "p" { var n: number = world.money; }""")
        fail("""program "p" { world.money += 1; }""", mapOf("money" to ValueType.STRING))
        assertDoesNotThrow {
            check("""program "p" { world.money = 1; world.money += 2; var m: number = world.money; }""",
                mapOf("money" to ValueType.NUMBER))
        }
    }

    @Test
    fun `string interpolation restricts expression types`() {
        fail(
            """program "p" {
                var e: entity = target(kind = "nearest_player");
                var s: string = "${'$'}{e}";
            }""",
        )
        assertDoesNotThrow {
            check("""program "p" { var n: number = 1; var s: string = "${'$'}{n + 1}"; }""")
        }
    }

    @Test
    fun `gamemode constant compares as string`() {
        assertDoesNotThrow {
            check(
                """program "p" {
                    var e: entity = target(kind = "nearest_player");
                    var g: boolean = e.gamemode == gamemode.creative;
                }""",
            )
        }
    }

    @Test
    fun `wait validation`() {
        fail("""program "p" { wait(); }""")
        fail("""program "p" { wait("a"); }""")
        fail("""program "p" { wait(foo = 1); }""")
        fail("""program "p" { wait(0.02); }""")
        fail("""program "p" { wait(1, 2); }""")
        assertDoesNotThrow { check("""program "p" { wait(1); wait(0.05); wait(seconds = 2); }""") }
    }

    @Test
    fun `error message carries position`() {
        val e = fail("program \"p\" {\n    x = 1;\n}")
        assertTrue(e.message!!.startsWith("2:"), "unexpected position: ${e.message}")
    }

    @Test
    fun `call disk statement`() {
        assertDoesNotThrow { check("""program "p" { call "disk_a"; }""") }
    }
}
