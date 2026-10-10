package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.ValueType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * DSL フロントエンド（字句＋構文）のテストです。
 *
 * 仕様案 `.docs/specs/kantan-commander-dsl-draft.md` の確定文法を固定します。
 * 型や引数の意味検証は対象外とし、構文と形状だけを確認します。
 */
class DslParserTest {
    private fun parse(text: String): DslProgram = DslParser.parse(text)
    private fun fail(text: String): DslParseException =
        assertThrows(DslParseException::class.java) { parse(text) }

    @Test
    fun `parses minimal program`() {
        val program = parse("""program "hello" ;""")
        assertEquals("hello", program.name)
        assertTrue(program.body.isEmpty())
    }

    @Test
    fun `program header is required`() {
        fail("""var x: number = 1;""")
        fail("""program {}""")
        fail("""program "x" """)
    }

    @Test
    fun `var declaration requires type and initializer`() {
        val program = parse("""program "p" ; var x: number = 1; """)
        val decl = program.body.single() as DslVarDecl
        assertEquals("x", decl.name)
        assertEquals(ValueType.NUMBER, decl.type)
        assertTrue(decl.value is DslNumberLiteral)

        fail("""program "p" ; var x = 1; """)
        fail("""program "p" ; var x: number; """)
        fail("""program "p" ; var x: integer = 1; """)
    }

    @Test
    fun `var declaration accepts all value types`() {
        for (t in listOf("number", "string", "boolean", "location", "item", "block", "entity")) {
            val program = parse("""program "p" ; var v: $t = item(id = "a"); """)
            assertEquals(ValueType.valueOf(t.uppercase()), (program.body.single() as DslVarDecl).type)
        }
    }

    @Test
    fun `assignment supports compound operators`() {
        val program = parse(
            """program "p" ;
                x = 1; x += 2; x -= 3; x *= 4; x /= 5; x %= 6;
            """,
        )
        val ops = program.body.map { (it as DslAssign).op }
        assertEquals(
            listOf(DslAssignOp.ASSIGN, DslAssignOp.ADD, DslAssignOp.SUB, DslAssignOp.MUL, DslAssignOp.DIV, DslAssignOp.MOD),
            ops,
        )
    }

    @Test
    fun `world variable read and write`() {
        val program = parse("""program "p" ; world.money = 10; var m: number = world.money; """)
        val assign = program.body[0] as DslAssign
        assertEquals("money", (assign.target as DslAssignTarget.WorldVariable).name)
        val decl = program.body[1] as DslVarDecl
        assertEquals("money", (decl.value as DslWorldRef).name)
    }

    @Test
    fun `if else if else normalizes else-if as nested if`() {
        val program = parse(
            """program "p" ;
                if (a) { wait(1); } else if (b) { wait(2); } else { wait(3); }
            """,
        )
        val ifStmt = program.body.single() as DslIf
        assertNotNull(ifStmt.elseBody)
        val elseIf = ifStmt.elseBody!!.single() as DslIf
        assertNotNull(elseIf.elseBody)
        assertTrue(elseIf.elseBody!!.single() is DslCallStmt)
    }

    @Test
    fun `if requires parens and braces`() {
        fail("""program "p" ; if a { } """)
        fail("""program "p" ; if (a) wait(1); """)
    }

    @Test
    fun `repeat break continue return`() {
        val program = parse(
            """program "p" ;
                repeat(5) { break; continue; }
                return;
                return x + 1;
            """,
        )
        val repeat = program.body[0] as DslRepeat
        assertTrue(repeat.body[0] is DslBreak)
        assertTrue(repeat.body[1] is DslContinue)
        assertNull((program.body[1] as DslReturn).value)
        assertNotNull((program.body[2] as DslReturn).value)
    }

