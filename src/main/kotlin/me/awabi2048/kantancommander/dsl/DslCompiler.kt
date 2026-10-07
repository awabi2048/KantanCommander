package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.DiskArgument
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.ValueProperties
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.encodeDeclaredArguments
import java.util.UUID

/** DSL のモデル写像（低級化）の失敗です。ユーザー向けの日本語メッセージを持ちます。 */
class DslCompileException(message: String, val pos: DslPos? = null) :
    Exception(if (pos != null) "$pos: $message" else message)

/** `call` の対象ディスクのリンク情報です。 */
data class DslDiskLink(
    val diskId: UUID,
    val snapshot: CommandGraph,
    val arguments: List<DiskArgument> = emptyList(),
)

/**
 * DSL 構文木を実行モデル（CommandGraph）へ低級化するコンパイラです。
 *
 * 設計の要点:
 * - 実行内変数は TEMP_SET ノードで実体化し、値欄は `%{name}%`／`${name}` の
 *   モデルテンプレート記法で参照します。
 * - `if` の条件式は CONDITION ノードの木へ短絡評価で展開し、3本以上の枝が
 *   集まる点だけに MERGE を生成します。
 * - 直列文の接続は「最後の未接続辺」を次ノードへ繋ぐ形で行い、分岐の合流は
 *   Junction（枝の集まる接続点）が解決時に必要なら MERGE を生成します。
 * - MERGE と CONDITION の対応は、枝が構造的にその合流または終端へ到達する
 *   条件ノードを到達性スキャンで割り当てます（GUI の pairedNodeId 相当）。
 * - 生成されるグラフは GraphValidator／ExecutableScriptValidator の規則に
 *   従うため、DSL 側の意味と保存・実行可能性を一致させます。
 */
