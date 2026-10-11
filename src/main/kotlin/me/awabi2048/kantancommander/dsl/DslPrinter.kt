package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.DiskArgument
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.MyworldSpawnKind
import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.model.redstoneSignalSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TemporaryTemplate
import me.awabi2048.kantancommander.model.TriggerClickSide
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.TriggerLeverState
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.variableScope
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.declaredArguments
import java.util.UUID

/**
 * モデルを DSL へ写像できない構造を検出した失敗です。
 * 対象ノードが GUI 専用の表現（折り畳み・捕捉位置・対象内包の向き等）を
 * 持つ場合に投げられ、呼び出し側は DSL 表示を諦めて GUI 表示へ戻ります。
 */
class DslPrintException(message: String) : Exception(message)

/**
 * 実行モデル（CommandGraph）を canonical な DSL テキストへ復元するプリンタです。
 *
 * 設計の要点:
 * - 直列の `next` 鎖を順にたどり、CONDITION は「条件領域」として復元、
 *   FOR_START/FOR_END は `repeat` ブロックへ畳み込みます。
 * - 条件領域の切り分けは「各極性（true/false）の出口は高々1箇所」という
 *   コンパイラ生成グラフの不変条件に依ります。出口を超える条件は
 *   else-if の入口として枝ブロック側へ返します。
 * - 条件内部のジャンクション合流（中間 MERGE）は式の境界として扱い、
 *   `if (a||b) { if (c) ... }` のように意味等価な入れ子へ正規化します。
 * - コンパイラ生成の一時変数 `dsl.tmpN` は定義を記録して参照箇所へ
 *   インライン展開し、DSL テキストへは出しません。
 * - モデル名（`my-var` 等）は DSL 名（`my_var`）へ逆写像します。
 */