    @Test
    fun `wait and call statements`() {
        val program = parse("""program "p" ; wait(1); wait(seconds = 1); call "disk_a"; """)
        val wait1 = program.body[0] as DslCallStmt
        assertEquals("wait", wait1.name)
        assertNull(wait1.args.single().name)
        val wait2 = program.body[1] as DslCallStmt
        assertEquals("seconds", wait2.args.single().name)
        val call = program.body[2] as DslCallDisk
        assertEquals("disk_a", call.diskId.plainText())
    }

    @Test
    fun `semicolons are required and allow one-line programs`() {
        fail("""program "p" ; var x: number = 1 """)
        fail("""program "p" ; wait(1) """)
        val program = parse("""program "p" ; var x: number = 1; wait(1); """)
        assertEquals(2, program.body.size)
    }

    @Test
    fun `expression precedence`() {
        val program = parse("""program "p" ; var v: boolean = 1 + 2 * 3 == 7 && !false; """)
        val top = (program.body.single() as DslVarDecl).value as DslBinary
        assertEquals(DslBinaryOp.AND, top.op)
        val eq = top.left as DslBinary
        assertEquals(DslBinaryOp.EQ, eq.op)
        val add = eq.left as DslBinary
        assertEquals(DslBinaryOp.ADD, add.op)
        assertEquals(DslBinaryOp.MUL, (add.right as DslBinary).op)
        assertEquals(DslUnaryOp.NOT, (top.right as DslUnary).op)
    }

    @Test
    fun `property reads and gamemode constants`() {
        val program = parse(
            """program "p" ;
                var g: boolean = p.gamemode == gamemode.creative;
                var d: number = e.distance(base);
                var h: boolean = e.has_item(sword);
                var b: block = l.block;
            """,
        )
        val cmp = (program.body[0] as DslVarDecl).value as DslBinary
        assertTrue(cmp.left is DslPropertyRead)
        val const = cmp.right as DslConst
        assertEquals("gamemode", const.group)
        assertEquals("creative", const.name)

        val dist = (program.body[1] as DslVarDecl).value as DslPropertyRead
        assertEquals("distance", dist.name)
        assertNotNull(dist.arg)
        val hasItem = (program.body[2] as DslVarDecl).value as DslPropertyRead
        assertEquals("has_item", hasItem.name)
        assertNotNull(hasItem.arg)

        fail("""program "p" ; var g: boolean = p.gamemode == gamemode.hardcore; """)
    }

    @Test
    fun `position literals`() {
        val program = parse(
            """program "p" ;
                summon(entity = "minecraft:zombie", at = ~0 ~1 ~0);
                var v: location = base ~0 ~1 ~0;
                var w: location = ^0 ^0 ^2;
                var m: location = ~0 64 ~-1;
            """,
        )
        val at = (program.body[0] as DslCallStmt).args.single { it.name == "at" }.value as DslPositionLiteral
        assertNull(at.base)
        assertEquals(
            listOf(CoordinateSemantics.RELATIVE, CoordinateSemantics.RELATIVE, CoordinateSemantics.RELATIVE),
            at.components.map { it.semantics },
        )
        assertEquals(listOf(0.0, 1.0, 0.0), at.components.map { it.value })

        val based = (program.body[1] as DslVarDecl).value as DslPositionLiteral
        assertTrue(based.base is DslVarRef)

        val local = (program.body[2] as DslVarDecl).value as DslPositionLiteral
        assertTrue(local.components.all { it.semantics == CoordinateSemantics.LOCAL })

        val mixed = (program.body[3] as DslVarDecl).value as DslPositionLiteral
        assertEquals(
            listOf(CoordinateSemantics.RELATIVE, CoordinateSemantics.ABSOLUTE, CoordinateSemantics.RELATIVE),
            mixed.components.map { it.semantics },
        )
        assertEquals(-1.0, mixed.components[2].value)
    }

    @Test
    fun `position literal requires exactly three components`() {
        fail("""program "p" ; var v: location = ~0 ~1; """)
        fail("""program "p" ; var v: location = ~^ ~0 ~0; """)
    }

