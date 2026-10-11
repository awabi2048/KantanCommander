package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphValidator
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
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
    ): CommandGraph = DslCompiler(worldTypes).compile(DslParser.parse(src)).graph

    private fun assertValid(result: DslCompileResult) {
        assertEquals(emptyList<String>(), GraphValidator.validate(result.graph), "構造検証")
        // イベント引数・defarg は起動方法・引数宣言から実行開始時の定義として
        // 導入されるため、検証へは宣言部込みの DiskScript を渡します。
        val script = DiskScript(
            name = "t",
            graph = result.graph,
            trigger = result.trigger,
            arguments = result.arguments,
        )
        val errors = ExecutableScriptValidator.validate(script)
        assertEquals(emptyList<String>(), errors.map { "${it.code}:${it.message}" }, "実行検証")
    }

    /**
     * 往復変換の検証です。DSL→モデル→DSL→モデル→DSL と変換し、
     * 1回目と2回目の印刷結果の一致（不動点）と、往路・復路グラフの
     * 意味構造の一致を確認します。両グラフはいずれも検証を通る必要があります。
     * `checkExecutable` を偽にすると実行検証を省略し、未設定（null）を含む
     * 「保存できるが実行できない」状態の往復を検証します。
     */
    private fun roundTrip(
        src: String,
        worldTypes: Map<String, ValueType> = emptyMap(),
        checkExecutable: Boolean = true,
    ): String {
        val r1 = DslCompiler(worldTypes).compile(DslParser.parse(src))
        val g1 = r1.graph
        assertEquals(emptyList<String>(), GraphValidator.validate(g1), "構造検証")
        if (checkExecutable) assertValid(r1)
        val text1 = DslPrinter().print(g1, "t", r1.trigger, r1.arguments)
        val r2 = DslCompiler(worldTypes).compile(DslParser.parse(text1))
        val g2 = r2.graph
        assertEquals(emptyList<String>(), GraphValidator.validate(g2), "構造検証")
        if (checkExecutable) assertValid(r2)
        val text2 = DslPrinter().print(g2, "t", r2.trigger, r2.arguments)
        assertEquals(text1, text2, "印刷テキストは不動点であるべきです")
        assertEquals(
            signatures(g1),
            signatures(g2),
            "往路・復路でノードの意味内容が一致すべきです",
        )
        return text1
    }

    /**
     * グラフの意味内容シグネチャです。各ノードの型・パラメータ・構造値を
     * 列挙し、数値文字列を正規化したうえで集合として比較します。
     * 正規形へ印刷すると `else-if` 展開で共有末端が複製される場合があるため、
     * 辺のトポロジや複数性ではなく「どの意味ノードが存在するか」を照合します。
     */
    private fun signatures(graph: CommandGraph): Set<String> =
        graph.nodes.values.map(::nodeSignature).toSet()

    private fun nodeSignature(n: CommandNode): String {
        val params = n.params.toSortedMap().entries
            .joinToString(",") { (k, v) -> "$k=${normalizeNumbers(v)}" }
        val specs = listOfNotNull(
            n.targetSpec, n.secondaryTargetSpec, n.destinationSpec, n.destinationTargetSpec,
            n.destinationFacingSpec, n.conditionPositionSpec, n.blockPositionSpec,
            n.blockFromSpec, n.blockToSpec, n.structurePlacePositionSpec,
            n.groupPivotPositionSpec, n.soundPositionSpec, n.particlePositionSpec,
            n.particleEndPositionSpec, n.fireworkPositionSpec, n.summonPositionSpec,
            n.executionPositionSpec, n.temporaryEntityTargetSpec,
            n.temporaryLocationPositionSpec, n.temporaryLocationFacingSpec,
        ).joinToString(",") { normalizeNumbers(it.toString()) }
        return "${n.type}|$params|$specs|${n.itemTempRef}|${n.blockTempRef}|${n.snapshot != null}|${n.foldedGraph != null}"
    }

    /** 数値トークンを `1.0`→`1` のように正規化します（保存形の揺れを吸収）。 */
    private fun normalizeNumbers(text: String): String =
        NUMBER_TOKEN.replace(text) { m ->
            m.value.toDoubleOrNull()?.let { d ->
                if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
            } ?: m.value
        }

    private companion object {
        val NUMBER_TOKEN = Regex("[+-]?\\d+\\.\\d+|[+-]?\\d+")
    }

    @Test
    fun `直列文が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                var x: number = 1;
                x += 2;
                wait(1);
            
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
            program "t" ;
                var x: number = 1;
                if (x > 2) {
                    x = 10;
                } else {
                    x = 0;
                }
                wait(1);
            
            """.trimIndent(),
        )
        assertTrue(text.contains("if (x > 2) {"))
        assertTrue(text.contains("} else {"))
    }

    @Test
    fun `else-if 連結が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                var x: number = 0;
                if (x < 1) {
                    x = 1;
                } else if (x < 5) {
                    x = 2;
                } else {
                    x = 3;
                }
            
            """.trimIndent(),
        )
        assertTrue(text.contains("} else if (x < 5) {"))
    }

    @Test
    fun `and-or の条件が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                var x: number = 1;
                var y: number = 2;
                if (x > 0 && y > 0 || x < -5) {
                    wait(1);
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
            program "t" ;
                var flag: boolean = true;
                if (!flag) {
                    wait(1);
                }
            
            """.trimIndent(),
        )
        assertTrue(text.contains("if (!flag) {"))
    }

    @Test
    fun `repeat-break-continue が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                var x: number = 0;
                repeat(5) {
                    if (x > 3) {
                        break;
                    }
                    x += 1;
                    continue;
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
            program "t" ;
                var x: number = 7;
                return x + 1;
            
            """.trimIndent(),
        )
        assertTrue(text.contains("return x + 1;"))
    }

    @Test
    fun `裸の return が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                wait(1);
                return;
            
            """.trimIndent(),
        )
        assertTrue(text.contains("return;"))
    }

    @Test
    fun `ワールド変数が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
            import world;
                var x: number = world.score + 1;
                world.score = x;
            
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
            program "t" ;
                var p: entity = target(kind = "nearest_player");
                if (p.has_item(item(id = "minecraft:diamond"))) {
                    wait(1);
                }
            
            """.trimIndent(),
        )
        assertTrue(text.contains("has_item"))
    }

    @Test
    fun `ブロック条件が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                var pos: location = location(x = 0, y = 64, z = 0);
                if (pos.block == block(id = "minecraft:stone")) {
                    wait(1);
                }
            
            """.trimIndent(),
        )
        assertTrue(text.contains(".block == block(id = \"minecraft:stone\")"))
    }

    @Test
    fun `位置リテラルが往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                var base: location = location(x = 100, y = 64, z = 0);
                teleport(target = target(kind = "nearest_player"), to = base ~0 ~1 ~0);
            
            """.trimIndent(),
        )
        assertTrue(text.contains("~0"))
    }

    @Test
    fun `文字列補間が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                var x: number = 5;
                display_text(text = "count is ${'$'}{x}", target = target(kind = "all_players"));
            
            """.trimIndent(),
        )
        assertTrue(text.contains("count is \${x}"))
    }

    @Test
    fun `命令の引数が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                give_item(target = target(kind = "nearest_player"), item = item(id = "minecraft:diamond"), count = 3);
                play_sound(sound = "minecraft:entity.experience_orb.pickup", at = ~0 ~1 ~0);
                summon(entity = "minecraft:zombie", at = ~0 ~0 ~0);
            
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
            program "t" ;
                var p: entity = target(kind = "nearest_player");
                if (p.gamemode == "CREATIVE") {
                    wait(1);
                }
            
            """.trimIndent(),
        )
        assertTrue(text.contains("\"CREATIVE\""))
    }

    @Test
    fun `終端のみのループ本体と後続文が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                repeat(3) {
                    break;
                }
                wait(1);
            
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
            program "t" ;
                var x: number = 0;
                if (x > 0) {
                    return;
                } else {
                    wait(1);
                }
                wait(2);
            
            """.trimIndent(),
        )
        assertTrue(text.contains("return;"))
        assertTrue(text.contains("wait(2);"))
    }

    @Test
    fun `ループ内で全経路が終端の分岐が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                var x: number = 0;
                repeat(3) {
                    if (x > 0) {
                        break;
                    } else {
                        continue;
                    }
                }
                wait(1);
            
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
            program "t" ;
                var i: number = 0;
                repeat(3) {
                    repeat(2) {
                        if (i > 0) {
                            i += 1;
                        }
                    }
                }
            
            """.trimIndent(),
        )
        assertTrue(text.contains("repeat(3) {"))
        assertTrue(text.contains("repeat(2) {"))
    }

    @Test
    fun `宣言部が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
            import world;
            trigger timer(interval = 20);
            defarg amount: number;
                world.score = amount;
            
            """.trimIndent(),
            worldTypes = mapOf("score" to ValueType.NUMBER),
        )
        assertTrue(text.contains("import world;"))
        assertTrue(text.contains("trigger timer(interval = 20);"))
        assertTrue(text.contains("defarg amount: number;"))
    }

    @Test
    fun `イベントトリガーとイベント引数が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
            trigger event(kind = "entity_click");
                var p: entity = event_actor;
            
            """.trimIndent(),
        )
        assertTrue(text.contains("trigger event(kind = \"entity_click\");"))
        assertTrue(text.contains("event_actor"))
    }

    @Test
    fun `コメントが往復する`() {
        val text = roundTrip(
            """
            program "t" ;
            // 先頭のコメント
            var x: number = 1;
            # 文の直前コメント
            wait(1); // 同行コメント
            if (x > 0) {
                // 内部コメント
                x = 2;
            }
            // 末尾コメント
            """.trimIndent(),
        )
        // `#` は正規形の `//` へ統一され、同行コメントは文の直前行へ揃います。
        listOf("先頭のコメント", "文の直前コメント", "同行コメント", "内部コメント", "末尾コメント")
            .forEach { assertTrue(text.contains("// $it"), "コメント '$it'") }
    }

    @Test
    fun `call の引数と戻り値受取が往復する`() {
        val target = CommandGraph()
        val wait = CommandType.WAIT.newNode()
        target.nodes[wait.id] = wait
        target.entryNodeId = wait.id
        val link = DslDiskLink(
            java.util.UUID.randomUUID(), target, listOf(
                me.awabi2048.kantancommander.model.DiskArgument("count", ValueType.NUMBER),
                me.awabi2048.kantancommander.model.DiskArgument("label", ValueType.STRING),
                me.awabi2048.kantancommander.model.DiskArgument("who", ValueType.ENTITY),
            ),
        )
        val src = """
            program "t" ;
                var who: entity = target(kind = "nearest_player");
                var result: number = 0;
                result = call "sub"(count = 3, label = "hi", who = who);

            """.trimIndent()
        val resolver: (String) -> DslDiskLink? = { name -> if (name == "sub") link else null }
        val printer = DslPrinter(diskNameResolver = { if (it == link.diskId) "sub" else it.toString() })
        val r1 = DslCompiler(emptyMap(), resolver).compile(DslParser.parse(src))
        assertValid(r1)
        val text1 = printer.print(r1.graph, "t", r1.trigger, r1.arguments)
        val r2 = DslCompiler(emptyMap(), resolver).compile(DslParser.parse(text1))
        assertValid(r2)
        val text2 = printer.print(r2.graph, "t", r2.trigger, r2.arguments)
        assertEquals(text1, text2, "印刷テキストは不動点であるべきです")
        assertTrue(text1.contains("""result = call "sub"(count = 3, label = "hi", who = who);"""))
        assertEquals(signatures(r1.graph), signatures(r2.graph), "往路・復路で意味内容が一致すべきです")
    }

    @Test
    fun `null 未設定が往復する`() {
        // 未設定を含むプログラムは「保存できるが実行できない」状態のため、
        // 構造検証のみで往復を確認します。
        val text = roundTrip(
            """
            program "t" ;
                if (null) { wait(1); }
                setblock(pos = null, block = block(id = "minecraft:stone"));
                var null: number = 0;
            
            """.trimIndent(),
            checkExecutable = false,
        )
        assertTrue(text.contains("if (null)"))
        assertTrue(text.contains("pos = null"))
        assertTrue(text.contains("var null: number"))
    }

    @Test
    fun `数値関数と累乗が往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                var a: number = 2;
                var b: number = pow(a, 3);
                var c: number = sin(a) + a * cos(b);
                var d: number = pow(a + 1, pow(2, a));
            
            """.trimIndent(),
        )
        assertTrue(text.contains("pow(a, 3)"), text)
        assertTrue(text.contains("sin(a)"), text)
        assertTrue(text.contains("cos(b)"), text)
        assertTrue(text.contains("pow(a + 1, pow(2, a))"), text)
    }

    @Test
    fun `複合式の一時変数展開で括弧が保持される`() {
        // `a * (b + c)` は `%{a}% * %{t}%`（t=`b + c`）へ低級化されます。
        // 参照箇所への単純な文字列展開は `a * b + c` と意味を変えるため、
        // 式木から優先度を見て括弧を補います。
        val text = roundTrip(
            """
            program "t" ;
                var a: number = 1;
                var b: number = 2;
                var c: number = 3;
                var d: number = a * (b + c);
                var e: number = a - (b - c);
            
            """.trimIndent(),
        )
        assertTrue(text.contains("a * (b + c)"), text)
        assertTrue(text.contains("a - (b - c)"), text)
    }

    @Test
    fun `エンティティ変数の読み取りが往復する`() {
        val text = roundTrip(
            """
            program "t" ;
                var e: entity = target(kind = "nearest_player");
                e.vars.dst = vars.src;
                e.vars.d = vars.src.health;
                e.vars.dist = vars.src.distance(vars.loc);
            
            """.trimIndent(),
        )
        assertTrue(text.contains("e.vars.dst = vars.src;"))
        assertTrue(text.contains("e.vars.d = vars.src.health;"))
        assertTrue(text.contains("e.vars.dist = vars.src.distance(vars.loc);"))
    }

    @Test
    fun `DSL で同名へ潰れる変数名の共存は拒否される`() {
        // モデルでは `a-b` と `a.b` を区別できますが、DSL ではいずれも `a_b` です。
        // 一方の変数へ沈黙のうち統合されるのを防ぐため、印刷時に衝突を検出します。
        fun define(name: String) = CommandNode(
            type = CommandType.VARIABLE,
            params = linkedMapOf(
                "scope" to "EXECUTION", "name" to name, "type" to "NUMBER",
                "operation" to "DEFINE", "changeMode" to "ASSIGN", "value" to "1",
            ),
        )
        val n1 = define("a-b")
        val n2 = define("a.b")
        n1.next = n2.id
        val graph = CommandGraph(
            entryNodeId = n1.id,
            nodes = linkedMapOf(n1.id to n1, n2.id to n2),
        )
        val ex = assertThrows<DslPrintException> {
            DslPrinter().print(graph, "t")
        }
        assertTrue(ex.message!!.contains("a_b"), "衝突した DSL 名を報告します")
    }
}
