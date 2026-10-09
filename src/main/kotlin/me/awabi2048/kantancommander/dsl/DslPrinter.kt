package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TemporaryTemplate
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
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
    /** グラフを `program "<name>" { ... }` の完全な DSL テキストへ復元します。 */
    fun print(graph: CommandGraph, name: String): String {
        val printer = Printer(graph)
        return printer.printProgram(name)
    }

    private inner class Printer(val graph: CommandGraph) {
        val out = StringBuilder()
        var indent = 0
        /** コンパイラ生成一時変数 `dsl.tmpN` の定義式（展開済みテキスト）です。 */
        val tempDefs = mutableMapOf<String, String>()
        /** DSL へ出力済みの変数名です（var 宣言と再代入の切り分け）。 */
        val declared = mutableSetOf<String>()

        fun printProgram(name: String): String {
            out.append("program \"").append(escape(name)).append("\" {\n")
            indent = 1
            emitBlock(graph.entryNodeId, null)
            out.append("}\n")
            return out.toString()
        }

        private fun line(text: String) {
            out.append("    ".repeat(indent)).append(text).append('\n')
        }

        private fun unsupported(what: String): Nothing =
            throw DslPrintException("DSL へ写像できない構造です: $what")

        // ---- ブロック走査 ----

        /**
         * `entry` から `boundary`（含まない）までの文を出力します。
         * MERGE は合流の構造要素として透過し、FOR_END は呼び出し側が
         * boundary へ指定しているためここでは現れない想定です。
         */
        private fun emitBlock(entry: UUID?, boundary: UUID?) {
            var cur = entry
            var guard = graph.nodes.size + 8
            while (cur != null && cur != boundary) {
                if (guard-- <= 0) throw DslPrintException("ノード走査が上限を超えました（循環の可能性）")
                val n = graph.nodes[cur] ?: throw DslPrintException("不明なノード参照です: $cur")
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
            val dsl = dslName(name)
            val calc = compoundAssign(n, name)
            when {
                calc != null && dsl in declared -> line("$dsl $calc;")
                declared.add(dsl) -> line("var $dsl: ${typeName(n.string("tempType"))} = $expr;")
                else -> line("$dsl = $expr;")
            }
        }

        private fun emitVariable(n: CommandNode) {
            val scope = VariableScope.parse(n.string("scope")) ?: VariableScope.WORLD
            if (scope != VariableScope.WORLD) unsupported("VARIABLE の範囲 '$scope'")
            if (n.string("operation") != VariableOperation.SET.name) {
                unsupported("VARIABLE の操作 '${n.string("operation")}'")
            }
            val name = n.string("name")
            val dsl = "world.${dslName(name)}"
            val calc = compoundAssign(n, name)
            if (calc != null) {
                line("$dsl $calc;")
            } else {
                line("$dsl = ${variableExpr(n)};")
            }
        }

        /**
         * CALCULATE の `<self> <op> <rhs>` 形を複合代入 `x <op>= rhs` へ畳み込みます。
         * 左辺の参照がこのノードの変数名と一致しない場合は null（通常代入）です。
         */
        private fun compoundAssign(n: CommandNode, name: String): String? {
            if (n.string("changeMode") != VariableChangeMode.CALCULATE.name) return null
            val m = CALC_HEAD.matchEntire(n.string("value")) ?: return null
            val execName = m.groupValues[1]
            val worldName = m.groupValues[2]
            val op = m.groupValues[3]
            val rhs = m.groupValues[4]
            val isSelf = if (n.type == CommandType.TEMP_SET) execName == name else worldName == name
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
                VariableChangeMode.CALCULATE.name -> expandRefs(n.string("value"))
                else -> assignExpr(n, typeName)
            }
        }

        /** ASSIGN の値（型別の構造値またはテンプレート文字列）を式へ復元します。 */
        private fun assignExpr(n: CommandNode, typeName: String): String = when (typeName) {
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
            // ループ本体内では空の枝が FOR_END を直接指すことがあります。
            // これは分岐先がループ境界そのものであり、中身のない枝として扱います。
            val thenEmpty = thenEntry == null || thenEntry == join || graph.nodes[thenEntry]?.type == CommandType.FOR_END
            val elseEmpty = elseEntry == null || elseEntry == join || graph.nodes[elseEntry]?.type == CommandType.FOR_END
            if (thenEmpty) {
                // then が空なら else を本体へ反転します。
                if (!elseEmpty) {
                    val inv = invertExpr(expr)
                    line("$keyword ($inv) {")
                    indent++
                    emitBlock(elseEntry, join)
                    indent--
                    line("}")
                } else {
                    line("$keyword ($expr) {")
                    line("}")
                }
                return join
            }
            line("$keyword ($expr) {")
            indent++
            emitBlock(thenEntry, join)
            indent--
            if (!elseEmpty) {
                // else 節が if 文1つだけなら `else if` へ畳み込みます。
                // 先頭が実体化用の一時変数なら条件の直前準備なので読み飛ばします。
                val firstElse = firstRealNode(elseEntry)
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
                emitBlock(elseEntry, join)
                indent--
                line("}")
            } else {
                line("}")
            }
            return join
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
            val count = scalarText(start.string("count"))
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
                line("return;")
                return
            }
            val scope = VariableScope.parse(n.string("sourceScope")) ?: VariableScope.EXECUTION
            val expr = when (scope) {
                VariableScope.WORLD -> "world.${dslName(source)}"
                else -> valueName(source)
            }
            line("return $expr;")
        }

        private fun emitCallDisk(n: CommandNode) {
            val rawId = n.string("diskId")
            val name = runCatching { UUID.fromString(rawId) }.getOrNull()
                ?.let(diskNameResolver) ?: rawId
            line("call \"${escape(name)}\";")
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
                val text = renderArg(n, argName, rule) ?: continue
                args += "$argName = $text"
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

        /** 命令の1引数をノード欄・構造値から読み、DSL の引数テキストへ戻します。 */
        private fun renderArg(n: CommandNode, argName: String, rule: ArgRule): String? = when (rule.kind) {
            ArgKind.NUMBER -> n.string(rule.param!!).takeIf(String::isNotBlank)?.let { v ->
                // 数値欄の保存形は `1.0` のように浮動小数表記を含むため、
                // 純数値は正規化して canonical 形へ揃えます。
                val expanded = expandRefs(v)
                expanded.toDoubleOrNull()?.let(::num) ?: expanded
            }
            ArgKind.BOOLEAN -> n.string(rule.param!!).takeIf(String::isNotBlank)?.let(::scalarText)
            ArgKind.STRING -> n.string(rule.param!!).takeIf(String::isNotBlank)?.let(::quoteTemplate)
            ArgKind.LITERAL -> {
                val v = n.string(rule.param!!)
                val isDefault = v == n.type.defaults[rule.param]
                if (v.isBlank() || (!rule.required && isDefault)) null else "\"${escape(v)}\""
            }
            ArgKind.ENTITY -> specOf(n, rule.slot!!)?.let { (it as? TargetSpec)?.let(::targetExpr) }
            ArgKind.LOCATION -> when (rule.slot) {
                ArgSlot.DEST_FACING -> n.destinationFacingSpec?.let(::facingExpr)
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

        private fun specOf(n: CommandNode, slot: ArgSlot): Any? = when (slot) {
            ArgSlot.TARGET -> n.targetSpec
            ArgSlot.SECONDARY_TARGET -> n.secondaryTargetSpec
            ArgSlot.DESTINATION -> n.destinationSpec
            ArgSlot.DEST_FACING -> n.destinationFacingSpec
            ArgSlot.EXEC_POS -> n.executionPositionSpec
            ArgSlot.SOUND_POS -> n.soundPositionSpec
            ArgSlot.SUMMON_POS -> n.summonPositionSpec
            ArgSlot.PARTICLE_POS -> n.particlePositionSpec
            ArgSlot.BLOCK_POS -> n.blockPositionSpec
            ArgSlot.BLOCK_FROM -> n.blockFromSpec
            ArgSlot.BLOCK_TO -> n.blockToSpec
            ArgSlot.FIREWORK_POS -> n.fireworkPositionSpec
        }

        // ---- 式の葉の復元 ----

        /** CONDITION の葉を `<lhs> <op> <rhs>` の式テキストへ復元します。 */
        private fun leafExpr(c: CommandNode): String = when (c.string("subject")) {
            ConditionSubject.VARIABLE.name -> {
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
                val op = if (c.boolean("inverted")) "!=" else "=="
                "${positionExpr(spec)}.block $op block(id = \"${escape(c.string("block"))}\")"
            }
            else -> throw DslPrintException("DSL へ写像できない条件対象です: ${c.string("subject")}")
        }

        /** 条件左辺の `%{x}%`（実行内変数）・plain 名（ワールド変数）を式へ戻します。 */
        private fun conditionLhs(raw: String): String {
            if (TemporaryTemplate.isSingleReference(raw)) {
                val name = TemporaryTemplate.references(raw).single()
                return valueName(name)
            }
            return "world.${dslName(raw)}"
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
                if (!relative && spec.yaw == null && spec.pitch == null) {
                    "location(x = ${num(spec.x)}, y = ${num(spec.y)}, z = ${num(spec.z)})"
                } else if (!relative) {
                    "location(x = ${num(spec.x)}, y = ${num(spec.y)}, z = ${num(spec.z)}, yaw = ${num(spec.yaw)}, pitch = ${num(spec.pitch)})"
                } else {
                    "${component(spec.x, spec.semanticsX)} ${component(spec.y, spec.semanticsY)} ${component(spec.z, spec.semanticsZ)}"
                }
            }
            else -> unsupported("位置種別 ${spec.kind}")
        }

        /** 位置リテラルの1成分を `~`/`^`/絶対値へ復元します。 */
        private fun component(value: Double?, semantics: CoordinateSemantics): String {
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
                    TargetSort.RANDOM -> "random"; else -> null
                } ?: unsupported("対象整列 $it")
                args += "sort = \"$s\""
            }
            spec.gameMode?.let { args += "gamemode = \"${escape(it)}\"" }
            spec.tag?.let { args += "tag = \"${escape(it)}\"" }
            spec.name?.let { args += "name = \"${escape(it)}\"" }
            spec.dx?.let { args += "dx = ${num(it)}" }
            spec.dy?.let { args += "dy = ${num(it)}" }
            spec.dz?.let { args += "dz = ${num(it)}" }
            return "target(${args.joinToString(", ")})"
        }

        /** FacingSpec を `location(yaw = ..., pitch = ...)` 等へ復元します。 */
        private fun facingExpr(spec: FacingSpec): String = when (spec.kind) {
            FacingKind.ROTATION -> "location(yaw = ${num(spec.yaw)}, pitch = ${num(spec.pitch)})"
            FacingKind.TEMPORARY -> valueName(spec.tempName ?: "")
            else -> unsupported("向き種別 ${spec.kind}")
        }

        // ---- テキスト復元ユーティリティ ----

        /**
         * テキスト中の `%{name}%`（実行内変数）・`${name}`（ワールド変数）参照を
         * DSL の識別子へ展開します。`dsl.tmpN` は定義式へインラインします。
         */
        private fun expandRefs(raw: String): String {
            var text = raw
            text = EXEC_REF.replace(text) { m -> valueName(m.groupValues[1]) }
            text = WORLD_REF.replace(text) { m -> "world.${dslName(m.groupValues[1])}" }
            return text
        }

        /** 変数名を DSL の識別子へ戻します（`dsl.tmpN` は定義式へ展開）。 */
        private fun valueName(name: String): String =
            if (name.startsWith(TEMP_PREFIX)) {
                tempDefs[name] ?: throw DslPrintException("一時変数 '$name' の定義が見つかりません")
            } else dslName(name)

        /** 数値・真偽値・参照のスカラーテキストを式へ戻します。 */
        private fun scalarText(raw: String): String = expandRefs(raw)

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
                else "world.${dslName(m.groupValues[2])}"
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
            val dsl = model.replace('-', '_').replace('.', '_')
            if (!NAME_PATTERN.matches(dsl)) unsupported("変数名 '$model'")
            return dsl
        }

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