    @Test
    fun `string interpolation`() {
        val program = parse("""program "p" ; var s: string = "残り ${'$'}{count + 1} 個"; """)
        val lit = (program.body.single() as DslVarDecl).value as DslStringLiteral
        assertEquals(3, lit.parts.size)
        assertEquals("残り ", (lit.parts[0] as DslTextPart).text)
        assertTrue(lit.parts[1] is DslInterpPart)
        assertEquals(" 個", (lit.parts[2] as DslTextPart).text)
        assertNull(lit.plainText())
    }

    @Test
    fun `comments are skipped`() {
        val program = parse(
            """program "p" ;
                # line comment
                wait(1); // trailing
            """,
        )
        assertEquals(1, program.body.size)
    }

    @Test
    fun `named args and constructor calls`() {
        val program = parse(
            """program "p" ;
                var base: location = location(x = 100, y = 64, z = 0, yaw = 0, pitch = 0);
                var it: item = item(id = "minecraft:stone", amount = 2);
                var mob: entity = target(kind = "nearest_player", within = 8.0);
            """,
        )
        val loc = (program.body[0] as DslVarDecl).value as DslCallExpr
        assertEquals("location", loc.name)
        assertEquals(listOf("x", "y", "z", "yaw", "pitch"), loc.args.map { it.name })
        val mob = (program.body[2] as DslVarDecl).value as DslCallExpr
        assertEquals("target", mob.name)
    }

    @Test
    fun `positional arg must precede named args`() {
        fail("""program "p" ; summon(entity = "x", "y"); """)
    }

    @Test
    fun `unterminated string fails`() {
        fail("""program "p" ; var s: string = "abc; """)
    }

    @Test
    fun `double coordinate prefix fails`() {
        fail("""program "p" ; var v: location = ~^0 ~1 ~0; """)
    }

    @Test
    fun `single ampersand fails`() {
        fail("""program "p" ; var b: boolean = a & b; """)
    }

    @Test
    fun `declaration section parses`() {
        val program = parse(
            """program "p" ;
                import world;
                trigger timer(interval = 20);
                defarg amount: number;
                defarg label: string;
                wait(1);
            """,
        )
        assertEquals(4, program.decls.size)
        assertTrue(program.decls[0] is DslImportWorld)
        val trigger = program.decls[1] as DslTriggerDecl
        assertEquals("timer", trigger.spec.name)
        assertEquals(listOf("amount", "label"), program.decls.filterIsInstance<DslDefarg>().map { it.name })
        assertEquals(1, program.body.size)
    }

    @Test
    fun `trigger bare identifier normalizes to empty call`() {
        val program = parse("""program "p" ; trigger redstone;""")
        val trigger = program.decls.single() as DslTriggerDecl
        assertEquals("redstone", trigger.spec.name)
        assertTrue(trigger.spec.args.isEmpty())
    }

    @Test
    fun `event trigger parses`() {
        val program = parse("""program "p" ; trigger event(kind = "block_break", side = "left");""")
        val trigger = program.decls.single() as DslTriggerDecl
        assertEquals("event", trigger.spec.name)
        assertEquals(listOf("kind", "side"), trigger.spec.args.map { it.name })
    }

    @Test
    fun `null marks unset positions`() {
        val program = parse(
            """program "p" ;
                if (null) { }
                setblock(pos = null, block = "minecraft:stone");
                var null: number = 0;
            """,
        )
        assertTrue((program.body[0] as DslIf).condition is DslNull)
        val call = program.body[1] as DslCallStmt
        assertTrue(call.args.single { it.name == "pos" }.value is DslNull)
        assertEquals("", (program.body[2] as DslVarDecl).name)
    }

    @Test
    fun `declarations must precede body`() {
        fail("""program "p" ; wait(1); import world;""")
        fail("""program "p" ; wait(1); trigger redstone;""")
        fail("""program "p" ; wait(1); defarg x: number;""")
    }
}