class DslCompiler(
    private val worldTypes: Map<String, ValueType> = emptyMap(),
    private val diskResolver: (String) -> DslDiskLink? = { null },
) {
    /** 検証済みのプログラムを CommandGraph へ低級化します。 */
    fun compile(program: DslProgram): CommandGraph {
        val exprTypes = mutableMapOf<DslExpr, ValueType>()
        DslChecker(worldTypes, exprTypes).check(program)
        val builder = Builder(exprTypes)
        val graph = builder.build(program)
        builder.assignMergePairs()
        return graph
    }

    private inner class Builder(private val exprTypes: Map<DslExpr, ValueType>) {
        val graph = CommandGraph()
        var tempCounter = 0
        /** 生成順の CONDITION・MERGE 一覧です。合流対応の割当てに使います。 */
        val conditions = mutableListOf<CommandNode>()
        val merges = mutableListOf<CommandNode>()
        /** if 文の条件木の入口ノード（評価順の先頭）です。 */
        val entryConds = mutableListOf<CommandNode>()

        private fun error(pos: DslPos?, message: String): Nothing = throw DslCompileException(message, pos)

        fun node(type: CommandType): CommandNode {
            val n = type.newNode()
            graph.nodes[n.id] = n
            when (type) {
                CommandType.CONDITION -> conditions += n
                CommandType.MERGE -> merges += n
                else -> Unit
            }
            return n
        }

        fun freshTemp(): String = "dsl.tmp${++tempCounter}"

        /**
         * DSL の変数名をモデルの変数名へ写像します。
         * モデルの命名規則 `[a-z][a-z0-9_.-]*` は `_` を含まないため、
         * DSLのsnake_caseは `-` へ置き換えます。DSL識別子は `-`／`.` を
         * 含めないため、この変換は一意です。
         */
        private fun modelName(name: String): String = name.replace('_', '-')

        /** 実行内変数参照 `%{name}%` の文字列です。 */
        private fun execRef(name: String): String = "%{${modelName(name)}}%"

        /** ワールド変数参照 `${name}` の文字列です。 */
        private fun worldRef(name: String): String = "\${${modelName(name)}}"

        // ---- 枝・合流の接続機構 ----

        private fun connect(tail: Tail, target: UUID?) = when (tail.edge) {
            Edge.NEXT -> tail.node.next = target
            Edge.TRUE -> tail.node.trueNext = target
            Edge.FALSE -> tail.node.falseNext = target
        }

        /**
         * ジャンクションを指定先へ解決します。枝が1本のみなら直接接続、
         * 2本以上なら MERGE を生成して集約します。
         */
        private fun resolveJunction(junction: Junction, target: UUID?) {
            when (junction.incoming.size) {
                0 -> Unit
                1 -> connect(junction.incoming.single(), target)
                else -> {
                    val merge = node(CommandType.MERGE)
                    junction.incoming.forEach { connect(it, merge.id) }
                    merge.next = target
                }
            }
        }

        private fun stmtChain(vararg nodes: CommandNode): StmtChain {
            nodes.asList().zipWithNext().forEach { (a, b) -> a.next = b.id }
            return StmtChain(
                nodes.firstOrNull()?.id,
                Junction().apply { nodes.lastOrNull()?.let { incoming += Tail(it, Edge.NEXT) } },
            )
        }

        /** 終端文（return/break/continue）のように後続へ渡す辺を持たない構造です。 */
        private fun terminalChain(node: CommandNode): StmtChain =
            StmtChain(node.id, Junction())

        // ---- ブロック（文列） ----

        /**
         * 文列を直列化します。各文の開放辺は次の文へ繋ぎ、最後の開放辺は
         * exitJunction へ集めます。到達不能な文（終端文の直後）はエラーです。
         */
        private fun compileBlock(stmts: List<DslStmt>, exitJunction: Junction): UUID? {
            var pending = Junction()
            var entry: UUID? = null
            var first = true
            stmts.forEach { stmt ->
                val chain = compileStmt(stmt)
                if (!first && pending.incoming.isEmpty() && chain.entry != null) {
                    error(stmt.pos, "終端文の後ろの文は到達できません")
                }
                resolveJunction(pending, chain.entry)
                if (first) { entry = chain.entry; first = false }
                pending = chain.exit
            }
            exitJunction.incoming += pending.incoming
            return entry
        }

        private fun compileStmt(stmt: DslStmt): StmtChain = when (stmt) {
            is DslVarDecl -> lowerVarDecl(stmt)
            is DslAssign -> lowerAssign(stmt)
            is DslIf -> lowerIf(stmt)
            is DslRepeat -> lowerRepeat(stmt)
            is DslBreak -> terminalChain(node(CommandType.BREAK))
            is DslContinue -> terminalChain(node(CommandType.CONTINUE))
            is DslReturn -> lowerReturn(stmt)
            is DslCallStmt -> lowerCall(stmt)
            is DslCallDisk -> lowerCallDisk(stmt)
        }

        // ---- 変数宣言・代入 ----

        /** 値生成ノードを組み立てます。TEMP_SET は実行内、VARIABLE(WORLD) はワールド内変数です。 */
        private fun varNode(
            scope: VariableScope,
            name: String,
            type: ValueType,
            plan: ValuePlan,
        ): CommandNode {
            val node = if (scope == VariableScope.EXECUTION) node(CommandType.TEMP_SET) else node(CommandType.VARIABLE)
            if (scope == VariableScope.EXECUTION) {
                node.params["name"] = modelName(name)
                node.params["tempType"] = type.name
            } else {
                node.params["scope"] = scope.name
                node.params["name"] = modelName(name)
                node.params["type"] = type.name
                node.params["operation"] = VariableOperation.SET.name
            }
            when (plan) {
                is ValuePlan.AssignText -> {
                    node.params["changeMode"] = VariableChangeMode.ASSIGN.name
                    node.params["value"] = plan.text
                }
                is ValuePlan.Calculate -> {
                    node.params["changeMode"] = VariableChangeMode.CALCULATE.name
                    node.params["value"] = plan.expression
                }
                is ValuePlan.Property -> {
                    node.params["changeMode"] = VariableChangeMode.PROPERTY.name
                    node.params["propertySource"] = plan.source
                    node.params["property"] = plan.property
                    if (plan.arg != null) node.params["propertyArg"] = plan.arg
                }
                is ValuePlan.LocationValue -> {
                    node.params["changeMode"] = VariableChangeMode.ASSIGN.name
                    node.temporaryLocationPositionSpec = plan.position
                    // 検証は ROTATION の有限値を必須とするため、spec 未指定の
                    // 角度は 0 へ正規化します（DSL の location 既定向きは 0,0）。
                    node.temporaryLocationFacingSpec = FacingSpec(
                        FacingKind.ROTATION,
                        yaw = plan.position.yaw ?: 0f,
                        pitch = plan.position.pitch ?: 0f,
                    )
                }
                is ValuePlan.EntityValue -> {
                    node.params["changeMode"] = VariableChangeMode.ASSIGN.name
                    node.temporaryEntityTargetSpec = plan.target
                }
                is ValuePlan.ItemValue -> {
                    node.params["changeMode"] = VariableChangeMode.ASSIGN.name
                    if (plan.tempRef != null) {
                        node.itemTempRef = plan.tempRef
                    } else {
                        node.params["item"] = plan.itemId ?: ""
                        node.params["itemData"] = plan.itemData ?: ""
                    }
                }
                is ValuePlan.BlockValue -> {
                    node.params["changeMode"] = VariableChangeMode.ASSIGN.name
                    if (plan.tempRef != null) {
                        node.blockTempRef = plan.tempRef
                    } else {
                        node.params["block"] = plan.blockId ?: ""
                    }
                }
            }
            return node
        }

        private fun lowerVarDecl(stmt: DslVarDecl): StmtChain =
            lowerVarWrite(VariableScope.EXECUTION, stmt.name, stmt.type, stmt.value)

        private fun lowerAssign(stmt: DslAssign): StmtChain {
            val (scope, name) = when (val t = stmt.target) {
                is DslAssignTarget.Variable -> VariableScope.EXECUTION to t.name
                is DslAssignTarget.WorldVariable -> VariableScope.WORLD to t.name
            }
            return when (stmt.op) {
                DslAssignOp.ASSIGN -> lowerVarWrite(scope, name, exprTypes.getValue(stmt.value), stmt.value)
                else -> lowerCompoundAssign(scope, name, stmt)
            }
        }

        /** 複合代入 `x += e` です。数値変数の加減乗除を CALCULATE へ写像します。 */
        private fun lowerCompoundAssign(scope: VariableScope, name: String, stmt: DslAssign): StmtChain {
            val pre = mutableListOf<CommandNode>()
            val operand = operandRef(stmt.value, pre)
            val ref = if (scope == VariableScope.EXECUTION) execRef(name) else worldRef(name)
            val node = varNode(
                scope, name, ValueType.NUMBER,
                ValuePlan.Calculate("$ref ${compoundSymbol(stmt.op)} $operand"),
            )
            return stmtChain(*(pre + node).toTypedArray())
        }

        /**
         * 変数への書き込み（var 宣言・単純代入）です。
         * 式の形に応じて ASSIGN／CALCULATE／PROPERTY・型別 spec のいずれかへ写像します。
         */
        private fun lowerVarWrite(scope: VariableScope, name: String, type: ValueType, expr: DslExpr): StmtChain {
            val pre = mutableListOf<CommandNode>()
            val plan = valuePlan(expr, type, pre)
            val node = varNode(scope, name, type, plan)
            return stmtChain(*(pre + node).toTypedArray())
        }

        /**
         * 式を「値生成の形」へ分類します。リテラル・変数参照はテキスト代入、
         * 数値式は CALCULATE、詳細値は PROPERTY、構造値は型別 spec です。
         */
        private fun valuePlan(expr: DslExpr, expected: ValueType, pre: MutableList<CommandNode>): ValuePlan = when (expr) {
            is DslNumberLiteral, is DslBooleanLiteral, is DslConst ->
                ValuePlan.AssignText(scalarText(expr, expected, pre))
            is DslStringLiteral -> ValuePlan.AssignText(scalarText(expr, expected, pre))
            is DslVarRef -> when (expected) {
                ValueType.ITEM -> ValuePlan.ItemValue(null, null, expr.name)
                ValueType.BLOCK -> ValuePlan.BlockValue(null, expr.name)
                ValueType.LOCATION -> ValuePlan.LocationValue(PositionSpec(PositionKind.TEMPORARY, tempName = modelName(expr.name)))
                ValueType.ENTITY -> ValuePlan.EntityValue(TargetSpec(TargetKind.TEMPORARY, tempName = modelName(expr.name)))
                else -> ValuePlan.AssignText(execRef(expr.name))
            }
            is DslWorldRef -> when (expected) {
                ValueType.NUMBER, ValueType.STRING, ValueType.BOOLEAN -> ValuePlan.AssignText(worldRef(expr.name))
                else -> error(expr.pos, "ワールド変数の ${expected.name.lowercase()} 型は実行内変数へ写せません")
            }
            is DslUnary -> when (expr.op) {
                DslUnaryOp.NEG -> {
                    val operand = expr.operand
                    if (operand is DslNumberLiteral) ValuePlan.AssignText(fmt(-operand.value))
                    else ValuePlan.Calculate("-${operandRef(operand, pre)}")
                }
                DslUnaryOp.NOT -> error(expr.pos, "否定式は条件としてのみ使用できます")
            }
            is DslBinary -> when (expr.op) {
                DslBinaryOp.ADD, DslBinaryOp.SUB, DslBinaryOp.MUL, DslBinaryOp.DIV -> {
                    if (expr.left is DslNumberLiteral && expr.right is DslNumberLiteral) {
                        // 両辺リテラルは畳み込み、式の生成を省きます。
                        ValuePlan.AssignText(fmt(foldArith(expr.op, expr.left.value, expr.right.value)))
                    } else {
                        ValuePlan.Calculate(
                            "${operandRef(expr.left, pre)} ${expr.op.symbol()} ${operandRef(expr.right, pre)}",
                        )
                    }
                }
                DslBinaryOp.MOD -> error(expr.pos, "剰余演算（%）はモデルが未対応のため使えません")
                else -> error(expr.pos, "比較・論理式は条件としてのみ使用できます")
            }
            is DslPropertyRead -> {
                val def = ValueProperties.definition(expr.name)
                    ?: error(expr.pos, "不明な詳細値 '${expr.name}' です")
                val sourceName = varNameOf(expr.source, pre, expected)
                val argName = expr.arg?.let { argExpr ->
                    val argType = def.argType ?: error(argExpr.pos, "詳細値 '${expr.name}' は引数を取りません")
                    varNameOf(argExpr, pre, argType)
                }
                ValuePlan.Property(sourceName, expr.name, argName)
            }
            is DslCallExpr -> when (expr.name) {
                "item" -> {
                    val id = literalStringArg(expr, "id")
                    val data = expr.args.firstOrNull { it.name == "data" }
                        ?.let { literalString(it.value, "data") }
                    ValuePlan.ItemValue(id, data, null)
                }
                "block" -> ValuePlan.BlockValue(literalStringArg(expr, "id"), null)
                "location" -> ValuePlan.LocationValue(positionSpecOf(expr, pre))
                "target" -> ValuePlan.EntityValue(targetSpecOf(expr))
                else -> error(expr.pos, "'${expr.name}' の値は代入できません")
            }
            is DslPositionLiteral -> ValuePlan.LocationValue(positionSpecOf(expr, pre))
            else -> error(expr.pos, "この式は代入できません")
        }

        /** 変数参照・リテラル・複合式から参照名（同一scopeの変数名）を取得します。 */
        private fun varNameOf(expr: DslExpr, pre: MutableList<CommandNode>, expected: ValueType): String = when (expr) {
            is DslVarRef -> modelName(expr.name)
            else -> {
                // リテラル・詳細値・算術式は一時変数へ実体化して参照名にします。
                val temp = freshTemp()
                val plan = valuePlan(expr, expected, pre)
                pre += varNode(VariableScope.EXECUTION, temp, expected, plan)
                temp
            }
        }

        /**
         * 数値計算式の被演算子を `%{name}%`／`${name}` の参照文字列へ変換します。
         * モデルの CALCULATE はリテラル被演算子を拒否するため、リテラル・
         * 複合式は一時変数へ実体化して参照にします。
         */
        private fun operandRef(expr: DslExpr, pre: MutableList<CommandNode>): String = when (expr) {
            is DslVarRef -> execRef(expr.name)
            is DslWorldRef -> worldRef(expr.name)
            is DslNumberLiteral -> {
                val temp = freshTemp()
                pre += varNode(VariableScope.EXECUTION, temp, ValueType.NUMBER, ValuePlan.AssignText(fmt(expr.value)))
                "%{$temp}%"
            }
            is DslUnary -> if (expr.op == DslUnaryOp.NEG && expr.operand is DslNumberLiteral) {
                val temp = freshTemp()
                pre += varNode(VariableScope.EXECUTION, temp, ValueType.NUMBER, ValuePlan.AssignText(fmt(-expr.operand.value)))
                "%{$temp}%"
            } else {
                val temp = freshTemp()
                pre += varNode(VariableScope.EXECUTION, temp, ValueType.NUMBER, ValuePlan.Calculate("-${operandRef(expr.operand, pre)}"))
                "%{$temp}%"
            }
            else -> {
                val temp = freshTemp()
                val plan = valuePlan(expr, ValueType.NUMBER, pre)
                pre += varNode(VariableScope.EXECUTION, temp, ValueType.NUMBER, plan)
                "%{$temp}%"
            }
        }

        // ---- 式のスカラー文字列化（値欄・条件値欄・数値欄） ----

        /**
         * 式を値欄へ書けるテンプレート文字列へ変換します。
         * 複合式は一時変数へ実体化して `%{...}%` 参照を返します。
         */
        private fun scalarText(expr: DslExpr, expected: ValueType, pre: MutableList<CommandNode>): String = when (expr) {
            is DslNumberLiteral -> fmt(expr.value)
            is DslBooleanLiteral -> expr.value.toString()
            is DslStringLiteral -> stringTemplate(expr, pre)
            is DslVarRef -> execRef(expr.name)
            is DslWorldRef -> worldRef(expr.name)
            is DslConst -> constValue(expr)
            is DslUnary -> when (expr.op) {
                DslUnaryOp.NEG -> if (expr.operand is DslNumberLiteral) fmt(-expr.operand.value) else operandRef(expr, pre)
                DslUnaryOp.NOT -> error(expr.pos, "否定式は条件としてのみ使用できます")
            }
            is DslBinary -> when (expr.op) {
                DslBinaryOp.ADD, DslBinaryOp.SUB, DslBinaryOp.MUL, DslBinaryOp.DIV -> operandRef(expr, pre)
                else -> error(expr.pos, "比較・論理式は条件としてのみ使用できます")
            }
            is DslPropertyRead -> {
                val temp = freshTemp()
                val plan = valuePlan(expr, expected, pre)
                pre += varNode(VariableScope.EXECUTION, temp, expected, plan)
                "%{$temp}%"
            }
            else -> error(expr.pos, "この式はここでは使えません")
        }

        /** 文字列リテラル（補間込み）をモデルのテンプレート文字列へ変換します。 */
        private fun stringTemplate(lit: DslStringLiteral, pre: MutableList<CommandNode>): String =
            lit.parts.joinToString("") { part ->
                when (part) {
                    is DslTextPart -> part.text
                    is DslInterpPart -> scalarText(part.expr, exprTypes.getValue(part.expr), pre)
                }
            }

        // ---- if 文 ----

        private fun lowerIf(stmt: DslIf): StmtChain {
            val trueJunction = Junction()
            val falseJunction = Junction()
            val condEntry = genCond(stmt.condition, trueJunction, falseJunction)
            conditions.firstOrNull { it.id == condEntry }?.let(entryConds::add)
            val join = Junction()

            // then 側の末端を join へ集め、条件の true 辺を then 先頭へ接続します。
            val thenEntry = compileBlock(stmt.thenBody, join)
            if (thenEntry != null) resolveJunction(trueJunction, thenEntry) else join.incoming += trueJunction.incoming

            // else 側も同様に join へ集めます。else なしのとき false は join へ直通です。
            if (stmt.elseBody != null) {
                val elseEntry = compileBlock(stmt.elseBody, join)
                if (elseEntry != null) resolveJunction(falseJunction, elseEntry) else join.incoming += falseJunction.incoming
            } else {
                join.incoming += falseJunction.incoming
            }
            return StmtChain(condEntry, join)
        }

        /**
         * 条件式を CONDITION ノードの木へ低級化します。
         * `&&`/`||` は右辺の CONDITION を先に作り、左辺の枝をその入口へ接続することで
         * 短絡評価（右辺の事前計算は左辺の枝経路内に配置）を保ちます。
         */
        private fun genCond(expr: DslExpr, trueJunction: Junction, falseJunction: Junction): UUID = when (expr) {
            is DslUnary -> when (expr.op) {
                DslUnaryOp.NOT -> genCond(expr.operand, falseJunction, trueJunction)
                DslUnaryOp.NEG -> error(expr.pos, "否定の単項は条件式に使えません")
            }
            is DslBinary -> when (expr.op) {
                DslBinaryOp.AND -> {
                    val right = genCond(expr.right, trueJunction, falseJunction)
                    val mid = Junction()
                    val left = genCond(expr.left, mid, falseJunction)
                    resolveJunction(mid, right)
                    left
                }
                DslBinaryOp.OR -> {
                    val right = genCond(expr.right, trueJunction, falseJunction)
                    val mid = Junction()
                    val left = genCond(expr.left, trueJunction, mid)
                    resolveJunction(mid, right)
                    left
                }
                else -> leafCondition(expr, trueJunction, falseJunction)
            }
            else -> leafCondition(expr, trueJunction, falseJunction)
        }

        /**
         * 葉条件（比較・boolean 値・詳細値）を CONDITION ノードへ写像します。
         * 条件対象の実体化に必要な一時変数ノードは、この CONDITION の直前へ
         * 直列に配置し、その先頭を返します。
         */
        private fun leafCondition(expr: DslExpr, trueJunction: Junction, falseJunction: Junction): UUID {
            val pre = mutableListOf<CommandNode>()
            val cond = emitCondition(expr, pre)
            trueJunction.incoming += Tail(cond, Edge.TRUE)
            falseJunction.incoming += Tail(cond, Edge.FALSE)
            if (pre.isEmpty()) return cond.id
            pre.zipWithNext().forEach { (a, b) -> a.next = b.id }
            pre.last().next = cond.id
            return pre.first().id
        }

        /**
         * 葉条件式から CONDITION ノードを生成します。
         * `pos.block == block(...)` 形式は BLOCK 判定、それ以外の比較・真偽値は
         * VARIABLE 判定（`%{x}%` で実行内変数を直接評価）へ写像します。
         */
        private fun emitCondition(expr: DslExpr, pre: MutableList<CommandNode>): CommandNode = when (expr) {
            is DslBinary -> when (expr.op) {
                DslBinaryOp.EQ, DslBinaryOp.NE -> {
                    if (expr.left is DslPropertyRead && expr.left.name == "block" &&
                        expr.right is DslCallExpr && expr.right.name == "block"
                    ) {
                        // `pos.block == block(...)` だけはモデルの BLOCK 判定へ直接写像します。
                        val node = node(CommandType.CONDITION)
                        node.params["subject"] = ConditionSubject.BLOCK.name
                        node.params["inverted"] = (expr.op == DslBinaryOp.NE).toString()
                        node.params["block"] = literalStringArg(expr.right, "id")
                        node.conditionPositionSpec = positionSpecOf(expr.left.source, pre)
                        node
                    } else {
                        variableCondition(expr.left, expr.right, exprTypes.getValue(expr.right), expr.op, pre)
                    }
                }
                DslBinaryOp.LT, DslBinaryOp.LE, DslBinaryOp.GT, DslBinaryOp.GE ->
                    variableCondition(expr.left, expr.right, exprTypes.getValue(expr.right), expr.op, pre)
                else -> error(expr.pos, "この式は条件として使えません")
            }
            is DslVarRef, is DslWorldRef, is DslBooleanLiteral, is DslPropertyRead ->
                variableCondition(expr, DslBooleanLiteral(true, expr.pos), ValueType.BOOLEAN, DslBinaryOp.EQ, pre)
            else -> error(expr.pos, "この式は条件として使えません")
        }

        /** VARIABLE 判定の CONDITION ノードを生成します。 */
        private fun variableCondition(
            left: DslExpr,
            right: DslExpr,
            rightType: ValueType,
            op: DslBinaryOp,
            pre: MutableList<CommandNode>,
        ): CommandNode {
            val node = node(CommandType.CONDITION)
            node.params["subject"] = ConditionSubject.VARIABLE.name
            node.params["variable"] = lhsVariable(left, pre)
            node.params["operator"] = op.symbol()
            node.params["value"] = scalarText(right, rightType, pre)
            return node
        }

        /**
         * 条件の左辺を `variable` 欄へ書ける参照名へ変換します。
         * 実行内変数は `%{x}%`、ワールド変数は plain 名、それ以外は一時変数へ
         * 実体化して `%{t}%` を返します。
         */
        private fun lhsVariable(expr: DslExpr, pre: MutableList<CommandNode>): String = when (expr) {
            is DslVarRef -> execRef(expr.name)
            is DslWorldRef -> modelName(expr.name)
            else -> {
                val temp = freshTemp()
                val plan = valuePlan(expr, exprTypes.getValue(expr), pre)
                pre += varNode(VariableScope.EXECUTION, temp, exprTypes.getValue(expr), plan)
                "%{$temp}%"
            }
        }

        // ---- repeat 文 ----

        private fun lowerRepeat(stmt: DslRepeat): StmtChain {
            val pre = mutableListOf<CommandNode>()
            val count = scalarText(stmt.count, ValueType.NUMBER, pre)
            val start = node(CommandType.FOR_START)
            start.params["count"] = count
            val end = node(CommandType.FOR_END)
            start.pairedNodeId = end.id
            end.pairedNodeId = start.id
            val bodyJunction = Junction()
            val bodyEntry = compileBlock(stmt.body, bodyJunction)
            resolveJunction(bodyJunction, end.id)
            // 空本体は FOR_START の trueNext を対向ノードへ置く（実行層の即時退出経路）です。
            start.trueNext = bodyEntry ?: end.id
            val exit = Junction().apply { incoming += Tail(end, Edge.NEXT) }
            val head = (pre + start)
            head.zipWithNext().forEach { (a, b) -> a.next = b.id }
            return StmtChain(head.first().id, exit)
        }

        // ---- return 文 ----

        private fun lowerReturn(stmt: DslReturn): StmtChain {
            val node = node(CommandType.RETURN_VALUE)
            when (val value = stmt.value) {
                null -> Unit // 空欄は「残りを打ち切る」として受理されます。
                is DslVarRef -> {
                    node.params["sourceScope"] = VariableScope.EXECUTION.name
                    node.params["source"] = modelName(value.name)
                }
                is DslWorldRef -> {
                    node.params["sourceScope"] = VariableScope.WORLD.name
                    node.params["source"] = modelName(value.name)
                }
                else -> {
                    // リテラル・式は型を保持するため一時変数へ実体化してから返します。
                    val pre = mutableListOf<CommandNode>()
                    val temp = freshTemp()
                    val type = exprTypes.getValue(value)
                    val plan = valuePlan(value, type, pre)
                    pre += varNode(VariableScope.EXECUTION, temp, type, plan)
                    pre.zipWithNext().forEach { (a, b) -> a.next = b.id }
                    pre.last().next = node.id
                    node.params["sourceScope"] = VariableScope.EXECUTION.name
                    node.params["source"] = temp
                    // 実体化チェーンの先頭が文の入口、RETURN_VALUE 自体が終端です。
                    return StmtChain(pre.first().id, Junction())
                }
            }
            return terminalChain(node)
        }

        // ---- call 文 ----

        private fun lowerCallDisk(stmt: DslCallDisk): StmtChain {
            val name = stmt.diskId.plainText()
                ?: error(stmt.pos, "call の対象は文字列リテラルで指定してください")
            val link = diskResolver(name)
                ?: error(stmt.pos, "call 対象のディスクが見つかりません: $name")
            if (link.arguments.isNotEmpty()) {
                error(stmt.pos, "引数付きディスクの呼出しは未対応です")
            }
            val node = node(CommandType.DISK_CALL)
            node.params["diskId"] = link.diskId.toString()
            node.params["declaredArgs"] = encodeDeclaredArguments(link.arguments)
            node.snapshot = link.snapshot.deepCopy()
            return stmtChain(node)
        }

        // ---- 命令呼出し ----

        private val commands = mapOf(
            "wait" to CommandSpec(CommandType.WAIT, mapOf(
                "seconds" to ArgRule(ArgKind.NUMBER, required = true, param = "seconds"),
            )),
            "teleport" to CommandSpec(CommandType.TELEPORT, mapOf(
                "target" to ArgRule(ArgKind.ENTITY, required = true, slot = ArgSlot.TARGET),
                "to" to ArgRule(ArgKind.LOCATION, required = true, slot = ArgSlot.DESTINATION),
                "facing" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.DEST_FACING),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.EXEC_POS),
            )),
            "give_item" to CommandSpec(CommandType.GIVE_ITEM, mapOf(
                "target" to ArgRule(ArgKind.ENTITY, required = true, slot = ArgSlot.TARGET),
                "item" to ArgRule(ArgKind.ITEM, required = true),
                "count" to ArgRule(ArgKind.NUMBER, required = false, param = "count"),
            )),
            "display_text" to CommandSpec(CommandType.DISPLAY_TEXT, mapOf(
                "text" to ArgRule(ArgKind.STRING, required = true, param = "text"),
                "mode" to ArgRule(ArgKind.LITERAL, required = false, param = "mode",
                    enumValues = setOf("tellraw", "title", "subtitle", "actionbar")),
                "subtitle" to ArgRule(ArgKind.STRING, required = false, param = "subtitle"),
                "fade_in" to ArgRule(ArgKind.NUMBER, required = false, param = "fadeInSeconds"),
                "stay" to ArgRule(ArgKind.NUMBER, required = false, param = "staySeconds"),
                "fade_out" to ArgRule(ArgKind.NUMBER, required = false, param = "fadeOutSeconds"),
                // DISPLAY_TEXT はモデル側で表示対象が必須です。
                "target" to ArgRule(ArgKind.ENTITY, required = true, slot = ArgSlot.TARGET),
            )),
            "summon" to CommandSpec(CommandType.SUMMON_ENTITY, mapOf(
                "entity" to ArgRule(ArgKind.LITERAL, required = true, param = "summonEntity"),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.SUMMON_POS),
                "tags" to ArgRule(ArgKind.STRING, required = false, param = "tags"),
            )),
            "play_sound" to CommandSpec(CommandType.PLAY_SOUND, mapOf(
                "sound" to ArgRule(ArgKind.LITERAL, required = true, param = "sound"),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.SOUND_POS),
                "volume" to ArgRule(ArgKind.NUMBER, required = false, param = "volume"),
                "pitch" to ArgRule(ArgKind.NUMBER, required = false, param = "pitch"),
                "scope" to ArgRule(ArgKind.LITERAL, required = false, param = "soundScope",
                    enumValues = setOf("POSITION", "WORLD")),
                "category" to ArgRule(ArgKind.LITERAL, required = false, param = "soundCategory"),
                "operation" to ArgRule(ArgKind.LITERAL, required = false, param = "soundOperation",
                    enumValues = setOf("PLAY", "STOP")),
            )),
            "entity_delete" to CommandSpec(CommandType.ENTITY_DELETE, mapOf(
                "target" to ArgRule(ArgKind.ENTITY, required = true, slot = ArgSlot.TARGET),
            )),
            "apply_effect" to CommandSpec(CommandType.APPLY_EFFECT, mapOf(
                "target" to ArgRule(ArgKind.ENTITY, required = true, slot = ArgSlot.TARGET),
                "effect" to ArgRule(ArgKind.LITERAL, required = true, param = "effect"),
                "seconds" to ArgRule(ArgKind.NUMBER, required = false, param = "seconds"),
                "level" to ArgRule(ArgKind.NUMBER, required = false, param = "level"),
            )),
            "camera_shake" to CommandSpec(CommandType.CAMERA_SHAKE, mapOf(
                "target" to ArgRule(ArgKind.ENTITY, required = true, slot = ArgSlot.TARGET),
                "intensity" to ArgRule(ArgKind.NUMBER, required = false, param = "intensity"),
                "seconds" to ArgRule(ArgKind.NUMBER, required = false, param = "seconds"),
                "type" to ArgRule(ArgKind.LITERAL, required = false, param = "shakeType",
                    enumValues = setOf("positional", "rotational")),
            )),
            "entity_action" to CommandSpec(CommandType.ENTITY_ACTION, mapOf(
                "target" to ArgRule(ArgKind.ENTITY, required = true, slot = ArgSlot.TARGET),
                "vehicle" to ArgRule(ArgKind.ENTITY, required = false, slot = ArgSlot.SECONDARY_TARGET),
                "action" to ArgRule(ArgKind.LITERAL, required = true, param = "action",
                    enumValues = setOf("ride", "dismount", "motion")),
                "motion_x" to ArgRule(ArgKind.NUMBER, required = false, param = "motionX"),
                "motion_y" to ArgRule(ArgKind.NUMBER, required = false, param = "motionY"),
                "motion_z" to ArgRule(ArgKind.NUMBER, required = false, param = "motionZ"),
            )),
            "setblock" to CommandSpec(CommandType.BLOCK_OPERATION, mapOf(
                "pos" to ArgRule(ArgKind.LOCATION, required = true, slot = ArgSlot.BLOCK_POS),
                "block" to ArgRule(ArgKind.BLOCK, required = true),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.EXEC_POS),
            ), fixedParams = mapOf("operation" to "setblock")),
            "fill" to CommandSpec(CommandType.BLOCK_OPERATION, mapOf(
                "from" to ArgRule(ArgKind.LOCATION, required = true, slot = ArgSlot.BLOCK_FROM),
                "to" to ArgRule(ArgKind.LOCATION, required = true, slot = ArgSlot.BLOCK_TO),
                "block" to ArgRule(ArgKind.BLOCK, required = true),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.EXEC_POS),
            ), fixedParams = mapOf("operation" to "fill")),
            "firework" to CommandSpec(CommandType.FIREWORK_LAUNCH, mapOf(
                "item" to ArgRule(ArgKind.LITERAL, required = false, param = "fireworkItem"),
                "data" to ArgRule(ArgKind.STRING, required = false, param = "fireworkData"),
                "height" to ArgRule(ArgKind.NUMBER, required = false, param = "burstHeight"),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.FIREWORK_POS),
            )),
            "particle" to CommandSpec(CommandType.PARTICLE, mapOf(
                "particle" to ArgRule(ArgKind.LITERAL, required = true, param = "particle"),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.PARTICLE_POS),
                "count" to ArgRule(ArgKind.NUMBER, required = false, param = "particleCount"),
                "speed" to ArgRule(ArgKind.NUMBER, required = false, param = "particleSpeed"),
                "delta_x" to ArgRule(ArgKind.NUMBER, required = false, param = "particleDeltaX"),
                "delta_y" to ArgRule(ArgKind.NUMBER, required = false, param = "particleDeltaY"),
                "delta_z" to ArgRule(ArgKind.NUMBER, required = false, param = "particleDeltaZ"),
                "data" to ArgRule(ArgKind.STRING, required = false, param = "particleData"),
            )),
        )

        private fun lowerCall(stmt: DslCallStmt): StmtChain {
            val spec = commands[stmt.name]
                ?: error(stmt.pos, "未対応の命令 '${stmt.name}' です")
            val pre = mutableListOf<CommandNode>()
            val node = node(spec.type)

            // `wait(1)`・`repeat(5)` のように唯一の必須引数だけ位置引数を許します。
            val args = stmt.args.toMutableList()
            if (args.any { it.name == null }) {
                val required = spec.args.entries.singleOrNull { it.value.required }
                if (required == null || args.size != 1 || args.single().name != null) {
                    error(stmt.pos, "'${stmt.name}' の引数は名前付きが必須です")
                }
                args[0] = args[0].copy(name = required.key)
            }
            val seen = mutableSetOf<String>()
            args.forEach { arg ->
                val rule = spec.args[arg.name]
                    ?: error(arg.pos, "'${stmt.name}' に引数 '${arg.name}' はありません")
                if (!seen.add(arg.name!!)) error(arg.pos, "引数 '${arg.name}' が重複しています")
                applyArg(node, arg, rule, pre)
            }
            spec.args.filter { it.value.required && it.key !in seen }.keys.firstOrNull()?.let {
                error(stmt.pos, "'${stmt.name}' には必須の引数 '$it' がありません")
            }
            spec.fixedParams.forEach { (key, value) -> node.params[key] = value }
            return stmtChain(*(pre + node).toTypedArray())
        }

        /** 1引数をルールに従ってノード欄・構造値・実体化ノードへ写像します。 */
        private fun applyArg(
            node: CommandNode,
            arg: DslArg,
            rule: ArgRule,
            pre: MutableList<CommandNode>,
        ) {
            when (rule.kind) {
                ArgKind.NUMBER -> node.params[rule.param!!] = scalarText(arg.value, ValueType.NUMBER, pre)
                ArgKind.STRING -> node.params[rule.param!!] = scalarText(arg.value, ValueType.STRING, pre)
                ArgKind.BOOLEAN -> node.params[rule.param!!] = scalarText(arg.value, ValueType.BOOLEAN, pre)
                ArgKind.LITERAL -> {
                    val text = literalString(arg.value, arg.name!!)
                    if (rule.enumValues != null && text !in rule.enumValues) {
                        error(arg.pos, "引数 '${arg.name}' は ${rule.enumValues.joinToString("/")} のいずれかです")
                    }
                    node.params[rule.param!!] = text
                }
                ArgKind.ENTITY -> assignSpec(node, rule.slot!!, targetSpecOf(arg.value))
                ArgKind.LOCATION -> if (rule.slot == ArgSlot.DEST_FACING) {
                    node.destinationFacingSpec = facingSpecOf(arg.value, pre)
                } else {
                    assignSpec(node, rule.slot!!, positionSpecOf(arg.value, pre))
                }
                ArgKind.ITEM -> {
                    val (id, data, ref) = itemValue(arg.value, pre)
                    if (ref != null) node.itemTempRef = ref
                    else {
                        node.params["item"] = id ?: ""
                        node.params["itemData"] = data ?: ""
                    }
                }
                ArgKind.BLOCK -> {
                    val (id, ref) = blockValue(arg.value, pre)
                    if (ref != null) node.blockTempRef = ref else node.params["block"] = id ?: ""
                }
            }
        }

        /** 構造値を対応するノードフィールドへ設定します。 */
        private fun assignSpec(node: CommandNode, slot: ArgSlot, spec: Any) {
            when (slot) {
                ArgSlot.TARGET -> node.targetSpec = spec as TargetSpec
                ArgSlot.SECONDARY_TARGET -> node.secondaryTargetSpec = spec as TargetSpec
                ArgSlot.DESTINATION -> node.destinationSpec = spec as PositionSpec
                ArgSlot.DEST_FACING -> node.destinationFacingSpec = spec as FacingSpec
                ArgSlot.EXEC_POS -> node.executionPositionSpec = spec as PositionSpec
                ArgSlot.SOUND_POS -> node.soundPositionSpec = spec as PositionSpec
                ArgSlot.SUMMON_POS -> node.summonPositionSpec = spec as PositionSpec
                ArgSlot.PARTICLE_POS -> node.particlePositionSpec = spec as PositionSpec
                ArgSlot.BLOCK_POS -> node.blockPositionSpec = spec as PositionSpec
                ArgSlot.BLOCK_FROM -> node.blockFromSpec = spec as PositionSpec
                ArgSlot.BLOCK_TO -> node.blockToSpec = spec as PositionSpec
                ArgSlot.FIREWORK_POS -> node.fireworkPositionSpec = spec as PositionSpec
            }
        }

        // ---- 構造値（位置・対象・向き） ----

        /**
         * 位置式を PositionSpec へ写像します。
         * 複合式（詳細値等）は一時変数へ実体化して TEMPORARY 参照にします。
         */
        private fun positionSpecOf(expr: DslExpr, pre: MutableList<CommandNode>): PositionSpec = when (expr) {
            is DslPositionLiteral -> {
                val kind = when (expr.base) {
                    null -> PositionKind.COORDINATES
                    is DslVarRef -> PositionKind.TEMPORARY
                    else -> error(expr.base!!.pos, "位置リテラルの基準は変数のみ指定できます")
                }
                val tempName = (expr.base as? DslVarRef)?.name?.let(::modelName)
                PositionSpec(
                    kind = kind,
                    tempName = tempName,
                    x = componentValue(expr.components, 0),
                    y = componentValue(expr.components, 1),
                    z = componentValue(expr.components, 2),
                    semanticsX = expr.components[0].semantics,
                    semanticsY = expr.components[1].semantics,
                    semanticsZ = expr.components[2].semantics,
                )
            }
            is DslCallExpr -> if (expr.name == "location") {
                PositionSpec(
                    kind = PositionKind.COORDINATES,
                    x = literalNumberArg(expr, "x"),
                    y = literalNumberArg(expr, "y"),
                    z = literalNumberArg(expr, "z"),
                    yaw = expr.args.firstOrNull { it.name == "yaw" }?.let { literalNumber(it.value) }?.toFloat(),
                    pitch = expr.args.firstOrNull { it.name == "pitch" }?.let { literalNumber(it.value) }?.toFloat(),
                )
            } else {
                error(expr.pos, "'${expr.name}' は位置として使えません")
            }
            is DslVarRef -> PositionSpec(PositionKind.TEMPORARY, tempName = modelName(expr.name))
            is DslWorldRef -> error(expr.pos, "ワールド変数の位置は位置式へ使えません")
            else -> {
                val temp = freshTemp()
                val plan = valuePlan(expr, ValueType.LOCATION, pre)
                pre += varNode(VariableScope.EXECUTION, temp, ValueType.LOCATION, plan)
                PositionSpec(PositionKind.TEMPORARY, tempName = temp)
            }
        }

        /** `target(...)` または変数参照を TargetSpec へ写像します。 */
        private fun targetSpecOf(expr: DslExpr): TargetSpec = when (expr) {
            is DslVarRef -> TargetSpec(TargetKind.TEMPORARY, tempName = modelName(expr.name))
            is DslCallExpr -> if (expr.name == "target") {
                TargetSpec(
                    kind = when (literalStringArg(expr, "kind")) {
                        "nearest_player" -> TargetKind.NEAREST_PLAYER
                        "nearby_players" -> TargetKind.NEARBY_PLAYERS
                        "all_players" -> TargetKind.ALL_PLAYERS
                        "random_player" -> TargetKind.RANDOM_PLAYER
                        "nearest_entity" -> TargetKind.NEAREST_ENTITY
                        "nearby_entities" -> TargetKind.NEARBY_ENTITIES
                        else -> error(expr.pos, "target の kind が不明です")
                    },
                    entityType = expr.args.firstOrNull { it.name == "type" }?.let { literalString(it.value, "type") },
                    maximumDistance = expr.args.firstOrNull { it.name == "within" }?.let { literalNumber(it.value) },
                    minimumDistance = expr.args.firstOrNull { it.name == "farther" }?.let { literalNumber(it.value) },
                    limit = expr.args.firstOrNull { it.name == "limit" }?.let { literalNumber(it.value).toInt() },
                    sort = expr.args.firstOrNull { it.name == "sort" }?.let {
                        when (literalString(it.value, "sort")) {
                            "nearest" -> TargetSort.NEAREST
                            "furthest" -> TargetSort.FURTHEST
                            "random" -> TargetSort.RANDOM
                            else -> error(it.pos, "sort は nearest/furthest/random のいずれかです")
                        }
                    },
                    gameMode = expr.args.firstOrNull { it.name == "gamemode" }?.let { gamemodeConst(it.value) },
                    tag = expr.args.firstOrNull { it.name == "tag" }?.let { literalString(it.value, "tag") },
                    name = expr.args.firstOrNull { it.name == "name" }?.let { literalString(it.value, "name") },
                    dx = expr.args.firstOrNull { it.name == "dx" }?.let { literalNumber(it.value) },
                    dy = expr.args.firstOrNull { it.name == "dy" }?.let { literalNumber(it.value) },
                    dz = expr.args.firstOrNull { it.name == "dz" }?.let { literalNumber(it.value) },
                )
            } else {
                error(expr.pos, "'${expr.name}' は対象として使えません")
            }
            else -> error(expr.pos, "対象は target(...) または entity 型変数で指定してください")
        }

        /** `facing` 引数（向き指定）を FacingSpec へ写像します。 */
        private fun facingSpecOf(expr: DslExpr, pre: MutableList<CommandNode>): FacingSpec = when (expr) {
            is DslCallExpr -> if (expr.name == "location") {
                // ROTATION は有限値必須のため、省略成分は 0 へ正規化します。
                FacingSpec(
                    kind = FacingKind.ROTATION,
                    yaw = expr.args.firstOrNull { it.name == "yaw" }?.let { literalNumber(it.value) }?.toFloat() ?: 0f,
                    pitch = expr.args.firstOrNull { it.name == "pitch" }?.let { literalNumber(it.value) }?.toFloat() ?: 0f,
                )
            } else {
                error(expr.pos, "facing は location(...) で指定してください")
            }
            is DslVarRef -> FacingSpec(FacingKind.TEMPORARY, tempName = modelName(expr.name))
            else -> error(expr.pos, "facing は location(...) または location 型変数で指定してください")
        }

        /** 位置リテラルの成分値を返します。`~` 単独は RELATIVE 0.0 として明示します。 */
        private fun componentValue(components: List<DslPositionComponent>, index: Int): Double =
            components[index].value

        // ---- 型別値 ----

        /** アイテム式を id/data/参照名へ分解します。 */
        private fun itemValue(expr: DslExpr, pre: MutableList<CommandNode>): Triple<String?, String?, String?> = when (expr) {
            is DslVarRef -> Triple(null, null, modelName(expr.name))
            is DslCallExpr -> if (expr.name == "item") {
                Triple(
                    literalStringArg(expr, "id"),
                    expr.args.firstOrNull { it.name == "data" }?.let { literalString(it.value, "data") },
                    null,
                )
            } else error(expr.pos, "item には item(...) または item 型変数を指定してください")
            else -> error(expr.pos, "item には item(...) または item 型変数を指定してください")
        }

        /** ブロック式を id/参照名へ分解します。 */
        private fun blockValue(expr: DslExpr, pre: MutableList<CommandNode>): Pair<String?, String?> = when (expr) {
            is DslVarRef -> Pair(null, modelName(expr.name))
            is DslCallExpr -> if (expr.name == "block") {
                Pair(literalStringArg(expr, "id"), null)
            } else error(expr.pos, "block には block(...) または block 型変数を指定してください")
            else -> error(expr.pos, "block には block(...) または block 型変数を指定してください")
        }

        // ---- リテラル・定数 ----

        private fun literalString(expr: DslExpr, name: String): String = when (expr) {
            is DslStringLiteral -> expr.plainText()
                ?: error(expr.pos, "引数 '$name' は補間なしの文字列リテラルで指定してください")
            is DslConst -> constValue(expr)
            else -> error(expr.pos, "引数 '$name' は文字列リテラルで指定してください")
        }

        private fun literalStringArg(expr: DslCallExpr, name: String): String =
            expr.args.firstOrNull { it.name == name }?.let { literalString(it.value, name) }
                ?: error(expr.pos, "'${expr.name}' には引数 '$name' が必要です")

        private fun literalNumber(expr: DslExpr): Double = when (expr) {
            is DslNumberLiteral -> expr.value
            is DslUnary -> if (expr.op == DslUnaryOp.NEG && expr.operand is DslNumberLiteral) -expr.operand.value
                else error(expr.pos, "数値リテラルで指定してください")
            else -> error(expr.pos, "数値リテラルで指定してください")
        }

        private fun literalNumberArg(expr: DslCallExpr, name: String): Double =
            expr.args.firstOrNull { it.name == name }?.let { literalNumber(it.value) }
                ?: error(expr.pos, "'${expr.name}' には引数 '$name' が必要です")

        /** `gamemode.creative` 等の定数を値文字列へ畳み込みます。 */
        private fun constValue(expr: DslConst): String = when (expr.group) {
            "gamemode" -> expr.name.uppercase()
            else -> error(expr.pos, "不明な定数 '${expr.group}.${expr.name}' です")
        }

        /** `gamemode` 引数の定数・文字列を大文字名へ正規化します。 */
        private fun gamemodeConst(expr: DslExpr): String = when (expr) {
            is DslConst -> constValue(expr)
            is DslStringLiteral -> expr.plainText()?.uppercase()
                ?: error(expr.pos, "gamemode は定数または文字列リテラルで指定してください")
            else -> error(expr.pos, "gamemode は定数または文字列リテラルで指定してください")
        }

        // ---- ユーティリティ ----

        private fun fmt(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

        private fun foldArith(op: DslBinaryOp, a: Double, b: Double): Double = when (op) {
            DslBinaryOp.ADD -> a + b
            DslBinaryOp.SUB -> a - b
            DslBinaryOp.MUL -> a * b
            DslBinaryOp.DIV -> a / b
            else -> error(null, "畳み込み不能な演算です")
        }

        private fun DslBinaryOp.symbol(): String = when (this) {
            DslBinaryOp.ADD -> "+"; DslBinaryOp.SUB -> "-"; DslBinaryOp.MUL -> "*"
            DslBinaryOp.DIV -> "/"; DslBinaryOp.MOD -> "%"; DslBinaryOp.EQ -> "=="
            DslBinaryOp.NE -> "!="; DslBinaryOp.LT -> "<"; DslBinaryOp.LE -> "<="
            DslBinaryOp.GT -> ">"; DslBinaryOp.GE -> ">="; DslBinaryOp.AND -> "&&"
            DslBinaryOp.OR -> "||"
        }

        // ---- MERGE 対応 ----

        /**
         * 生成された各 MERGE に対応する CONDITION を1つ割り当てます
         * （GUI の pairedNodeId 相当）。
         *
         * 割当て規則:
         * - 両枝の全経路がその MERGE へ収束する条件があれば、その中で
         *   if 入口に最も近い（生成順で早い）ものを選びます。if/else-if の
         *   外側合流や `if` 直後の合流はこれで正しく対応します。
         * - 収束条件がなければ `&&`/`||` 由来の合流であり、枝が MERGE または
         *   終端へ到達する条件のうち最も深い葉（生成順で最も早いもの）を選びます。
         */
        fun assignMergePairs() {
            val used = mutableSetOf<CommandNode>()
            merges.forEach { merge ->
                val candidates = conditions.filter { it !in used && reachesMergeOrEnd(it, merge) }
                val cond = candidates
                    .filter { allPathsReach(it, merge) }
                    .minByOrNull { condOrder(it) }
                    ?: candidates.firstOrNull()
                    ?: error(null, "MERGE に対応する条件ノードが見つかりません")
                // 対応は双方向で保持します（CONDITION→MERGE・MERGE→CONDITION）。
                cond.pairedNodeId = merge.id
                merge.pairedNodeId = cond.id
                used += cond
            }
        }

        /** if 入口条件を優先し、それ以外は生成順に並べる比較値です。 */
        private fun condOrder(cond: CommandNode): Int {
            val entryIndex = entryConds.indexOf(cond)
            return if (entryIndex >= 0) entryIndex else entryConds.size + conditions.indexOf(cond)
        }

        /**
         * 条件ノードの両枝の全経路が merge またはグラフ終端へ到達するかを
         * 判定します。`&&`/`||` のジャンクション合流はこの条件を満たします。
         */
        private fun reachesMergeOrEnd(cond: CommandNode, merge: CommandNode): Boolean {
            fun reaches(start: UUID?, depth: Int): Boolean {
                if (depth > graph.nodes.size) return false
                if (start == null || start == merge.id) return true
                val node = graph.nodes[start] ?: return true
                return reaches(node.next, depth + 1) && reaches(node.trueNext, depth + 1) && reaches(node.falseNext, depth + 1)
            }
            return reaches(cond.trueNext, 0) && reaches(cond.falseNext, 0)
        }

        /**
         * 条件ノードの両枝の全経路がその MERGE 自体へ到達するかを判定します。
         * if 文の真の合流点はこの条件を満たします。
         */
        private fun allPathsReach(cond: CommandNode, merge: CommandNode): Boolean {
            fun reaches(start: UUID?, depth: Int): Boolean {
                if (depth > graph.nodes.size) return false
                if (start == null) return false
                if (start == merge.id) return true
                val node = graph.nodes[start] ?: return false
                return reaches(node.next, depth + 1) && reaches(node.trueNext, depth + 1) && reaches(node.falseNext, depth + 1)
            }
            return reaches(cond.trueNext, 0) && reaches(cond.falseNext, 0)
        }

        fun build(program: DslProgram): CommandGraph {
            val exit = Junction()
            val entry = compileBlock(program.body, exit)
            resolveJunction(exit, null)
            graph.entryNodeId = entry
            return graph
        }
    }
}