class DslPrinter(
    private val diskNameResolver: (UUID) -> String? = { it.toString() },
) {
    /**
     * グラフと宣言部を `.kcp` の完全な DSL テキストへ復元します。
     *
     * 正規形は `program "name";` の宣言行に続き、`import world;`（本文が
     * ワールドコンテキストを使う場合のみ）・`trigger ...;`（非既定の場合のみ）・
     * `defarg`（引数宣言順）を置き、本文をトップレベルへ並べます。
     */
    fun print(
        graph: CommandGraph,
        name: String,
        trigger: ScriptTrigger = ScriptTrigger(TriggerKind.REDSTONE),
        arguments: List<DiskArgument> = emptyList(),
    ): String {
        val printer = Printer(unfoldAll(graph))
        return printer.printProgram(name, trigger, arguments)
    }

    /**
     * 折り畳みノード（FOLDED_IF/FOLDED_FOR）を全展開した複製を返します。
     * 元グラフは変更せず、折り畳みを含まない入力はそのまま返します。
     */
    private fun unfoldAll(graph: CommandGraph): CommandGraph {
        fun isFolded(n: CommandNode) =
            n.type == CommandType.FOLDED_IF || n.type == CommandType.FOLDED_FOR
        if (graph.nodes.values.none(::isFolded)) return graph
        val copy = graph.deepCopy()
        var guard = copy.nodes.size + 8
        while (true) {
            if (guard-- <= 0) throw DslPrintException("折り畳み展開が上限を超えました")
            val folded = copy.nodes.values.firstOrNull(::isFolded) ?: return copy
            if (!me.awabi2048.kantancommander.data.GraphEditor.unfold(copy, folded.id)) {
                throw DslPrintException("折り畳みノードを展開できません: ${folded.id}")
            }
        }
    }

    private inner class Printer(val graph: CommandGraph) {
        val out = StringBuilder()
        var indent = 0
        /** コンパイラ生成一時変数 `dsl.tmpN` の定義式（展開済みテキスト）です。 */
        val tempDefs = mutableMapOf<String, String>()
        /** DSL へ出力済みの変数名です（var 宣言と再代入の切り分け）。 */
        val declared = mutableSetOf<String>()

        fun printProgram(name: String, trigger: ScriptTrigger, arguments: List<DiskArgument>): String {
            // 本文を先に出力し、使用したコンテキストから import 宣言を導出します。
            emitBlock(graph.entryNodeId, null)
            val body = out.toString()
            out.setLength(0)

            out.append("program \"").append(escape(name)).append("\";\n")
            val decls = StringBuilder()
            if (WORLD_USE.containsMatchIn(body)) decls.append("import world;\n")
            triggerDecl(trigger)?.let { decls.append(it).append('\n') }
            arguments.forEach { arg ->
                decls.append("defarg ").append(varNameText(arg.name))
                    .append(": ").append(arg.type.name.lowercase()).append(";\n")
            }
            if (decls.isNotEmpty()) out.append(decls)
            if (body.isNotEmpty()) out.append('\n')
            out.append(body)
            return out.toString()
        }

        /** `trigger` 宣言の行です。既定の redstone は省略します。 */
        private fun triggerDecl(trigger: ScriptTrigger): String? {
            val t = trigger.normalized()
            return when (t.kind) {
                TriggerKind.REDSTONE -> null
                TriggerKind.TIMER -> "trigger timer(interval = ${num(t.intervalSeconds)});"
                TriggerKind.EVENT -> "trigger event(${eventTriggerArgs(t)});"
            }
        }

        /** `trigger event(...)` の引数列です。種別と詳細条件を含みます。 */
        private fun eventTriggerArgs(t: ScriptTrigger): String {
            val event = t.event
                ?: throw DslPrintException("イベント起動の種別が未設定です")
            val c = t.conditions
            val args = mutableListOf("kind = \"${event.name.lowercase()}\"")
            c.actorTarget?.let { args += "actor = ${targetExpr(it)}" }
            c.actorId?.let { args += "actor_id = \"$it\"" }
            c.blockType?.let { args += "block = \"${escape(it)}\"" }
            c.blockIdentity?.let { b ->
                args += "block_world = \"${escape(b.world)}\""
                args += "block_x = ${b.x}"
                args += "block_y = ${b.y}"
                args += "block_z = ${b.z}"
            }
            c.entityTarget?.let { args += "entity = ${targetExpr(it)}" }
            c.entityId?.let { args += "entity_id = \"$it\"" }
            c.clickSide?.let { args += "side = \"${it.name.lowercase()}\"" }
            c.leverState?.let { args += "state = \"${it.name.lowercase()}\"" }
            return args.joinToString(", ")
        }

        private fun line(text: String) {
            out.append("    ".repeat(indent)).append(text).append('\n')
        }

        private fun unsupported(what: String): Nothing =
            throw DslPrintException("DSL へ写像できない構造です: $what")

        // ---- ブロック走査 ----

        /**
         * `entry` から `boundary`（含まない）までの文を出力し、走査が
         * 停止した位置のノード ID を返します。
         * MERGE は合流の構造要素として透過します。FOR_END は `endAtForEnd`
         * が真のとき領域の自然な終端として停止して返し、偽のときは
         * 対応ループの外側への出現としてエラーにします。
         */
        private fun emitBlock(entry: UUID?, boundary: UUID?, endAtForEnd: Boolean = false): UUID? {
            var cur = entry
            var guard = graph.nodes.size + 8
            while (cur != null && cur != boundary) {
                if (guard-- <= 0) throw DslPrintException("ノード走査が上限を超えました（循環の可能性）")
                val n = graph.nodes[cur] ?: throw DslPrintException("不明なノード参照です: $cur")
                if (n.type == CommandType.FOR_END && endAtForEnd) return cur
                emitComments(n)
                cur = when (n.type) {
                    CommandType.MERGE -> n.next
                    CommandType.FOR_END -> throw DslPrintException("FOR_END が対応ループの外側に現れました")
                    CommandType.TEMP_SET -> { emitTempSet(n); n.next }
                    CommandType.VARIABLE -> { emitVariable(n); n.next }
                    CommandType.CONDITION -> emitIf(n)
                    CommandType.FOR_START -> emitRepeat(n)
                    // 終端文の next 辺は実行継続ではなく構造到達性のための接続です
                    // （ループ本体の末尾では FOR_END を指す）。ブロック走査はここで
                    // 打ち切り、境界の外側へ出ないようにします。
                    CommandType.BREAK -> { line("break;"); null }
                    CommandType.CONTINUE -> { line("continue;"); null }
                    CommandType.RETURN_VALUE -> { emitReturn(n); null }
                    CommandType.DISK_CALL -> { emitCallDisk(n); n.next }
                    CommandType.FOLDED_IF, CommandType.FOLDED_FOR ->
                        throw DslPrintException("折り畳みノードは DSL へ写像できません")
                    else -> { emitCommand(n); n.next }
                }
            }
            return cur
        }

        /** ノードへ付随する `//` コメントを文の直前へ出力します。 */
        private fun emitComments(n: CommandNode) {
            n.comment?.lines()?.forEach { text -> line(if (text.isEmpty()) "//" else "// $text") }
        }

        // ---- 変数（TEMP_SET / VARIABLE） ----

        private fun emitTempSet(n: CommandNode) {
            val name = n.string("name")
            val expr = tempSetExpr(n)
            if (name.startsWith(TEMP_PREFIX)) {
                // 生成一時変数は文として出さず、参照箇所で展開します。
                tempDefs[name] = expr
                return
            }
            emitExecWrite(n, name, expr)
        }

        /**
         * VARIABLE ノードの復元です。scope・operation の全組合せを DSL 表層へ写像します。
         * - EXECUTION: TEMP_SET と同じ変数表層（`var`/`x =`/`x op=`）
         * - WORLD: `world.x` 参照・`var world.x` 宣言・`world.x op=` 複合代入
         * - ENTITY: `<対象>.vars.<name> = ...`（DSL は entity 変数の書込みのみ表現）
         * - DELETE: `del <対象>;`
         */
        private fun emitVariable(n: CommandNode) {
            val scope = n.variableScope()
            val name = n.string("name")
            val op = VariableOperation.entries.firstOrNull { it.name == n.string("operation") }
            if (op == null) unsupported("VARIABLE の操作 '${n.string("operation")}'")
            when (op) {
                VariableOperation.DELETE -> {
                    val target = when (scope) {
                        VariableScope.EXECUTION -> varNameText(name)
                        VariableScope.WORLD -> "world.${varNameText(name)}"
                        VariableScope.ENTITY -> "${entityTargetText(n)}.vars.${varNameText(name)}"
                    }
                    line("del $target;")
                }
                VariableOperation.COPY -> {
                    // 複写は参照代入と同義のため `dst = src` へ正規化します。
                    // ENTITY の複写元は同一対象のエンティティ変数 `vars.<name>` です。
                    val src = n.string("source")
                    val srcText = when (scope) {
                        VariableScope.WORLD -> "world.${varNameText(src)}"
                        VariableScope.EXECUTION -> valueName(src)
                        VariableScope.ENTITY -> "vars.${dslName(src)}"
                    }
                    emitVarAssign(n, scope, name, srcText)
                }
                else -> when (scope) {
                    VariableScope.EXECUTION -> emitExecWrite(n, name, variableExpr(n))
                    VariableScope.WORLD -> {
                        val dsl = "world.${varNameText(name)}"
                        val calc = compoundAssign(n, name, scope)
                        when {
                            calc != null -> line("$dsl $calc;")
                            op == VariableOperation.DEFINE ->
                                line("var $dsl: ${typeName(n.string("type"))} = ${variableExpr(n)};")
                            else -> line("$dsl = ${variableExpr(n)};")
                        }
                    }
                    VariableScope.ENTITY -> {
                        if (n.string("changeMode") == VariableChangeMode.PROPERTY.name) {
                            // PROPERTY の参照元・引数は同一対象の entity 変数で、
                            // DSL では `vars.<name>` 名前空間で表します。
                            val src = n.string("propertySource")
                            val prop = n.string("property")
                            val arg = n.string("propertyArg").takeIf(String::isNotBlank)
                            val expr = "vars.${dslName(src)}.$prop" +
                                (arg?.let { "(vars.${dslName(it)})" } ?: "")
                            line("${entityTargetText(n)}.vars.${varNameText(name)} = $expr;")
                        } else {
                            line("${entityTargetText(n)}.vars.${varNameText(name)} = ${variableExpr(n)};")
                        }
                    }
                }
            }
        }

        /** 実行内変数への書き込み（TEMP_SET または VARIABLE・EXECUTION）です。 */
        private fun emitExecWrite(n: CommandNode, name: String, expr: String) {
            val dsl = varNameText(name)
            val calc = compoundAssign(n, name, VariableScope.EXECUTION)
            when {
                calc != null && dsl in declared -> line("$dsl $calc;")
                declared.add(dsl) ->
                    line("var $dsl: ${typeName(varTypeName(n))} = $expr;")
                else -> line("$dsl = $expr;")
            }
        }

        /** VARIABLE の COPY/SET を scope に応じた代入文へ写します。 */
        private fun emitVarAssign(n: CommandNode, scope: VariableScope, name: String, expr: String) {
            when (scope) {
                VariableScope.EXECUTION -> emitExecWrite(n, name, expr)
                VariableScope.WORLD -> line("world.${varNameText(name)} = $expr;")
                VariableScope.ENTITY -> line("${entityTargetText(n)}.vars.${varNameText(name)} = $expr;")
            }
        }

        /** ノードの宣言型名です。TEMP_SET は `tempType`、VARIABLE は `type` 欄です。 */
        private fun varTypeName(n: CommandNode): String =
            if (n.type == CommandType.TEMP_SET) n.string("tempType") else n.string("type")

        /** ENTITY scope の対象指定を対象式テキストへ戻します。 */
        private fun entityTargetText(n: CommandNode): String {
            // VARIABLE のエンティティ変数対象は targetSpec 欄です。
            val spec = n.targetSpec
                ?: unsupported("ENTITY 変数の対象が未設定です")
            return targetExpr(spec)
        }

        /**
         * CALCULATE の `<self> <op> <rhs>` 形を複合代入 `x <op>= rhs` へ畳み込みます。
         * 左辺の参照がこのノードの変数名と一致しない場合は null（通常代入）です。
         */
        private fun compoundAssign(n: CommandNode, name: String, scope: VariableScope): String? {
            if (n.string("changeMode") != VariableChangeMode.CALCULATE.name) return null
            val m = CALC_HEAD.matchEntire(n.string("value")) ?: return null
            val execName = m.groupValues[1]
            val worldName = m.groupValues[2]
            val op = m.groupValues[3]
            val rhs = m.groupValues[4]
            val isSelf = if (scope == VariableScope.EXECUTION) execName == name else worldName == name
            if (!isSelf) return null
            return "$op= ${expandRefs(rhs)}"
        }

        /** TEMP_SET／VARIABLE の値を式テキストへ復元します。 */
        private fun tempSetExpr(n: CommandNode): String = nodeExpr(n, n.string("tempType"))
        private fun variableExpr(n: CommandNode): String = nodeExpr(n, n.string("type"))

        private fun nodeExpr(n: CommandNode, typeName: String): String {
            return when (n.string("changeMode")) {
                VariableChangeMode.PROPERTY.name -> {
                    val src = valueName(n.string("propertySource"))
                    val prop = n.string("property")
                    val arg = n.string("propertyArg").takeIf(String::isNotBlank)
                    if (arg != null) "$src.$prop(${valueName(arg)})" else "$src.$prop"
                }
                VariableChangeMode.CALCULATE.name ->
                    // 式欄が純数値のときは他のスカラ経路と同じく正規形へ揃えます。
                    expandRefs(n.string("value")).let { it.toDoubleOrNull()?.let(::num) ?: it }
                VariableChangeMode.RANDOM.name -> randomExpr(n.string("range"))
                else -> assignExpr(n, typeName)
            }
        }

        /** `range` 欄の `a..b` を `random(min = a, max = b)` へ復元します。 */
        private fun randomExpr(range: String): String {
            val r = CommandValueRules.parseNumericRange(range)
                ?: unsupported("range '$range'")
            return "random(min = ${num(r.min)}, max = ${num(r.max)})"
        }

        /** ASSIGN の値（型別の構造値またはテンプレート文字列）を式へ復元します。 */
        private fun assignExpr(n: CommandNode, typeName: String): String {
            // 値が空の型付き変数は `null`（値未設定）へ写像します。string の
            // 空文字は意味のある値なので `""` のまま残します。
            if (n.string("value").isBlank() && typeName != "STRING") return "null"
            return when (typeName) {
            "LOCATION" -> {
                val spec = n.temporaryLocationPositionSpec
                    ?: return quoteTemplate(n.string("value"))
                positionExpr(spec)
            }
            "ENTITY" -> {
                val spec = n.temporaryEntityTargetSpec
                    ?: return quoteTemplate(n.string("value"))
                targetExpr(spec)
            }
            "ITEM" -> {
                val ref = n.itemTempRef
                if (ref != null) valueName(ref)
                else itemCtor(n.string("item"), n.string("itemData").ifBlank { null })
            }
            "BLOCK" -> {
                val ref = n.blockTempRef
                if (ref != null) valueName(ref) else "block(id = \"${escape(n.string("block"))}\")"
            }
            "STRING" -> quoteTemplate(n.string("value"))
            "BOOLEAN" -> scalarText(n.string("value"))
            else -> scalarText(n.string("value"))
            }
        }

        // ---- if 文 ----

        private fun emitIf(head: CommandNode): UUID? {
            val region = analyzeRegion(head)
            val (expr, _) = condExpr(head.id, region)
            return emitIfTail(expr, region.thenEntry, region.elseEntry, elseIf = false)
        }

        /**
         * `if (expr) { then } else { else }` を出力し、合流後のカーソルを返します。
         * then が空で else があれば `if (!(expr))` へ反転して正規形にします。
         */
        private fun emitIfTail(expr: String, thenEntry: UUID?, elseEntry: UUID?, elseIf: Boolean): UUID? {
            val join = resolveJoin(thenEntry, elseEntry)
            val keyword = if (elseIf) "} else if" else "if"
            // 枝の出口 null は「その極性でプログラムを終了」という意味です。
            // DSL では明示の `return;` へ写像するため、合流直行の空枝とは区別します。
            val thenHalt = thenEntry == null
            val elseHalt = elseEntry == null
            // ループ本体内では空の枝が FOR_END を直接指すことがあります。
            // これは分岐先がループ境界そのものであり、中身のない枝として扱います。
            val thenEmpty = !thenHalt &&
                (thenEntry == join || graph.nodes[thenEntry]?.type == CommandType.FOR_END)
            val elseEmpty = !elseHalt &&
                (elseEntry == join || graph.nodes[elseEntry]?.type == CommandType.FOR_END)
            // 両枝に内容があり条件が全体否定なら、正準形として枝を入れ替えて
            // 先頭の `!` を外します（`if (!e) {A} else {B}` ≡ `if (e) {B} else {A}`）。
            var outExpr = expr
            var outThen = thenEntry
            var outElse = elseEntry
            var outThenHalt = thenHalt
            var outElseHalt = elseHalt
            if (!thenEmpty && !elseEmpty) {
                stripLeadingNot(expr)?.let {
                    outExpr = it
                    outThen = elseEntry
                    outElse = thenEntry
                    outThenHalt = elseHalt
                    outElseHalt = thenHalt
                }
            }
            if (thenEmpty) {
                // then が空なら else を本体へ反転します。else が終了なら
                // `if (!e) { return }`（以後の文は到達しない）へ畳み込みます。
                when {
                    outElseHalt -> {
                        val inv = invertExpr(outExpr)
                        line("$keyword ($inv) {")
                        indent++
                        line("return;")
                        indent--
                        line("}")
                    }
                    !elseEmpty -> {
                        val inv = invertExpr(outExpr)
                        line("$keyword ($inv) {")
                        indent++
                        emitBlock(outElse, join)
                        indent--
                        line("}")
                    }
                    else -> {
                        line("$keyword ($outExpr) {")
                        line("}")
                    }
                }
                return join
            }
            line("$keyword ($outExpr) {")
            indent++
            // 片側が終了する形では合流が存在しないため、then 側は領域の
            // 自然な終端（FOR_END／プログラム終端）までを出力します。
            var stop = join
            if (outThenHalt) line("return;")
            else if (join == null) stop = emitBlock(outThen, null, endAtForEnd = true)
            else emitBlock(outThen, join)
            indent--
            if (outElseHalt) {
                line("} else {")
                indent++
                line("return;")
                indent--
                line("}")
                return stop
            }
            if (!elseEmpty) {
                // else 節が if 文1つだけなら `else if` へ畳み込みます。
                // 先頭が実体化用の一時変数なら条件の直前準備なので読み飛ばします。
                val firstElse = firstRealNode(outElse)
                if (firstElse != null && firstElse.type == CommandType.CONDITION) {
                    val innerRegion = analyzeRegion(firstElse)
                    val innerJoin = resolveJoin(innerRegion.thenEntry, innerRegion.elseEntry)
                    if (innerJoin == join) {
                        val (innerExpr, _) = condExpr(firstElse.id, innerRegion)
                        return emitIfTail(innerExpr, innerRegion.thenEntry, innerRegion.elseEntry, elseIf = true)
                    }
                }
                line("} else {")
                indent++
                emitBlock(outElse, join)
                indent--
                line("}")
            } else {
                line("}")
            }
            return join
        }

        /**
         * 式全体を覆う先頭の否定 `!` を取り除きます。
         * `!x`・`!f(…)`・`!(式)` の形だけを対象とし、部分否定や
         * 二項演算の片側だけに掛かる `!` は対象外です。
         */
        private fun stripLeadingNot(expr: String): String? {
            if (!expr.startsWith("!")) return null
            val rest = expr.substring(1)
            val open = rest.indexOf('(')
            return when {
                open >= 0 && rest.endsWith(")") && parensCover(rest.substring(open)) ->
                    if (open == 0) rest.substring(1, rest.length - 1) else rest
                open < 0 && SIMPLE_OPERAND.matches(rest) -> rest
                else -> null
            }
        }

        /** 先頭の `(` が末尾の `)` と対になり文字列全体を覆うかを判定します。 */
        private fun parensCover(s: String): Boolean {
            var depth = 0
            var inString = false
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (inString) {
                    if (c == '\\') i++
                    else if (c == '"') inString = false
                } else when (c) {
                    '"' -> inString = true
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth <= 0 && i != s.lastIndex) return false
                    }
                }
                i++
            }
            return depth == 0 && s.startsWith("(")
        }

        /**
         * 条件式の先頭条件から、true/false 出口が一意に定まる条件領域を切り出します。
         * 出口が既存の出口と衝突する条件（else-if の入口）は領域に含めません。
         */
        /**
         * 条件式の先頭条件から、true/false 出口が一意に定まる条件領域を切り出します。
         *
         * 第1段階では「各極性の出口は高々1箇所」の不変条件で領域を拡大し、
         * 出口が衝突する条件（else-if 連結の入口など）は領域へ含めません。
         * 第2段階で領域内条件の領域外への枝から then/else の入口を確定します。
         */
        private fun analyzeRegion(head: CommandNode): Region {
            val conds = LinkedHashMap<UUID, CommandNode>()
            val queue = ArrayDeque<UUID>()
            var tExit: UUID? = null
            var fExit: UUID? = null
            var tSeen = false
            var fSeen = false
            queue += head.id
            while (queue.isNotEmpty()) {
                val c = graph.nodes[queue.removeFirst()] ?: continue
                if (c.id in conds) continue
                val t = followCondEdge(c.trueNext)
                val f = followCondEdge(c.falseNext)
                val tExitNew = if (t.isCond) null else c.trueNext
                val fExitNew = if (f.isCond) null else c.falseNext
                val tBad = tExitNew != null && tSeen && tExitNew != tExit
                val fBad = fExitNew != null && fSeen && fExitNew != fExit
                if (tBad || fBad) continue
                conds[c.id] = c
                if (tExitNew != null) { tSeen = true; tExit = tExitNew }
                if (fExitNew != null) { fSeen = true; fExit = fExitNew }
                if (t.isCond) queue += t.id!!
                if (f.isCond) queue += f.id!!
            }
            // 第2段階: 領域内の条件から領域外（または拒否条件）へ出る枝の
            // 行き先を then/else の枝入口として確定します。
            val region = Region(conds)
            for (c in conds.values) {
                for ((edge, isTrue) in listOf(c.trueNext to true, c.falseNext to false)) {
                    val followed = followCondEdge(edge)
                    if (followed.isCond && followed.id in conds) continue
                    if (isTrue) {
                        if (region.thenEntry == null) region.thenEntry = edge
                        else if (region.thenEntry != edge) {
                            throw DslPrintException("条件の真側の出口が一意に定まりません")
                        }
                    } else {
                        if (region.elseEntry == null) region.elseEntry = edge
                        else if (region.elseEntry != edge) {
                            throw DslPrintException("条件の偽側の出口が一意に定まりません")
                        }
                    }
                }
            }
            return region
        }

        /**
         * `dsl.tmpN` の実体化ノードを飛ばして、枝の最初の実ノードを返します。
         * 飛ばした定義は記録するため、else-if 畳み込みで枝入口の実体化ノードを
         * 素通りしても参照先が解決できます。
         */
        private fun firstRealNode(entry: UUID?): CommandNode? {
            var cur = entry
            var guard = graph.nodes.size + 8
            while (cur != null) {
                if (guard-- <= 0) throw DslPrintException("枝先の走査が上限を超えました")
                val n = graph.nodes[cur] ?: return null
                if (n.type == CommandType.TEMP_SET && n.string("name").startsWith(TEMP_PREFIX)) {
                    tempDefs.getOrPut(n.string("name")) { tempSetExpr(n) }
                    cur = n.next
                    continue
                }
                return n
            }
            return null
        }

        /**
         * 条件の枝先を、実体化用一時変数ノードを飛ばしてたどります。
         * 途中の `dsl.tmpN` 定義は収集し、戻り値は枝の実質的な行き先です。
         */
        private fun followCondEdge(raw: UUID?): EdgeTarget {
            var cur = raw
            var guard = graph.nodes.size + 8
            while (true) {
                if (guard-- <= 0) throw DslPrintException("条件枝の走査が上限を超えました")
                val n = graph.nodes[cur] ?: return EdgeTarget(cur, false)
                if (n.type == CommandType.CONDITION) return EdgeTarget(cur, true)
                if (n.type == CommandType.MERGE) return EdgeTarget(cur, false)
                if (n.type == CommandType.TEMP_SET && n.string("name").startsWith(TEMP_PREFIX)) {
                    tempDefs.getOrPut(n.string("name")) { tempSetExpr(n) }
                    cur = n.next
                    continue
                }
                return EdgeTarget(cur, false)
            }
        }

        /** 条件式の枝先を「then 出口・else 出口・領域内条件」のいずれかへ分類します。 */
        private fun classify(region: Region, raw: UUID?): CondTarget {
            val t = followCondEdge(raw)
            if (t.isCond && t.id in region.conds) return CondTarget.Next(t.id!!)
            return when (raw) {
                region.thenEntry -> CondTarget.Then
                region.elseEntry -> CondTarget.Else
                else -> CondTarget.Other(raw)
            }
        }

        /**
         * 条件領域を DSL の boolean 式へ復元します。
         * 短絡評価の木構造を `&&`/`||`/`!` の式へ畳み込み、
         * 領域外の複雑な分岐は Shannon 展開で冗長でも正確な式にします。
         */
        private fun condExpr(condId: UUID, region: Region): Pair<String, CondTopOp?> {
            val c = region.conds[condId] ?: throw DslPrintException("条件ノードが領域にありません")
            val t = classify(region, c.trueNext)
            val f = classify(region, c.falseNext)
            val leaf = leafExpr(c)
            return when {
                t is CondTarget.Then && f is CondTarget.Else -> leaf to null
                t is CondTarget.Else && f is CondTarget.Then -> invertLeaf(leaf) to null
                t is CondTarget.Next && f is CondTarget.Else -> {
                    val (e, op) = condExpr(t.id, region)
                    val rhs = if (op == CondTopOp.OR) "($e)" else e
                    "$leaf && $rhs" to CondTopOp.AND
                }
                t is CondTarget.Next && f is CondTarget.Then -> {
                    val (e, op) = condExpr(t.id, region)
                    val rhs = if (op == CondTopOp.AND) "($e)" else e
                    "${invertLeaf(leaf)} || $rhs" to CondTopOp.OR
                }
                f is CondTarget.Next && t is CondTarget.Then -> {
                    val (e, op) = condExpr(f.id, region)
                    val rhs = if (op == CondTopOp.AND) "($e)" else e
                    "$leaf || $rhs" to CondTopOp.OR
                }
                f is CondTarget.Next && t is CondTarget.Else -> {
                    val (e, op) = condExpr(f.id, region)
                    val rhs = if (op == CondTopOp.OR) "($e)" else e
                    "${invertLeaf(leaf)} && $rhs" to CondTopOp.AND
                }
                t is CondTarget.Next && f is CondTarget.Next -> {
                    // 一般形: (L && E_t) || (!L && E_f) の Shannon 展開です。
                    val (et, _) = condExpr(t.id, region)
                    val (ef, _) = condExpr(f.id, region)
                    "($leaf && ($et)) || (${invertLeaf(leaf)} && ($ef))" to CondTopOp.OR
                }
                else -> throw DslPrintException("条件の分岐構造を式へ復元できません")
            }
        }

        // ---- repeat 文 ----

        private fun emitRepeat(start: CommandNode): UUID? {
            val endId = start.pairedNodeId
            val end = graph.nodes[endId]
            if (end == null || end.type != CommandType.FOR_END) {
                throw DslPrintException("FOR_START の対向ノードが FOR_END ではありません")
            }
            // `repeat(null)` は回数未設定の FOR_START です。
            val raw = start.string("count")
            val count = if (raw.isBlank()) "null" else scalarText(raw)
            line("repeat($count) {")
            indent++
            val bodyEntry = start.trueNext
            if (bodyEntry != end.id) emitBlock(bodyEntry, end.id)
            indent--
            line("}")
            return end.next
        }

        // ---- 終端・呼出し ----

        private fun emitReturn(n: CommandNode) {
            val source = n.string("source")
            if (source.isBlank()) {
                // 変数参照の無い return はリテラル戻り値か、値なし終了です。
                val literal = n.string("value")
                if (literal.isBlank()) line("return;")
                else if (me.awabi2048.kantancommander.model.inferLiteralType(literal) == ValueType.NUMBER) {
                    line("return ${scalarText(literal)};")
                } else {
                    line("return ${quoteTemplate(literal)};")
                }
                return
            }
            val scope = VariableScope.parse(n.string("sourceScope")) ?: VariableScope.EXECUTION
            val expr = when (scope) {
                VariableScope.WORLD -> worldRef(source)
                else -> valueName(source)
            }
            line("return $expr;")
        }

        /**
         * DISK_CALL を `call` へ復元します。
         *
         * 引数は被呼出し側の宣言（`declaredArgs`）の順で、`argvar_` は変数参照、
         * `arg_` は宣言型に従うリテラル・テンプレートとして読みます。宣言に
         * 存在するが未設定の引数は `= null` として残し、宣言外に残った古い
         * 引数欄（arg_/argvar_）も後続に保持します。戻り値の受取欄
         * （returnName/returnScope）があれば `<変数> = call ".."` へ写像します。
         */
        private fun emitCallDisk(n: CommandNode) {
            val rawId = n.string("diskId")
            val name = runCatching { UUID.fromString(rawId) }.getOrNull()
                ?.let(diskNameResolver) ?: rawId
            val declared = n.declaredArguments()
            val declaredNames = declared.mapTo(mutableSetOf()) { it.name }
            val args = mutableListOf<String>()
            for (argument in declared) {
                val label = dslName(argument.name)
                val variable = n.string("argvar_${argument.name}").takeIf(String::isNotBlank)
                val literal = n.string("arg_${argument.name}").takeIf(String::isNotBlank)
                args += "$label = " + when {
                    variable != null -> valueName(variable)
                    literal != null -> when (argument.type) {
                        ValueType.STRING -> quoteTemplate(literal)
                        else -> scalarText(literal)
                    }
                    else -> "null"
                }
            }
            // 宣言に存在しない arg_/argvar_ 欄は被呼出し側の引数改名等で残る
            // 古いデータです。捨てずに名前順で保持します。
            for (key in n.params.keys.sorted()) {
                val (isVariable, argName) = when {
                    key.startsWith("argvar_") -> true to key.removePrefix("argvar_")
                    key.startsWith("arg_") -> false to key.removePrefix("arg_")
                    else -> continue
                }
                if (argName in declaredNames) continue
                val raw = n.string(key).takeIf(String::isNotBlank) ?: continue
                args += "${dslName(argName)} = ${if (isVariable) valueName(raw) else scalarText(raw)}"
            }
            val receiver = n.string("returnName").takeIf(String::isNotBlank)?.let { r ->
                if (VariableScope.parse(n.string("returnScope")) == VariableScope.WORLD) {
                    worldRef(r)
                } else {
                    dslName(r)
                }
            }
            val argsText = if (args.isEmpty()) "" else "(${args.joinToString(", ")})"
            val call = "call \"${escape(name)}\"$argsText"
            line(if (receiver != null) "$receiver = $call;" else "$call;")
        }

        // ---- 命令ノード ----

        /** CommandType → DSL 命令の逆引き表です（固定引数で同名型を分岐）。 */
        private val inverseCommands: Map<CommandType, List<Pair<String, CommandSpec>>> by lazy {
            DSL_COMMAND_TABLE.entries.groupBy { it.value.type }.mapValues { e ->
                e.value.map { it.key to it.value }
            }
        }

        private fun emitCommand(n: CommandNode) {
            val specs = inverseCommands[n.type]
                ?: throw DslPrintException("DSL へ写像できない命令ノードです: ${n.type}")
            val (name, spec) = when (specs.size) {
                1 -> specs.single()
                else -> specs.firstOrNull { (_, s) ->
                    s.fixedParams.all { (k, v) -> n.string(k) == v }
                } ?: specs.first()
            }
            val args = mutableListOf<String>()
            for ((argName, rule) in spec.args) {
                for (text in renderArg(n, argName, rule)) {
                    args += "$argName = $text"
                }
            }
            // `summon(entity=..., <項目>=<値>)` の型付きデータ欄を引数として復元します。
            spec.extraParamPrefix?.let { prefix ->
                for ((key, raw) in n.params.toSortedMap()) {
                    if (!key.startsWith(prefix)) continue
                    val id = key.removePrefix(prefix)
                    val kind = me.awabi2048.kantancommander.model.EntityDataSchema.definition(id)
                        ?.valueKind
                    args += "$id = ${summonValueText(raw, kind)}"
                }
            }
            // 唯一の必須引数だけなら位置引数へ正規化します（wait(1) 等）。
            val required = spec.args.entries.singleOrNull { it.value.required }
            val rendered = if (required != null && args.size == 1 && args.single().startsWith("${required.key} = ")) {
                "$name(${args.single().removePrefix("${required.key} = ")})"
            } else {
                "$name(${args.joinToString(", ")})"
            }
            line("$rendered;")
        }

        /**
         * 命令の1引数をノード欄・構造値から読み、DSL の引数テキストへ戻します。
         * 戻り値は0個以上です（TAG_OP は複数エントリを1引数ずつ返します）。
         */
        private fun renderArg(n: CommandNode, argName: String, rule: ArgRule): List<String> {
            val single: String? = when (rule.kind) {
                ArgKind.NUMBER -> n.string(rule.param!!).takeIf(String::isNotBlank)?.let { v ->
                    // 数値欄の保存形は `1.0` のように浮動小数表記を含むため、
                    // 純数値は正規化して canonical 形へ揃えます。
                    val expanded = expandRefs(v)
                    expanded.toDoubleOrNull()?.let(::num) ?: expanded
                }
                ArgKind.BOOLEAN -> n.string(rule.param!!).takeIf(String::isNotBlank)?.let(::scalarText)
                ArgKind.STRING -> {
                    // `__dslValue` は entity_operation の value の仮想欄です。
                    // 実保存先はパス種別で propertyValue／attributeValue に分かれます。
                    val raw = if (rule.param == "__dslValue") {
                        when {
                            n.string("entityDataPath").startsWith("property:") -> n.string("propertyValue")
                            n.string("entityDataPath").startsWith("attribute:") -> n.string("attributeValue")
                            else -> ""
                        }
                    } else n.string(rule.param!!)
                    // 空欄は未設定であり、必須欄では末尾の `= null` 変換で表します。
                    if (raw.isBlank()) null else quoteTemplate(raw)
                }
                ArgKind.LITERAL -> {
                    val v = n.string(rule.param!!)
                    val isDefault = v == n.type.defaults[rule.param]
                    if (v.isBlank()) null
                    else if (!rule.required && isDefault) null
                    else "\"${escape(v.lowercaseIfEnum(rule))}\""
                }
                ArgKind.SLOT -> n.string(rule.param!!).takeIf(String::isNotBlank)?.let { v ->
                    // slot 欄は数値（40）と名前付きスロット（HAND 等）の両方を取ります。
                    if (v.toIntOrNull() != null) v else "\"${escape(v.lowercase())}\""
                }
                ArgKind.PATH_PROPERTY ->
                    n.string("entityDataPath").removePrefix("property:")
                        .takeIf { n.string("entityDataPath").startsWith("property:") }
                        ?.let { "\"${escape(it)}\"" }
                ArgKind.PATH_ATTRIBUTE ->
                    n.string("entityDataPath").removePrefix("attribute:")
                        .takeIf { n.string("entityDataPath").startsWith("attribute:") }
                        ?.let { "\"${escape(it)}\"" }
                ArgKind.TAG_OP -> return tagOpValues(n, rule.param!!).map { "\"${escape(it)}\"" }
                ArgKind.ENTITY -> specOf(n, rule.slot!!)
                    ?.let { (it as? TargetSpec)?.let(::targetExpr) }
                ArgKind.LOCATION -> when (rule.slot) {
                    ArgSlot.DEST_FACING -> n.destinationFacingSpec?.let(::facingExpr)
                    ArgSlot.DESTINATION -> n.destinationTargetSpec?.let(::targetExpr)
                        ?: (n.destinationSpec)?.let(::positionExpr)
                    else -> (specOf(n, rule.slot!!) as? PositionSpec)?.let(::positionExpr)
                }
                ArgKind.ITEM -> {
                    val ref = n.itemTempRef
                    if (ref != null) valueName(ref)
                    else {
                        val id = n.string("item")
                        if (id.isBlank() && !rule.required) null
                        else itemCtor(id, n.string("itemData").ifBlank { null })
                    }
                }
                ArgKind.BLOCK -> {
                    val ref = n.blockTempRef
                    if (ref != null) valueName(ref)
                    else {
                        val id = n.string("block")
                        if (id.isBlank() && !rule.required) null
                        else "block(id = \"${escape(id)}\")"
                    }
                }
            }
            // 必須だが未設定の欄は `= null` で表します（GUI の半設定状態と等価）。
            if (single == null && rule.required) return listOf("null")
            return listOfNotNull(single)
        }

        /**
         * LITERAL 値の正規化です。enumValues／uppercase 指定のある欄はモデル側で
         * 大文字保存されるため DSL 表記（小文字）へ戻します。
         */
        private fun String.lowercaseIfEnum(rule: ArgRule): String =
            if (rule.uppercase || rule.enumValues != null) lowercase() else this

        /**
         * `tagEntries`（`add=a;remove=b` 形）から `op` に合致するタグ値を列挙します。
         * `tag` パス指定時の未分類形式（`tagEntries` が `=` を含まない）は
         * `tag_add` として扱います（旧保存形式）。
         */
        private fun tagOpValues(n: CommandNode, op: String): List<String> {
            val raw = n.string("tagEntries")
            if (raw.isBlank()) return emptyList()
            val entries = raw.split(";").map(String::trim).filter(String::isNotEmpty)
            return entries.mapNotNull { entry ->
                val eq = entry.indexOf('=')
                if (eq < 0) {
                    // `tag` パスのみの旧形（値がタグ名そのもの）。
                    if (op == "add") entry else null
                } else {
                    val (k, v) = entry.substring(0, eq) to entry.substring(eq + 1)
                    if (k == op) v else null
                }
            }
        }

        /** `summonData_<id>` の値を引数リテラルへ戻します。 */
        private fun summonValueText(raw: String, kind: me.awabi2048.kantancommander.model.EntityPropertyValueKind?): String =
            when (kind) {
                me.awabi2048.kantancommander.model.EntityPropertyValueKind.BOOLEAN ->
                    raw.lowercase().let { if (it == "true" || it == "false") it else "\"${escape(raw)}\"" }
                me.awabi2048.kantancommander.model.EntityPropertyValueKind.INTEGER ->
                    raw.toDoubleOrNull()?.let(::num) ?: "\"${escape(raw)}\""
                else -> "\"${escape(raw)}\""
            }

        private fun specOf(n: CommandNode, slot: ArgSlot): Any? = when (slot) {
            ArgSlot.TARGET -> n.targetSpec
            ArgSlot.SECONDARY_TARGET -> n.secondaryTargetSpec
            ArgSlot.DESTINATION -> n.destinationSpec
            ArgSlot.DEST_TARGET -> n.destinationTargetSpec
            ArgSlot.DEST_FACING -> n.destinationFacingSpec
            ArgSlot.COND_POS -> n.conditionPositionSpec
            ArgSlot.EXEC_POS -> n.executionPositionSpec
            ArgSlot.SOUND_POS -> n.soundPositionSpec
            ArgSlot.SUMMON_POS -> n.summonPositionSpec
            ArgSlot.PARTICLE_POS -> n.particlePositionSpec
            ArgSlot.PARTICLE_END -> n.particleEndPositionSpec
            ArgSlot.BLOCK_POS -> n.blockPositionSpec
            ArgSlot.BLOCK_FROM -> n.blockFromSpec
            ArgSlot.BLOCK_TO -> n.blockToSpec
            ArgSlot.FIREWORK_POS -> n.fireworkPositionSpec
            ArgSlot.STRUCTURE_POS -> n.structurePlacePositionSpec
            ArgSlot.GROUP_PIVOT -> n.groupPivotPositionSpec
        }

        // ---- 式の葉の復元 ----

        /**
         * CONDITION の葉を DSL 式へ復元します。
         * `inverted` 欄は式側の否定へ畳み込みます（BLOCK は `!=`、それ以外は `!` 前置）。
         */
        private fun leafExpr(c: CommandNode): String {
            val inverted = c.boolean("inverted")
            val base = when (c.string("subject")) {
                // 条件 subject 未設定は `null` 条件へ写像します。
                "" -> "null"
                ConditionSubject.VARIABLE.name -> {
                    // 左辺の変数が未設定の条件は比較形を構成できないため、
                    // 条件全体を `null`（未設定）へ写像します。
                    if (c.string("variable").isBlank()) {
                        return "null"
                    }
                    val lhs = conditionLhs(c.string("variable"))
                    val op = c.string("operator", "==")
                    val rhs = conditionValue(c.string("value"))
                    // `X == true` は `X`、`X != true` は `!X` に正規化します。
                    when {
                        rhs == "true" && op == "==" -> lhs
                        rhs == "true" && op == "!=" -> "!$lhs"
                        rhs == "false" && op == "==" -> "!$lhs"
                        rhs == "false" && op == "!=" -> lhs
                        else -> "$lhs $op $rhs"
                    }
                }
                ConditionSubject.BLOCK.name -> {
                    val spec = c.conditionPositionSpec
                        ?: throw DslPrintException("BLOCK 判定の位置が未設定です")
                    val op = if (inverted) "!=" else "=="
                    return "${positionExpr(spec)}.block $op block(id = \"${escape(c.string("block"))}\")"
                }
                ConditionSubject.PLAYER.name -> playerCondText(c)
                ConditionSubject.OTHER_ENTITY.name ->
                    "exists(${condArgsText(c, c.targetSpec)})"
                ConditionSubject.CONTROL_BLOCK.name -> signalCondText(c)
                else -> throw DslPrintException("DSL へ写像できない条件対象です: ${c.string("subject")}")
            }
            return if (inverted) invertLeaf(base) else base
        }

        /** `player(...)` のプレイヤー判定を復元します。対象＋sneaking/item 補足です。 */
        private fun playerCondText(c: CommandNode): String {
            // 対象未設定は `target = null` として表します。
            val args = mutableListOf(
                c.targetSpec?.let(::condTargetText) ?: "target = null"
            )
            c.string("sneaking").takeIf(String::isNotBlank)?.let {
                args += "sneaking = $it"
            }
            val item = c.string("item")
            if (item.isNotBlank()) {
                args += "item = ${itemCtor(item, c.string("itemData").ifBlank { null })}"
            }
            c.executionPositionSpec?.let { args += "at = ${positionExpr(it)}" }
            return "player(${args.joinToString(", ")})"
        }

        /**
         * `exists(...)` の引数列です。対象指定に加え、判定位置（`at`）が
         * 設定されていれば末尾へ付けます。対象未設定は `target = null` です。
         */
        private fun condArgsText(c: CommandNode, spec: TargetSpec?): String {
            val args = mutableListOf(spec?.let(::condTargetText) ?: "target = null")
            c.executionPositionSpec?.let { args += "at = ${positionExpr(it)}" }
            return args.joinToString(", ")
        }

        /** `signal(...)` の制御ブロック信号判定を復元します。 */
        private fun signalCondText(c: CommandNode): String {
            val raw = c.string("redstoneSignal").trim()
            // 強度範囲の未設定は `signal(null)` で表します。
            if (raw.isBlank()) return "signal(null)"
            val spec = c.redstoneSignalSpec()
                ?: throw DslPrintException("CONTROL_BLOCK 判定の信号範囲が未設定です")
            return when {
                spec.first == spec.last && !raw.contains("..") -> "signal(${spec.first})"
                spec.first == 0 && raw.startsWith("..") -> "signal(max = ${spec.last})"
                spec.last == CommandValueRules.SIGNAL_STRENGTH_MAX && raw.endsWith("..") ->
                    "signal(min = ${spec.first})"
                else -> "signal(min = ${spec.first}, max = ${spec.last})"
            }
        }

        /** 条件左辺の `%{x}%`（実行内変数）・plain 名（ワールド変数）を式へ戻します。 */
        private fun conditionLhs(raw: String): String {
            // 変数名未設定の条件は `null` を左辺に置きます。
            if (raw.isBlank()) return "null"
            if (TemporaryTemplate.isSingleReference(raw)) {
                val name = TemporaryTemplate.references(raw).single()
                return valueName(name)
            }
            return worldRef(raw)
        }

        /** 条件右辺の値欄を式テキストへ戻します。文字列等値は引用符を付与します。 */
        private fun conditionValue(raw: String): String {
            val expanded = expandRefs(raw)
            return when {
                raw.isBlank() -> "\"\""
                expanded == "true" || expanded == "false" -> expanded
                expanded.toDoubleOrNull() != null -> expanded
                TemporaryTemplate.isSingleReference(raw) -> expanded
                else -> "\"${escapeExpanded(expanded)}\""
            }
        }

        /** 葉式の否定です。比較演算は否定形の演算子へ反転します。 */
        private fun invertLeaf(leaf: String): String {
            val m = Regex("^(.*) (==|!=|<|<=|>|>=) (.*)$").find(leaf)
            if (m != null) {
                val (l, op, r) = m.destructured
                val inv = when (op) {
                    "==" -> "!="; "!=" -> "=="; "<" -> ">="; "<=" -> ">"
                    ">" -> "<="; ">=" -> "<"; else -> op
                }
                return "$l $inv $r"
            }
            return if (leaf.startsWith("!")) leaf.removePrefix("!") else "!$leaf"
        }

        /**
         * 式全体の否定です。変数名・プロパティ参照・リテラル等の単純オペランドは
         * `!x` とし、二項演算を含む式だけ括弧で包みます。
         */
        private fun invertExpr(expr: String): String =
            if (SIMPLE_OPERAND.matches(expr)) "!$expr" else "!($expr)"

        // ---- 合流点の解決 ----

        /** then/else 両枝が最初に収束するノード（合流）を返します。 */
        private fun resolveJoin(thenEntry: UUID?, elseEntry: UUID?): UUID? {
            val thenReach = reachable(thenEntry)
            if (elseEntry == null || elseEntry in thenReach) return elseEntry
            val seen = mutableSetOf<UUID>()
            val queue = ArrayDeque<UUID>()
            queue += elseEntry
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                if (!seen.add(id)) continue
                if (id in thenReach) return id
                val n = graph.nodes[id] ?: continue
                listOfNotNull(n.next, n.trueNext, n.falseNext).forEach(queue::add)
            }
            return null
        }

        /** ノードから到達可能な全ノード ID の集合です。 */
        private fun reachable(from: UUID?): Set<UUID> {
            if (from == null) return emptySet()
            val seen = mutableSetOf<UUID>()
            val queue = ArrayDeque<UUID>()
            queue += from
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                if (!seen.add(id)) continue
                val n = graph.nodes[id] ?: continue
                listOfNotNull(n.next, n.trueNext, n.falseNext).forEach { if (it !in seen) queue += it }
            }
            return seen
        }

        // ---- 構造値の復元 ----

        /** PositionSpec を `location(...)`・`~`/`^` リテラル・変数参照へ復元します。 */
        private fun positionExpr(spec: PositionSpec): String = when (spec.kind) {
            PositionKind.TEMPORARY -> {
                val name = valueName(spec.tempName ?: "")
                if (spec.x == null && spec.y == null && spec.z == null &&
                    spec.yaw == null && spec.pitch == null
                ) {
                    name
                } else {
                    "$name ${component(spec.x, spec.semanticsX)} ${component(spec.y, spec.semanticsY)} ${component(spec.z, spec.semanticsZ)}"
                }
            }
            PositionKind.COORDINATES -> {
                val relative = listOf(spec.semanticsX, spec.semanticsY, spec.semanticsZ)
                    .any { it != CoordinateSemantics.ABSOLUTE }
                val relRot = spec.semanticsYaw != CoordinateSemantics.ABSOLUTE ||
                    spec.semanticsPitch != CoordinateSemantics.ABSOLUTE
                val hasRot = spec.yaw != null || spec.pitch != null
                when {
                    !relative && !hasRot ->
                        "location(x = ${num(spec.x)}, y = ${num(spec.y)}, z = ${num(spec.z)})"
                    !relative && !relRot ->
                        "location(x = ${num(spec.x)}, y = ${num(spec.y)}, z = ${num(spec.z)}, yaw = ${num(spec.yaw)}, pitch = ${num(spec.pitch)})"
                    hasRot -> {
                        // 相対成分と角度の混在は位置リテラルで表せないため、
                        // 各成分を `~`/`^` 付き引数として location(...) へ展開します。
                        listOf(
                            "x = ${component(spec.x, spec.semanticsX)}",
                            "y = ${component(spec.y, spec.semanticsY)}",
                            "z = ${component(spec.z, spec.semanticsZ)}",
                            "yaw = ${component(spec.yaw, spec.semanticsYaw)}",
                            "pitch = ${component(spec.pitch, spec.semanticsPitch)}",
                        ).joinToString(", ", prefix = "location(", postfix = ")")
                    }
                    else ->
                        "${component(spec.x, spec.semanticsX)} ${component(spec.y, spec.semanticsY)} ${component(spec.z, spec.semanticsZ)}"
                }
            }
            PositionKind.DISK -> specialPositionExpr("disk", spec)
            PositionKind.CAPTURED -> specialPositionExpr("captured", spec)
            PositionKind.MYWORLD_SPAWN -> when (spec.myworldSpawn ?: MyworldSpawnKind.MEMBER) {
                MyworldSpawnKind.MEMBER -> "myworld_spawn()"
                MyworldSpawnKind.GUEST -> "myworld_spawn(kind = \"guest\")"
            }
            PositionKind.TARGET -> {
                val target = spec.target ?: unsupported("対象位置の指定が未設定です")
                if (target.kind == TargetKind.TEMPORARY) {
                    unsupported("対象位置の変数参照")
                }
                "target(${targetArgs(target).joinToString(", ")})"
            }
        }

        /**
         * `disk`/`captured` の特殊位置を復元します。成分を持つ場合は
         * 位置リテラル形（`captured ~1 ~0 ~0`）へ、角度成分は表層に無いため拒否します。
         */
        private fun specialPositionExpr(name: String, spec: PositionSpec): String {
            if (spec.yaw != null || spec.pitch != null) unsupported("$name の角度成分")
            val hasComponents = spec.x != null || spec.y != null || spec.z != null
            if (!hasComponents) return name
            return "$name ${component(spec.x, spec.semanticsX)} ${component(spec.y, spec.semanticsY)} ${component(spec.z, spec.semanticsZ)}"
        }

        /** 位置リテラルの1成分を `~`/`^`/絶対値へ復元します。 */
        private fun component(value: Number?, semantics: CoordinateSemantics): String {
            val v = num(value)
            return when (semantics) {
                CoordinateSemantics.ABSOLUTE -> v
                CoordinateSemantics.RELATIVE -> "~$v"
                CoordinateSemantics.LOCAL -> "^$v"
            }
        }

        /** TargetSpec を `target(...)`・変数参照へ復元します。 */
        private fun targetExpr(spec: TargetSpec): String {
            if (spec.kind == TargetKind.TEMPORARY) return valueName(spec.tempName ?: "")
            return "target(${targetArgs(spec).joinToString(", ")})"
        }

        /** TargetSpec の絞り込み引数列です（kind 以降、宣言順）。 */
        private fun targetArgs(spec: TargetSpec): List<String> {
            val kind = when (spec.kind) {
                TargetKind.NEAREST_PLAYER -> "nearest_player"
                TargetKind.NEARBY_PLAYERS -> "nearby_players"
                TargetKind.ALL_PLAYERS -> "all_players"
                TargetKind.RANDOM_PLAYER -> "random_player"
                TargetKind.NEAREST_ENTITY -> "nearest_entity"
                TargetKind.NEARBY_ENTITIES -> "nearby_entities"
                else -> unsupported("対象種別 ${spec.kind}")
            }
            val args = mutableListOf("kind = \"$kind\"")
            spec.entityType?.let { args += "type = \"${escape(it)}\"" }
            spec.maximumDistance?.let { args += "within = ${num(it)}" }
            spec.minimumDistance?.let { args += "farther = ${num(it)}" }
            spec.limit?.let { args += "limit = $it" }
            spec.sort?.let {
                val s = when (it) {
                    TargetSort.NEAREST -> "nearest"; TargetSort.FURTHEST -> "furthest"
                    TargetSort.RANDOM -> "random"
                }
                args += "sort = \"$s\""
            }
            spec.gameMode?.let { args += "gamemode = \"${escape(it).lowercase()}\"" }
            spec.tag?.let { args += "tag = \"${escape(it)}\"" }
            spec.name?.let { args += "name = \"${escape(it)}\"" }
            spec.dx?.let { args += "dx = ${num(it)}" }
            spec.dy?.let { args += "dy = ${num(it)}" }
            spec.dz?.let { args += "dz = ${num(it)}" }
            return args
        }

        /**
         * 条件呼出し（player/exists）の対象引数を復元します。
         * TEMPORARY 参照は `target = <name>`、それ以外は target と同じ絞り込み引数です。
         */
        private fun condTargetText(spec: TargetSpec): String =
            if (spec.kind == TargetKind.TEMPORARY) {
                "target = ${valueName(spec.tempName ?: "")}"
            } else {
                targetArgs(spec).joinToString(", ")
            }

        /**
         * FacingSpec を向き式へ復元します。
         * `location(yaw,pitch)`（ROTATION）・`location(x,y,z)`（座標注視）・
         * `target(...)`（対象注視）・`orientation(...)`（向き複写）・変数参照です。
         */
        private fun facingExpr(spec: FacingSpec): String = when (spec.kind) {
            FacingKind.ROTATION -> "location(yaw = ${component(spec.yaw, spec.semanticsYaw)}, pitch = ${component(spec.pitch, spec.semanticsPitch)})"
            FacingKind.COORDINATES ->
                "location(x = ${num(spec.x)}, y = ${num(spec.y)}, z = ${num(spec.z)})"
            FacingKind.TEMPORARY -> valueName(spec.tempName ?: "")
            FacingKind.TARGET -> facingTargetExpr(spec, "target")
            FacingKind.COPY_ORIENTATION -> facingTargetExpr(spec, "orientation")
        }

        /** TARGET／COPY_ORIENTATION の対象を式へ戻します。変数参照は表層に無いため拒否します。 */
        private fun facingTargetExpr(spec: FacingSpec, ctor: String): String {
            val target = spec.target ?: unsupported("向き対象が未設定です")
            if (target.kind == TargetKind.TEMPORARY) unsupported("向き対象の変数参照")
            return "$ctor(${targetArgs(target).joinToString(", ")})"
        }

        // ---- テキスト復元ユーティリティ ----

        /**
         * テキスト中の `%{name}%`（実行内変数）・`${name}`（ワールド変数）参照を
         * DSL の識別子へ展開します。`dsl.tmpN` は定義式へインラインします。
         */
        private fun expandRefs(raw: String): String {
            var text = raw
            text = EXEC_REF.replace(text) { m -> valueName(m.groupValues[1]) }
            text = WORLD_REF.replace(text) { m -> worldRef(m.groupValues[1]) }
            return text
        }

        /** `${name}` 参照の DSL 表記です。システム変数は `loop.count` へ写像します。 */
        private fun worldRef(name: String): String =
            if (name == SystemVariableNames.CURRENT_LOOP_COUNT) "loop.count"
            else "world.${dslName(name)}"

        /** 変数名を DSL の識別子へ戻します（`dsl.tmpN` は定義式へ展開）。 */
        private fun valueName(name: String): String =
            if (name.startsWith(TEMP_PREFIX)) {
                tempDefs[name] ?: throw DslPrintException("一時変数 '$name' の定義が見つかりません")
            } else dslName(name)

        /** 数値・真偽値・参照のスカラーテキストを式へ戻します。 */
        // 文字列として保存された数値リテラルは正規形へ揃えます（`0.0`→`0`）。
        private fun scalarText(raw: String): String =
            expandRefs(raw).let { it.toDoubleOrNull()?.let(::num) ?: it }

        /** 文字列テンプレートを `"…${expr}…"` の補間リテラルへ戻します。 */
        /**
         * 文字列テンプレートを `"…${expr}…"` の補間リテラルへ戻します。
         * `%{x}%` は `${x}`、`${w}` は `${world.w}` へ写像します。
         */
        private fun quoteTemplate(raw: String): String {
            val sb = StringBuilder("\"")
            var i = 0
            for (m in ANY_REF.findAll(raw)) {
                for (ch in raw.substring(i, m.range.first)) sb.append(escapeChar(ch))
                val execName = m.groupValues[1]
                val expr = if (execName.isNotEmpty()) valueName(execName)
                else worldRef(m.groupValues[2])
                sb.append("\${").append(expr).append('}')
                i = m.range.last + 1
            }
            for (ch in raw.substring(i)) sb.append(escapeChar(ch))
            sb.append('"')
            return sb.toString()
        }

        private fun valueNameOrWorld(raw: String): String =
            if (raw.startsWith("world.")) raw else valueName(raw)

        /** モデル変数名を DSL 識別子へ逆写像します（`-`→`_`、`.`→`_`）。 */
        private fun dslName(model: String): String {
            val dsl = DslNames.dslName(model)
            if (!NAME_PATTERN.matches(dsl)) unsupported("変数名 '$model'")
            return dsl
        }

        /** 変数名を名前位置のテキストへ戻します。空名（未命名スロット）は `null` です。 */
        private fun varNameText(model: String): String =
            if (model.isBlank()) "null" else dslName(model)

        private fun typeName(raw: String): String = raw.lowercase()

        private fun num(v: Number?): String {
            val d = v?.toDouble() ?: 0.0
            return if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
        }

        private fun escape(text: String): String = buildString {
            for (ch in text) append(escapeChar(ch))
        }

        private fun escapeChar(ch: Char): String = when (ch) {
            '"' -> "\\\""
            '\\' -> "\\\\"
            '\n' -> "\\n"
            else -> ch.toString()
        }

        /** 既に展開済みテキスト内の `${expr}` 区切りを壊さないエスケープです。 */
        private fun escapeExpanded(text: String): String = escape(text)

        private fun itemCtor(id: String, data: String?): String {
            val args = mutableListOf("id = \"${escape(id)}\"")
            data?.let { args += "data = \"${escape(it)}\"" }
            return "item(${args.joinToString(", ")})"
        }
    }

    private companion object {
        /** コンパイラ生成の一時変数接頭辞です。DSL 識別子は `.` を含めないため一意です。 */
        const val TEMP_PREFIX = "dsl.tmp"
        val NAME_PATTERN = Regex("[a-z][a-z0-9_]*")
        val EXEC_REF = Regex("%\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}%")
        val WORLD_REF = Regex("\\$\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}")
        /** 実行内・ワールド変数の参照を一括で拾う正規表現です。 */
        val ANY_REF = Regex("%\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}%|\\$\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}")
        /** CALCULATE 値欄の先頭 `%{x}%`/`${x}` 参照と演算子の検出です。 */
        val CALC_HEAD = Regex("^(?:%\\{([A-Za-z][A-Za-z0-9_.-]*)}%|\\\$\\{([A-Za-z][A-Za-z0-9_.-]*)})\\s*([+\\-*/])\\s*(.+)$")
        /** `!` のオペランドとして括弧不要な単純形（変数・プロパティ・リテラル）です。 */
        val SIMPLE_OPERAND = Regex("[!a-zA-Z0-9_.]+")
        /** 本文中のワールドコンテキスト使用（`world.x` 参照・`place_structure`）を検出します。 */
        val WORLD_USE = Regex("\\bworld\\.|\\bplace_structure\\(")
    }
}

// ---- 復元内部の構造 ----

/**
 * 条件領域の解析結果です。`thenEntry`/`elseEntry` はそれぞれ
 * 真・偽の出口が指す生の枝先（MERGE 自体・文ノード・null=終端）です。
 * 領域内の条件への枝は出口に含みません。
 */
private class Region(
    val conds: LinkedHashMap<UUID, CommandNode> = linkedMapOf(),
    var thenEntry: UUID? = null,
    var elseEntry: UUID? = null,
)

/** 条件の枝先の走査結果です。isCond は枝先が条件ノードであることを示します。 */
private class EdgeTarget(val id: UUID?, val isCond: Boolean)

/** 条件式の枝先の分類です。領域内条件／then 出口／else 出口のいずれかです。 */
private sealed interface CondTarget {
    data object Then : CondTarget
    data object Else : CondTarget
    data class Next(val id: UUID) : CondTarget
    /** 領域外の出口です（else-if の入口など）。式への復元では現れません。 */
    data class Other(val id: UUID?) : CondTarget
}

/** 復元した式の最上位演算子です（括弧の要否判定に使います）。 */
private enum class CondTopOp { AND, OR }
