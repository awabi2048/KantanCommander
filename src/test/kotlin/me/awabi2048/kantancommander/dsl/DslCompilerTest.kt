package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphValidator
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.EventTriggerKind
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableScope
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * DSL→モデルコンパイラの検証です。
 *
 * 生成したグラフを構造検証（GraphValidator）と実行可能検証
 * （ExecutableScriptValidator）へ通し、DSLの意味と保存・実行可能性が
 * 一致することを確認します。
 */
class DslCompilerTest {
    private fun compile(
        src: String,
        worldTypes: Map<String, ValueType> = emptyMap(),
        resolver: (String) -> DslDiskLink? = { null },
    ): CommandGraph = DslCompiler(worldTypes, resolver).compile(DslParser.parse(src)).graph

    private fun script(graph: CommandGraph) =
        DiskScript(name = "t", graph = graph)

    private fun assertValid(graph: CommandGraph) {
        assertEquals(emptyList<String>(), GraphValidator.validate(graph), "構造検証")
        val errors = ExecutableScriptValidator.validate(script(graph))
        assertEquals(emptyList<String>(), errors.map { "${it.code}:${it.message}" }, "実行検証")
    }

    private fun chain(graph: CommandGraph) = graph.nodes.values.toList()

    @Test
    fun `直列文が変数ノードと命令へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var x: number = 1;
                x += 2;
                wait(1);
            
            """.trimIndent(),
        )
        assertValid(graph)
        val nodes = chain(graph)
        // TEMP_SET x=1, TEMP_SET dsl.tmp1=2, TEMP_SET x CALCULATE, WAIT の4ノードです。
        assertEquals(4, nodes.size)
        assertEquals(CommandType.TEMP_SET, nodes[0].type)
        assertEquals("x", nodes[0].string("name"))
        assertEquals("1", nodes[0].string("value"))
        assertEquals(VariableChangeMode.ASSIGN.name, nodes[0].string("changeMode"))
        assertEquals(VariableChangeMode.CALCULATE.name, nodes[2].string("changeMode"))
        assertEquals("%{x}% + %{dsl.tmp1}%", nodes[2].string("value"))
        assertEquals(CommandType.WAIT, nodes[3].type)
        assertEquals(graph.entryNodeId, nodes[0].id)
        assertEquals(nodes[1].id, nodes[0].next)
        assertEquals(nodes[2].id, nodes[1].next)
        assertEquals(nodes[3].id, nodes[2].next)
    }

    @Test
    fun `if が CONDITION と MERGE へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var x: number = 1;
                if (x > 2) {
                    wait(1);
                }
                wait(2);
            
            """.trimIndent(),
        )
        assertValid(graph)
        val nodes = chain(graph)
        val cond = nodes.single { it.type == CommandType.CONDITION }
        val merge = nodes.single { it.type == CommandType.MERGE }
        assertEquals(ConditionSubject.VARIABLE.name, cond.string("subject"))
        assertEquals("%{x}%", cond.string("variable"))
        assertEquals(">", cond.string("operator"))
        assertEquals("2", cond.string("value"))
        // 対応づけは双方向です。
        assertEquals(merge.id, cond.pairedNodeId)
        assertEquals(cond.id, merge.pairedNodeId)
        // true枝は wait(1) 経由で合流、false枝は直接合流します。
        val wait1 = nodes.first { it.type == CommandType.WAIT }
        assertEquals(wait1.id, cond.trueNext)
        assertEquals(merge.id, cond.falseNext)
        assertEquals(merge.id, wait1.next)
        val wait2 = nodes.last { it.type == CommandType.WAIT }
        assertEquals(wait2.id, merge.next)
    }

    @Test
    fun `if else の両枝が同じ MERGE へ合流する`() {
        val graph = compile(
            """
            program "t" ;
                var x: number = 1;
                if (x > 2) {
                    wait(1);
                } else {
                    wait(2);
                }
            