// ---- 低級化内部の構造 ----

private enum class Edge { NEXT, TRUE, FALSE }
private data class Tail(val node: CommandNode, val edge: Edge)

/** 複数の枝が同じ出口へ集まる接続点です。 */
private class Junction {
    val incoming = mutableListOf<Tail>()
}

/** 1文分の構造です。entry は開始ノード、exit は後続へ渡す開放辺の集まりです。 */
private class StmtChain(val entry: UUID?, val exit: Junction)

/**
 * 値生成の形です。TEMP_SET／VARIABLE ノードの changeMode・spec・
 * 値欄のいずれかへ写像します。
 */
private sealed interface ValuePlan {
    /** `value` 欄へ書くテンプレート文字列（ASSIGN）です。 */
    data class AssignText(val text: String) : ValuePlan
    /** 数値計算式（CALCULATE、被演算子は変数参照のみ）です。 */
    data class Calculate(val expression: String) : ValuePlan
    /** 詳細値読出し（PROPERTY）です。 */
    data class Property(val source: String, val property: String, val arg: String?) : ValuePlan
    /** 位置（LOCATION）の構造値です。 */
    data class LocationValue(val position: PositionSpec) : ValuePlan
    /** 対象（ENTITY）の構造値です。 */
    data class EntityValue(val target: TargetSpec) : ValuePlan
    /** アイテムの構造値です。itemTempRef か item/itemData 欄へ写像します。 */
    data class ItemValue(val itemId: String?, val itemData: String?, val tempRef: String?) : ValuePlan
    /** ブロックの構造値です。blockTempRef か block 欄へ写像します。 */
    data class BlockValue(val blockId: String?, val tempRef: String?) : ValuePlan
}

/** 命令の引数仕様です。slot は構造値の格納先、param は params 欄です。 */
private enum class ArgKind { NUMBER, STRING, BOOLEAN, ENTITY, LOCATION, ITEM, BLOCK, LITERAL }
private enum class ArgSlot { TARGET, SECONDARY_TARGET, DESTINATION, DEST_FACING, EXEC_POS, SOUND_POS, SUMMON_POS, PARTICLE_POS, BLOCK_POS, BLOCK_FROM, BLOCK_TO, FIREWORK_POS }
private data class ArgRule(
    val kind: ArgKind,
    val required: Boolean,
    val param: String? = null,
    val slot: ArgSlot? = null,
    val enumValues: Set<String>? = null,
)
private data class CommandSpec(
    val type: CommandType,
    val args: Map<String, ArgRule>,
    val fixedParams: Map<String, String> = emptyMap(),
)

private fun compoundSymbol(op: DslAssignOp): String = when (op) {
    DslAssignOp.ADD -> "+"; DslAssignOp.SUB -> "-"; DslAssignOp.MUL -> "*"
    DslAssignOp.DIV -> "/"; DslAssignOp.MOD -> "%"; DslAssignOp.ASSIGN -> "="
}