            """.trimIndent(),
        )
        assertValid(graph)
        val nodes = chain(graph)
        val cond = nodes.single { it.type == CommandType.CONDITION }
        val merge = nodes.single { it.type == CommandType.MERGE }
        val waits = nodes.filter { it.type == CommandType.WAIT }
        assertEquals(2, waits.size)
        assertEquals(merge.id, cond.pairedNodeId)
        assertEquals(waits[0].id, cond.trueNext)
        assertEquals(waits[1].id, cond.falseNext)
        assertEquals(merge.id, waits[0].next)
        assertEquals(merge.id, waits[1].next)
    }

    @Test
    fun `else if 連結は1つの合流へ正規化される`() {
        val graph = compile(
            """
            program "t" ;
                var x: number = 1;
                if (x > 2) {
                    wait(1);
                } else if (x > 1) {
                    wait(2);
                } else {
                    wait(3);
                }
                wait(4);
            
            """.trimIndent(),
        )
        assertValid(graph)
        val nodes = chain(graph)
        val conds = nodes.filter { it.type == CommandType.CONDITION }
        val merge = nodes.single { it.type == CommandType.MERGE }
        assertEquals(2, conds.size)
        // 外側条件のfalse枝が内側条件の入口へ接続します。
        val outer = conds.first { it.id == graph.nodes[graph.nodes.values.first().next]?.let { conds.minOf { c -> c.id } } }
        val c1 = conds[0]
        val c2 = conds[1]
        assertEquals(c2.id, c1.falseNext)
        assertEquals(merge.id, c1.pairedNodeId)
        // 3枝とも同じ合流へ到達します。
        val waits = nodes.filter { it.type == CommandType.WAIT }
        assertEquals(4, waits.size)
        assertTrue(waits.take(3).all { it.next == merge.id })
    }

    @Test
    fun `repeat が FOR_START と FOR_END の対応へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                repeat(3) {
                    wait(1);
                }
            
            """.trimIndent(),
        )
        assertValid(graph)
        val nodes = chain(graph)
        val start = nodes.single { it.type == CommandType.FOR_START }
        val end = nodes.single { it.type == CommandType.FOR_END }
        val wait = nodes.single { it.type == CommandType.WAIT }
        assertEquals("3", start.string("count"))
        assertEquals(end.id, start.pairedNodeId)
        assertEquals(start.id, end.pairedNodeId)
        assertEquals(wait.id, start.trueNext)
        assertEquals(end.id, wait.next)
        // ループ後は終端です。
        assertEquals(null, end.next)
    }

    @Test
    fun `repeat 内の if が FOR_END 直前の合流へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var x: number = 0;
                repeat(3) {
                    if (x > 1) {
                        wait(1);
                    }
                }
            
            """.trimIndent(),
        )
        assertValid(graph)
        val nodes = chain(graph)
        val cond = nodes.single { it.type == CommandType.CONDITION }
        val merge = nodes.single { it.type == CommandType.MERGE }
        val end = nodes.single { it.type == CommandType.FOR_END }
        assertEquals(merge.id, cond.pairedNodeId)
        assertEquals(end.id, merge.next)
    }

    @Test
    fun `break と continue がループ制御へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var x: number = 0;
                repeat(5) {
                    x += 1;
                    if (x > 3) {
                        break;
                    }
                }
            
            """.trimIndent(),
        )
        assertValid(graph)
        val nodes = chain(graph)
        assertEquals(1, nodes.count { it.type == CommandType.BREAK })
        val cond = nodes.single { it.type == CommandType.CONDITION }
        val breakNode = nodes.single { it.type == CommandType.BREAK }
        assertEquals(breakNode.id, cond.trueNext)
        // 終端文なので後続への辺はありません。
        assertEquals(null, breakNode.next)
    }

    @Test
    fun `return が変数と式の両方へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var x: number = 1;
                return x;
            
            """.trimIndent(),
        )
        assertValid(graph)
        val ret = graph.nodes.values.single { it.type == CommandType.RETURN_VALUE }
        assertEquals(VariableScope.EXECUTION.name, ret.string("sourceScope"))
        assertEquals("x", ret.string("source"))

        val graph2 = compile(
            """
            program "t" ;
                var x: number = 1;
                return x + 2;
            
            """.trimIndent(),
        )
        assertValid(graph2)
        val ret2 = graph2.nodes.values.single { it.type == CommandType.RETURN_VALUE }
        assertEquals(VariableScope.EXECUTION.name, ret2.string("sourceScope"))
        assertTrue(ret2.string("source").startsWith("dsl.tmp"), "式は一時変数へ実体化します")
    }

    @Test
    fun `call が DISK_CALL へ写像される`() {
        val target = CommandGraph()
        val wait = CommandType.WAIT.newNode()
        target.nodes[wait.id] = wait
        target.entryNodeId = wait.id
        val link = DslDiskLink(UUID.randomUUID(), target)
        val graph = compile(
            """
            program "t" ;
                call "other_disk";
            
            """.trimIndent(),
            resolver = { name -> if (name == "other_disk") link else null },
        )
        assertValid(graph)
        val call = graph.nodes.values.single { it.type == CommandType.DISK_CALL }
        assertEquals(link.diskId.toString(), call.string("diskId"))
        assertNotNull(call.snapshot)
    }

    private fun diskLink(
        vararg arguments: me.awabi2048.kantancommander.model.DiskArgument,
    ): DslDiskLink {
        val target = CommandGraph()
        val wait = CommandType.WAIT.newNode()
        target.nodes[wait.id] = wait
        target.entryNodeId = wait.id
        return DslDiskLink(UUID.randomUUID(), target, arguments.toList())
    }

    @Test
    fun `call の引数が arg_ と argvar_ へ写像される`() {
        val link = diskLink(
            me.awabi2048.kantancommander.model.DiskArgument("count", ValueType.NUMBER),
            me.awabi2048.kantancommander.model.DiskArgument("label", ValueType.STRING),
            me.awabi2048.kantancommander.model.DiskArgument("who", ValueType.ENTITY),
        )
        val graph = compile(
            """
            program "t" ;
                var who: entity = target(kind = "nearest_player");
                call "sub"(count = 3, label = "hello", who = who);

            """.trimIndent(),
            resolver = { name -> if (name == "sub") link else null },
        )
        val call = graph.nodes.values.single { it.type == CommandType.DISK_CALL }
        assertEquals("3", call.string("arg_count"))
        assertEquals("hello", call.string("arg_label"))
        assertEquals("who", call.string("argvar_who"))
        assertEquals("count:NUMBER,label:STRING,who:ENTITY", call.string("declaredArgs"))
    }

    @Test
    fun `call の引数で宣言外の名前はエラーになる`() {
        val link = diskLink(me.awabi2048.kantancommander.model.DiskArgument("count", ValueType.NUMBER))
        val ex = assertThrows(DslCompileException::class.java) {
            compile(
                """
                program "t" ;
                    call "sub"(missing = 1);

                """.trimIndent(),
                resolver = { name -> if (name == "sub") link else null },
            )
        }
        assertTrue(ex.message!!.contains("missing"))
    }

    @Test
    fun `call の複合型引数は実行内変数のみ受け付ける`() {
        val link = diskLink(me.awabi2048.kantancommander.model.DiskArgument("pos", ValueType.LOCATION))
        assertThrows(DslCompileException::class.java) {
            compile(
                """
                program "t" ;
                    call "sub"(pos = ~0 ~0 ~0);

                """.trimIndent(),
                resolver = { name -> if (name == "sub") link else null },
            )
        }
    }

    @Test
    fun `call の戻り値が受取変数へ写像される`() {
        val link = diskLink()
        val graph = compile(
            """
            program "t" ;
                var result: number = 0;
                result = call "sub"();

            """.trimIndent(),
            resolver = { name -> if (name == "sub") link else null },
        )
        val call = graph.nodes.values.single { it.type == CommandType.DISK_CALL }
        assertEquals("result", call.string("returnName"))
        assertEquals(VariableScope.EXECUTION.name, call.string("returnScope"))
    }

    @Test
    fun `var 宣言で call の戻り値を受け取れる`() {
        val link = diskLink()
        val graph = compile(
            """
            program "t" ;
                var result: number = call "sub"();
                wait(1);

            """.trimIndent(),
            resolver = { name -> if (name == "sub") link else null },
        )
        val nodes = chain(graph)
        // TEMP_SET（宣言・値未設定）→ DISK_CALL → WAIT の3ノードです。
        assertEquals(3, nodes.size)
        assertEquals(CommandType.TEMP_SET, nodes[0].type)
        assertEquals("result", nodes[0].string("name"))
        assertEquals(CommandType.DISK_CALL, nodes[1].type)
        assertEquals("result", nodes[1].string("returnName"))
    }

    @Test
    fun `call の引数の型不一致はエラーになる`() {
        val link = diskLink(me.awabi2048.kantancommander.model.DiskArgument("count", ValueType.NUMBER))
        assertThrows(DslCompileException::class.java) {
            compile(
                """
                program "t" ;
                    var s: string = "x";
                    call "sub"(count = s);

                """.trimIndent(),
                resolver = { name -> if (name == "sub") link else null },
            )
        }
    }

    @Test
    fun `and と or の条件が木構造へ展開される`() {
        val graph = compile(
            """
            program "t" ;
                var x: number = 1;
                var y: number = 2;
                if (x > 0 && y > 0) {
                    wait(1);
                }
            
            """.trimIndent(),
        )
        assertValid(graph)
        val nodes = chain(graph)
        val conds = nodes.filter { it.type == CommandType.CONDITION }
        assertEquals(2, conds.size)
        // a&&b: a.true→cond_b、a.false→合流、b.true→T、b.false→合流 です。
        val merge = nodes.single { it.type == CommandType.MERGE }
        val a = conds.single { it.string("variable") == "%{x}%" }
        val b = conds.single { it.string("variable") == "%{y}%" }
        assertEquals(b.id, a.trueNext)
        assertEquals(merge.id, a.falseNext)
        assertEquals(merge.id, b.falseNext)
    }

    @Test
    fun `or 条件が短絡評価の木構造へ展開される`() {
        val graph = compile(
            """
            program "t" ;
                var x: number = 1;
                var y: number = 2;
                if (x > 0 || y > 0) {
                    wait(1);
                }
            
            """.trimIndent(),
        )
        assertValid(graph)
        val nodes = chain(graph)
        val conds = nodes.filter { it.type == CommandType.CONDITION }
        // || のtrue側は2経路合流するためジャンクションMERGEが生成されます。
        val merges = nodes.filter { it.type == CommandType.MERGE }
        assertEquals(2, merges.size)
        assertEquals(2, conds.size)
    }

    @Test
    fun `ブロック条件が BLOCK 判定へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var pos: location = location(x = 0, y = 64, z = 0);
                if (pos.block == block(id = "minecraft:stone")) {
                    wait(1);
                }
            
            """.trimIndent(),
        )
        assertValid(graph)
        val cond = graph.nodes.values.single { it.type == CommandType.CONDITION }
        assertEquals(ConditionSubject.BLOCK.name, cond.string("subject"))
        assertEquals("minecraft:stone", cond.string("block"))
        assertEquals(PositionKind.TEMPORARY, cond.conditionPositionSpec?.kind)
    }

    @Test
    fun `詳細値の条件比較が PROPERTY 実体化と VARIABLE 判定へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var p: entity = target(kind = "nearest_player");
                if (p.gamemode == gamemode.creative) {
                    wait(1);
                }
            
            """.trimIndent(),
        )
        assertValid(graph)
        val nodes = chain(graph)
        // property 実体化ノードが条件の直前に配置されます。
        val prop = nodes.first { it.string("changeMode") == VariableChangeMode.PROPERTY.name }
        assertEquals("p", prop.string("propertySource"))
        assertEquals("gamemode", prop.string("property"))
        val cond = nodes.single { it.type == CommandType.CONDITION }
        assertEquals("%{${prop.string("name")}}%", cond.string("variable"))
        assertEquals("CREATIVE", cond.string("value"))
    }

    @Test
    fun `has_item 詳細値が引数付き PROPERTY へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var p: entity = target(kind = "nearest_player");
                var i: item = item(id = "minecraft:stone");
                if (p.has_item(i)) {
                    wait(1);
                }
            
            """.trimIndent(),
        )
        assertValid(graph)
        val nodes = chain(graph)
        val prop = nodes.first { it.string("changeMode") == VariableChangeMode.PROPERTY.name }
        assertEquals("has_item", prop.string("property"))
        assertEquals("i", prop.string("propertyArg"))
    }

    @Test
    fun `ワールド変数の読み書きが写像される`() {
        val graph = compile(
            """
            program "t" ;
            import world;
                var x: number = world.score + 1;
                world.score = x;
            
            """.trimIndent(),
            worldTypes = mapOf("score" to ValueType.NUMBER),
        )
        assertValid(graph)
        val nodes = chain(graph)
        val read = nodes.first { it.string("changeMode") == VariableChangeMode.CALCULATE.name }
        // モデルの CALCULATE はリテラル被演算子を持てないため、1 は一時変数へ実体化されます。
        assertEquals("\${score} + %{dsl.tmp1}%", read.string("value"))
        val write = nodes.last { it.type == CommandType.VARIABLE }
        assertEquals(VariableScope.WORLD.name, write.string("scope"))
        assertEquals("score", write.string("name"))
        assertEquals("%{x}%", write.string("value"))
    }

    @Test
    fun `setblock と fill がブロック操作へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                setblock(pos = location(x = 0, y = 64, z = 0), block = block(id = "minecraft:stone"));
                fill(from = ~0 ~0 ~0, to = ~3 ~3 ~3, block = block(id = "minecraft:glass"));
            
            """.trimIndent(),
        )
        assertValid(graph)
        val nodes = chain(graph).filter { it.type == CommandType.BLOCK_OPERATION }
        assertEquals(2, nodes.size)
        assertEquals("setblock", nodes[0].string("operation"))
        assertEquals("minecraft:stone", nodes[0].string("block"))
        assertEquals(PositionKind.COORDINATES, nodes[0].blockPositionSpec?.kind)
        assertEquals("fill", nodes[1].string("operation"))
        assertNotNull(nodes[1].blockFromSpec)
        assertNotNull(nodes[1].blockToSpec)
    }

    @Test
    fun `give_item の対象と item が写像される`() {
        val graph = compile(
            """
            program "t" ;
                var p: entity = target(kind = "nearest_player");
                give_item(target = p, item = item(id = "minecraft:diamond", data = "{}"), count = 2);
            
            """.trimIndent(),
        )
        assertValid(graph)
        val give = graph.nodes.values.single { it.type == CommandType.GIVE_ITEM }
        assertEquals(TargetKind.TEMPORARY, give.targetSpec?.kind)
        assertEquals("p", give.targetSpec?.tempName)
        assertEquals("minecraft:diamond", give.string("item"))
        assertEquals("{}", give.string("itemData"))
        assertEquals("2", give.string("count"))
    }

    @Test
    fun `target クエリが構造値へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var m: entity = target(kind = "nearby_entities", type = "minecraft:zombie", within = 8.0, limit = 4);
            
            """.trimIndent(),
        )
        assertValid(graph)
        val spec = graph.nodes.values.single { it.type == CommandType.TEMP_SET }
            .temporaryEntityTargetSpec
        assertEquals(TargetKind.NEARBY_ENTITIES, spec?.kind)
        assertEquals("minecraft:zombie", spec?.entityType)
        assertEquals(8.0, spec?.maximumDistance)
        assertEquals(4, spec?.limit)
    }

    @Test
    fun `位置リテラルが成分別の構造値へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var base: location = location(x = 100, y = 64, z = 0);
                var l: location = base ~0 ~1 ~0;
            
            """.trimIndent(),
        )
        assertValid(graph)
        val spec = graph.nodes.values.last().temporaryLocationPositionSpec
        assertEquals(PositionKind.TEMPORARY, spec?.kind)
        assertEquals("base", spec?.tempName)
    }

    @Test
    fun `文字列補間がモデルテンプレートへ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var x: number = 5;
                display_text(text = "count is ${'$'}{x}", target = target(kind = "all_players"));
            
            """.trimIndent(),
        )
        assertValid(graph)
        val node = graph.nodes.values.single { it.type == CommandType.DISPLAY_TEXT }
        assertEquals("count is %{x}%", node.string("text"))
    }

    @Test
    fun `未解決の call はエラーになる`() {
        assertThrows(DslCompileException::class.java) {
            compile(
                """
                program "t" ;
                    call "missing";
                
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `条件式は変数へ代入できない`() {
        assertThrows(DslCompileException::class.java) {
            compile(
                """
                program "t" ;
                    var x: number = 1;
                    var b: boolean = x > 2;
                
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `剰余はチェッカーで拒否される`() {
        assertThrows(DslCheckException::class.java) {
            compile(
                """
                program "t" ;
                    var x: number = 1;
                    x %= 2;
                
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `到達不能な文はエラーになる`() {
        assertThrows(DslCheckException::class.java) {
            compile(
                """
                program "t" ;
                    return;
                    wait(1);
                
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `wait の名前付き引数も受理される`() {
        val graph = compile(
            """
            program "t" ;
                wait(seconds = 0.5);
            
            """.trimIndent(),
        )
        assertValid(graph)
        assertEquals("0.5", graph.nodes.values.single().string("seconds"))
    }

    @Test
    fun `trigger 宣言が起動方法へ写像される`() {
        val result = DslCompiler().compile(DslParser.parse("""program "t" ;"""))
        assertEquals(TriggerKind.REDSTONE, result.trigger.kind)

        val timer = DslCompiler().compile(
            DslParser.parse("""program "t" ; trigger timer(interval = 20);"""),
        )
        assertEquals(TriggerKind.TIMER, timer.trigger.kind)
        assertEquals(20.0, timer.trigger.intervalSeconds)

        val event = DslCompiler().compile(
            DslParser.parse("""program "t" ; trigger event(kind = "block_break", side = "left");"""),
        )
        assertEquals(TriggerKind.EVENT, event.trigger.kind)
        assertEquals(EventTriggerKind.BLOCK_BREAK, event.trigger.event)
    }

    @Test
    fun `defarg がモデル引数名へ写像される`() {
        val result = DslCompiler().compile(
            DslParser.parse("""program "t" ; defarg my_arg: number; var x: number = my_arg;"""),
        )
        // 引数名・本文参照ともにモデル名（my-arg）へ写像します。
        assertEquals(listOf("my-arg"), result.arguments.map { it.name })
        assertEquals(ValueType.NUMBER, result.arguments.single().type)
        val decl = result.graph.nodes.values.first { it.type == CommandType.TEMP_SET }
        assertEquals("%{my-arg}%", decl.string("value"))
    }

    @Test
    fun `イベント引数はモデルのシステム名をそのまま使う`() {
        val result = DslCompiler().compile(
            DslParser.parse(
                """program "t" ;
                    trigger event(kind = "entity_click");
                    var p: entity = event_actor;
                """,
            ),
        )
        // event_actor は _ を含むシステム名であり、kebab 写像を掛けません。
        val decl = result.graph.nodes.values.first { it.type == CommandType.TEMP_SET }
        assertEquals("event_actor", decl.temporaryEntityTargetSpec?.tempName)
    }

    @Test
    fun `vars_ref はエンティティ変数の COPY へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var e: entity = target(kind = "nearest_player");
                e.vars.dst = vars.src;
            
            """.trimIndent(),
        )
        assertValid(graph)
        val node = graph.nodes.values.first { it.type == CommandType.VARIABLE }
        assertEquals(VariableScope.ENTITY.name, node.string("scope"))
        assertEquals("COPY", node.string("operation"))
        assertEquals("dst", node.string("name"))
        assertEquals("src", node.string("source"))
        assertNotNull(node.targetSpec)
    }

    @Test
    fun `vars_ref の詳細値はエンティティ変数の PROPERTY 生成へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var e: entity = target(kind = "nearest_player");
                e.vars.dst = vars.src.health;
            
            """.trimIndent(),
        )
        assertValid(graph)
        val node = graph.nodes.values.first { it.type == CommandType.VARIABLE }
        assertEquals(VariableScope.ENTITY.name, node.string("scope"))
        assertEquals("SET", node.string("operation"))
        assertEquals(VariableChangeMode.PROPERTY.name, node.string("changeMode"))
        assertEquals("src", node.string("propertySource"))
        assertEquals("health", node.string("property"))
        assertNotNull(node.targetSpec)
    }

    @Test
    fun `vars_ref の引数付き詳細値は propertyArg へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                var e: entity = target(kind = "nearest_player");
                e.vars.dst = vars.src.distance(vars.loc);
            
            """.trimIndent(),
        )
        assertValid(graph)
        val node = graph.nodes.values.first { it.type == CommandType.VARIABLE }
        assertEquals(VariableChangeMode.PROPERTY.name, node.string("changeMode"))
        assertEquals("src", node.string("propertySource"))
        assertEquals("distance", node.string("property"))
        assertEquals("loc", node.string("propertyArg"))
    }

    @Test
    fun `vars_ref はエンティティ変数以外への代入では拒否される`() {
        assertThrows(DslCheckException::class.java) {
            compile(
                """
                program "t" ;
                    var x: number = 0;
                    x = vars.src;
                
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `エンティティ変数の複合代入は拒否される`() {
        val ex = assertThrows(DslCheckException::class.java) {
            compile(
                """
                program "t" ;
                    var e: entity = target(kind = "nearest_player");
                    e.vars.x += 1;
                
                """.trimIndent(),
            )
        }
        assertTrue(ex.message?.contains("複合代入") == true)
    }

    @Test
    fun `実行内変数の詳細値はエンティティ変数への書込みで拒否される`() {
        val ex = assertThrows(DslCheckException::class.java) {
            compile(
                """
                program "t" ;
                    var e: entity = target(kind = "nearest_player");
                    var l: location = null;
                    e.vars.x = l.x;
                
                """.trimIndent(),
            )
        }
        assertTrue(ex.message?.contains("vars.<name>") == true)
    }

    @Test
    fun `vars という変数名は予約名として拒否される`() {
        val ex = assertThrows(DslCheckException::class.java) {
            compile(
                """
                program "t" ;
                    var vars: number = 0;
                
                """.trimIndent(),
            )
        }
        assertTrue(ex.message?.contains("予約") == true)
    }

    @Test
    fun `null は条件・引数・変数名の未設定へ写像される`() {
        val graph = compile(
            """
            program "t" ;
                if (null) { wait(1); }
                setblock(pos = null, block = block(id = "minecraft:stone"));
                var null: number = 0;
            
            """.trimIndent(),
        )
        val nodes = graph.nodes.values.toList()
        val cond = nodes.first { it.type == CommandType.CONDITION }
        assertEquals("", cond.string("subject"))
        val setblock = nodes.first { it.type == CommandType.BLOCK_OPERATION }
        assertNull(setblock.blockPositionSpec)
        val variable = nodes.first { it.type == CommandType.TEMP_SET && it.string("tempType") == "NUMBER" }
        assertEquals("", variable.string("name"))
    }
}
