package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.DiskArgument
import me.awabi2048.kantancommander.model.EventTriggerConditions
import me.awabi2048.kantancommander.model.EventTriggerKind
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TriggerBlockIdentity
import me.awabi2048.kantancommander.model.TriggerClickSide
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.TriggerLeverState
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
/** `compile` の結果です。本体グラフと宣言部（起動方法・呼出し引数）を返します。 */
data class DslCompileResult(
    val graph: CommandGraph,
    val trigger: ScriptTrigger,
    val arguments: List<DiskArgument>,
)

class DslCompiler(
    private val worldTypes: Map<String, ValueType> = emptyMap(),
    private val diskResolver: (String) -> DslDiskLink? = { null },
) {
    /** 検証済みのプログラムを CommandGraph と宣言部へ低級化します。 */
    fun compile(program: DslProgram): DslCompileResult {
        val exprTypes = mutableMapOf<DslExpr, ValueType>()
        DslChecker(worldTypes, exprTypes).check(program)
        val builder = Builder(exprTypes, program.comments)
        val graph = builder.build(program)
        builder.assignMergePairs()
        return DslCompileResult(
            graph = graph,
            trigger = builder.lowerTriggerDecl(program.decls.filterIsInstance<DslTriggerDecl>().singleOrNull()),
            arguments = program.decls.filterIsInstance<DslDefarg>()
                .map { DiskArgument(name = DslNames.modelName(it.name), type = it.type) },
        )
    }

    private inner class Builder(
        private val exprTypes: Map<DslExpr, ValueType>,
        private val comments: Map<DslPos, List<String>>,
    ) {
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
         * 写像規則（snake_case→kebab-case とシステム名の保持）は
         * [DslNames] が単一正本です。
         */
        private fun modelName(name: String): String = DslNames.modelName(name)

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
            StmtChain(node.id, Junction(), listOf(node))

        // ---- ブロック（文列） ----

        /**
         * 文列を直列化します。各文の開放辺は次の文へ繋ぎ、最後の開放辺は
         * exitJunction へ集めます。到達不能な文（終端文の直後）はエラーです。
         * 戻り値の terminals はブロック末端の終端ノード列で、途中の文が
         * 終端を含んでいても後続文がある時点で到達不能エラーになるため、
         * 実質「最後の文の終端集合」として伝播します。
         */
        private fun compileBlock(stmts: List<DslStmt>, exitJunction: Junction): StmtChain {
            var pending = Junction()
            var entry: UUID? = null
            var terminals = emptyList<CommandNode>()
            var first = true
            stmts.forEach { stmt ->
                val chain = compileStmt(stmt)
                if (!first && pending.incoming.isEmpty() && chain.entry != null) {
                    error(stmt.pos, "終端文の後ろの文は到達できません")
                }
                resolveJunction(pending, chain.entry)
                if (first) { entry = chain.entry; first = false }
                pending = chain.exit
                terminals = chain.terminals
            }
            exitJunction.incoming += pending.incoming
            return StmtChain(entry, exitJunction, terminals)
        }

        private fun compileStmt(stmt: DslStmt): StmtChain {
            val chain = when (stmt) {
                is DslVarDecl -> lowerVarDecl(stmt)
                is DslAssign -> lowerAssign(stmt)
                is DslIf -> lowerIf(stmt)
                is DslRepeat -> lowerRepeat(stmt)
                is DslBreak -> terminalChain(node(CommandType.BREAK))
                is DslContinue -> terminalChain(node(CommandType.CONTINUE))
                is DslReturn -> lowerReturn(stmt)
                is DslCallStmt -> lowerCall(stmt)
                is DslCallDisk -> lowerCallDisk(stmt)
                is DslDelete -> lowerDelete(stmt)
            }
            // 文のコメントはその文が生成する先頭ノードへ付記し、ノードが
            // 残る限り DSL 印刷で `//` 行として復元されるようにします。
            comments[stmt.pos]?.takeIf { it.isNotEmpty() }?.let { texts ->
                chain.entry?.let { graph.nodes[it]?.comment = texts.joinToString("\n") }
            }
            return chain
        }

        // ---- 変数宣言・代入 ----

        /**
         * 値生成ノードを組み立てます。TEMP_SET は実行内、VARIABLE はワールド・
         * エンティティ変数です。`operation` は VARIABLE のみ有効で、既定は
         * SET（再設定）です。`entityTarget` は ENTITY scope の対象指定です。
         */
        private fun varNode(
            scope: VariableScope,
            name: String,
            type: ValueType,
            plan: ValuePlan,
            operation: VariableOperation = VariableOperation.SET,
            entityTarget: TargetSpec? = null,
        ): CommandNode {
            val node = if (scope == VariableScope.EXECUTION) node(CommandType.TEMP_SET) else node(CommandType.VARIABLE)
            if (scope == VariableScope.EXECUTION) {
                node.params["name"] = modelName(name)
                node.params["tempType"] = type.name
            } else {
                node.params["scope"] = scope.name
                node.params["name"] = modelName(name)
                node.params["type"] = type.name
                node.params["operation"] = operation.name
            }
            if (entityTarget != null) node.targetSpec = entityTarget
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
                is ValuePlan.RandomRange -> {
                    node.params["changeMode"] = VariableChangeMode.RANDOM.name
                    node.params["range"] = plan.range
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

        private fun lowerVarDecl(stmt: DslVarDecl): StmtChain {
            val call = stmt.value as? DslCallResult
            return if (call != null) {
                // `var x: T = call ".."(..)` は「値未設定の宣言＋戻り値受取の
                // 呼出し」の2ノードです。受取変数は呼出ノード以前に定義済みで
                // ある必要があるため、空値の宣言ノードを先に発行します。
                val scope = if (stmt.world) VariableScope.WORLD else VariableScope.EXECUTION
                val define = varNode(
                    scope, stmt.name, stmt.type, ValuePlan.AssignText(""),
                    operation = if (stmt.world) VariableOperation.DEFINE else VariableOperation.SET,
                )
                val callChain = lowerCallResult(call, if (stmt.world)
                    DslAssignTarget.WorldVariable(stmt.name, stmt.pos)
                    else DslAssignTarget.Variable(stmt.name, stmt.pos))
                define.next = callChain.entry
                StmtChain(define.id, callChain.exit)
            } else if (stmt.world) {
                // ワールド変数の初期宣言は DEFINE を使います（旧保存データの規約と同一）。
                lowerVarWrite(VariableScope.WORLD, stmt.name, stmt.type, stmt.value,
                    operation = VariableOperation.DEFINE)
            } else {
                lowerVarWrite(VariableScope.EXECUTION, stmt.name, stmt.type, stmt.value)
            }
        }

        private fun lowerAssign(stmt: DslAssign): StmtChain {
            return when (val t = stmt.target) {
                is DslAssignTarget.Variable -> when (stmt.op) {
                    DslAssignOp.ASSIGN -> if (stmt.value is DslCallResult) {
                        lowerCallResult(stmt.value as DslCallResult, t)
                    } else {
                        lowerVarWrite(VariableScope.EXECUTION, t.name, exprTypes.getValue(stmt.value), stmt.value)
                    }
                    else -> lowerCompoundAssign(VariableScope.EXECUTION, t.name, stmt)
                }
                is DslAssignTarget.WorldVariable -> when (stmt.op) {
                    // ワールド変数の代入は CHANGE（変更）として記録します。旧保存データと同じ規約です。
                    DslAssignOp.ASSIGN -> if (stmt.value is DslCallResult) {
                        lowerCallResult(stmt.value as DslCallResult, t)
                    } else {
                        lowerVarWrite(VariableScope.WORLD, t.name, exprTypes.getValue(stmt.value), stmt.value,
                            operation = VariableOperation.CHANGE)
                    }
                    else -> lowerCompoundAssign(VariableScope.WORLD, t.name, stmt)
                }
                is DslAssignTarget.EntityVariable -> {
                    if (stmt.op != DslAssignOp.ASSIGN) {
                        error(stmt.pos, "エンティティ変数は = 代入のみ使用できます")
                    }
                    when (val v = stmt.value) {
                        // `e.vars.x = vars.src` は対象ごとに同一Entity内の
                        // 変数から複写する COPY 操作へ写像します。
                        is DslEntityVarRef -> {
                            val node = node(CommandType.VARIABLE)
                            node.params["scope"] = VariableScope.ENTITY.name
                            node.params["operation"] = VariableOperation.COPY.name
                            node.params["changeMode"] = VariableChangeMode.ASSIGN.name
                            node.params["name"] = modelName(t.name)
                            node.params["source"] = modelName(v.name)
                            node.targetSpec = targetSpecOf(t.entity)
                            stmtChain(node)
                        }
                        // `e.vars.x = vars.src.prop` は対象Entity自身の変数を
                        // 参照元に取る PROPERTY 生成へ写像します。引数は
                        // 同じ対象の変数名（`vars.<name>`）です。
                        is DslPropertyRead -> if (v.source is DslEntityVarRef) {
                            val def = ValueProperties.definition(v.name)
                                ?: error(v.pos, "不明な詳細値 '${v.name}' です")
                            val node = node(CommandType.VARIABLE)
                            node.params["scope"] = VariableScope.ENTITY.name
                            node.params["operation"] = VariableOperation.SET.name
                            node.params["changeMode"] = VariableChangeMode.PROPERTY.name
                            node.params["name"] = modelName(t.name)
                            node.params["type"] = def.result.name
                            node.params["propertySource"] = modelName(v.source.name)
                            node.params["property"] = v.name
                            (v.arg as? DslEntityVarRef)?.let {
                                node.params["propertyArg"] = modelName(it.name)
                            }
                            node.targetSpec = targetSpecOf(t.entity)
                            stmtChain(node)
                        } else {
                            // PROPERTY の参照元は対象Entity自身の変数に限定されます。
                            // 実行内・ワールド変数の詳細値は値欄を通らないため
                            // 写像できません。
                            error(v.pos, "エンティティ変数への書き込みでは、詳細値の参照元は vars.<name> で指定してください")
                        }
                        else -> lowerVarWrite(VariableScope.ENTITY, t.name, exprTypes.getValue(v), v,
                            entityTarget = targetSpecOf(t.entity))
                    }
                }
            }
        }

        /**
         * `del` による変数削除です。scope・対象に応じて VARIABLE(DELETE) を発行します。
         * エンティティ変数は対象指定 `targetSpec` をノードへ載せます。
         */
        private fun lowerDelete(stmt: DslDelete): StmtChain {
            val node = node(CommandType.VARIABLE)
            node.params["operation"] = VariableOperation.DELETE.name
            when (val target = stmt.target) {
                is DslAssignTarget.Variable -> {
                    node.params["scope"] = VariableScope.EXECUTION.name
                    node.params["name"] = modelName(target.name)
                }
                is DslAssignTarget.WorldVariable -> {
                    node.params["scope"] = VariableScope.WORLD.name
                    node.params["name"] = modelName(target.name)
                }
                is DslAssignTarget.EntityVariable -> {
                    node.params["scope"] = VariableScope.ENTITY.name
                    node.params["name"] = modelName(target.name)
                    node.targetSpec = targetSpecOf(target.entity)
                }
            }
            return stmtChain(node)
        }

        /** 複合代入 `x += e` です。数値変数の加減乗除を CALCULATE へ写像します。 */
        private fun lowerCompoundAssign(scope: VariableScope, name: String, stmt: DslAssign): StmtChain {
            val pre = mutableListOf<CommandNode>()
            val operand = operandRef(stmt.value, pre)
            val ref = if (scope == VariableScope.EXECUTION) execRef(name) else worldRef(name)
            val node = varNode(
                scope, name, ValueType.NUMBER,
                ValuePlan.Calculate("$ref ${compoundSymbol(stmt.op)} $operand"),
                operation = VariableOperation.CHANGE,
            )
            return stmtChain(*(pre + node).toTypedArray())
        }

        /**
         * 変数への書き込み（var 宣言・単純代入）です。
         * 式の形に応じて ASSIGN／CALCULATE／PROPERTY・型別 spec のいずれかへ写像します。
         */
        private fun lowerVarWrite(
            scope: VariableScope,
            name: String,
            type: ValueType,
            expr: DslExpr,
            operation: VariableOperation = VariableOperation.SET,
            entityTarget: TargetSpec? = null,
        ): StmtChain {
            val pre = mutableListOf<CommandNode>()
            val plan = valuePlan(expr, type, pre)
            val node = varNode(scope, name, type, plan, operation, entityTarget)
            return stmtChain(*(pre + node).toTypedArray())
        }

        /**
         * 式を「値生成の形」へ分類します。リテラル・変数参照はテキスト代入、
         * 数値式は CALCULATE、詳細値は PROPERTY、構造値は型別 spec です。
         */
        private fun valuePlan(expr: DslExpr, expected: ValueType, pre: MutableList<CommandNode>): ValuePlan = when (expr) {
            // `= null` は値が未設定の宣言・代入です。型は宣言・代入先に従い、
            // 値の実体は空として保持します（旧形式の空欄と同じ意味です）。
            is DslNull -> ValuePlan.AssignText("")
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
                "target" -> if (expected == ValueType.LOCATION) {
                    // 対象の現在位置を参照する位置値です（PositionKind.TARGET）。
                    ValuePlan.LocationValue(PositionSpec(PositionKind.TARGET, target = targetSpecOf(expr)))
                } else {
                    ValuePlan.EntityValue(targetSpecOf(expr))
                }
                "random" -> {
                    if (expected != ValueType.NUMBER) {
                        error(expr.pos, "random(...) は number 型へだけ代入できます")
                    }
                    val min = literalNumberArg(expr, "min")
                    val max = literalNumberArg(expr, "max")
                    ValuePlan.RandomRange("${fmt(min)}..${fmt(max)}")
                }
                "myworld_spawn" -> ValuePlan.LocationValue(positionSpecOf(expr, pre))
                else -> error(expr.pos, "'${expr.name}' の値は代入できません")
            }
            is DslSpecialPosition -> ValuePlan.LocationValue(specialPositionSpec(expr))
            is DslLoopCount -> ValuePlan.AssignText(
                "\${" + SystemVariableNames.CURRENT_LOOP_COUNT + "}")
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
            is DslLoopCount ->
                "\${" + SystemVariableNames.CURRENT_LOOP_COUNT + "}"
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
            is DslLoopCount ->
                "\${" + SystemVariableNames.CURRENT_LOOP_COUNT + "}"
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
            val then = compileBlock(stmt.thenBody, join)
            if (then.entry != null) resolveJunction(trueJunction, then.entry) else join.incoming += trueJunction.incoming

            // else 側も同様に join へ集めます。else なしのとき false は join へ直通です。
            val elseChain = stmt.elseBody?.let { elseBody ->
                val chain = compileBlock(elseBody, join)
                if (chain.entry != null) resolveJunction(falseJunction, chain.entry) else join.incoming += falseJunction.incoming
                chain
            } ?: run {
                join.incoming += falseJunction.incoming
                null
            }
            return StmtChain(condEntry, join, then.terminals + (elseChain?.terminals ?: emptyList()))
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
            // `if (null)` は条件 subject 未設定の CONDITION ノードです。
            // ノード既定値を明示的に空欄へ戻し、GUI の未設定状態と同一の保存値にします。
            is DslNull -> node(CommandType.CONDITION).also { it.params["subject"] = "" }
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
            is DslCallExpr -> when (expr.name) {
                "player" -> playerCondition(expr, pre)
                "exists" -> {
                    // 対象存在判定です。PLAYER との違いは補足条件を持たない点だけです。
                    val node = node(CommandType.CONDITION)
                    node.params["subject"] = ConditionSubject.OTHER_ENTITY.name
                    condTargetSpec(expr)?.let { node.targetSpec = it }
                    // `at` は判定位置です。実行位置 spec は対象解決の起点として使われます。
                    expr.args.firstOrNull { it.name == "at" && it.value !is DslNull }?.let {
                        node.executionPositionSpec = positionSpecOf(it.value, pre)
                    }
                    node
                }
                "signal" -> {
                    // 制御ブロックの入力信号強度判定です。`signal(4)`・
                    // `signal(min = 1, max = 15)` を `redstoneSignal` 範囲記法へ変換します。
                    val node = node(CommandType.CONDITION)
                    node.params["subject"] = ConditionSubject.CONTROL_BLOCK.name
                    node.params["redstoneSignal"] = signalRangeText(expr)
                    node
                }
                else -> error(expr.pos, "'${expr.name}' は条件として使えません")
            }
            else -> error(expr.pos, "この式は条件として使えません")
        }

        /**
         * `player(...)` のプレイヤー判定です。対象指定に加えて sneaking・
         * item の補足条件を持ちます。
         */
        private fun playerCondition(expr: DslCallExpr, pre: MutableList<CommandNode>): CommandNode {
            val node = node(CommandType.CONDITION)
            node.params["subject"] = ConditionSubject.PLAYER.name
            condTargetSpec(expr)?.let { node.targetSpec = it }
            expr.args.forEach { arg ->
                if (arg.value is DslNull) return@forEach
                when (arg.name) {
                    "at" -> node.executionPositionSpec = positionSpecOf(arg.value, pre)
                    "sneaking" -> node.params["sneaking"] = (arg.value as? DslBooleanLiteral)?.value?.toString()
                        ?: error(arg.pos, "sneaking は true/false リテラルで指定してください")
                    "item" -> {
                        // PLAYER 判定のアイテム条件は material リテラルのみ受理します
                        // （検証層も同じ規約）。itemTempRef 参照は使いません。
                        val (id, data, ref) = itemValue(arg.value, mutableListOf())
                        if (ref != null) error(arg.pos, "player の item は item(...) リテラルで指定してください")
                        node.params["item"] = id ?: ""
                        node.params["itemData"] = data ?: ""
                    }
                    else -> Unit // kind 系の対象引数は condTargetSpec が処理済みです。
                }
            }
            return node
        }

        /**
         * 条件呼出し（player/exists）から対象指定を構築します。
         * `target = <entity式>` の直接指定または `kind = ...` 以下の絞り込みです。
         * `target = null`・全引数 null のときは未設定（null）を返します。
         */
        private fun condTargetSpec(expr: DslCallExpr): TargetSpec? {
            val direct = expr.args.firstOrNull { it.name == "target" }
            if (direct != null) {
                if (direct.value is DslNull) return null
                return targetSpecOf(direct.value)
            }
            if (expr.args.all { it.value is DslNull }) return null
            return targetSpecFromArgs(expr.args.filter { it.value !is DslNull }, expr.pos)
        }

        /** `signal(...)` の強度範囲を `4`・`1..15`・`3..`・`..2` の範囲記法へ変換します。 */
        private fun signalRangeText(expr: DslCallExpr): String {
            val positional = expr.args.filter { it.name == null && it.value !is DslNull }
            if (positional.isNotEmpty()) return literalNumber(positional.single().value).toInt().toString()
            val min = expr.args.firstOrNull { it.name == "min" && it.value !is DslNull }
                ?.let { literalNumber(it.value).toInt() }
            val max = expr.args.firstOrNull { it.name == "max" && it.value !is DslNull }
                ?.let { literalNumber(it.value).toInt() }
            return when {
                min != null && max != null -> "$min..$max"
                min != null -> "$min.."
                max != null -> "..$max"
                else -> "" // 強度未設定（全引数 null）
            }
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
            // `repeat(null)` は回数未設定の FOR_START です。
            val count = if (stmt.count is DslNull) "" else scalarText(stmt.count, ValueType.NUMBER, pre)
            val start = node(CommandType.FOR_START)
            start.params["count"] = count
            val end = node(CommandType.FOR_END)
            start.pairedNodeId = end.id
            end.pairedNodeId = start.id
            val bodyJunction = Junction()
            val body = compileBlock(stmt.body, bodyJunction)
            resolveJunction(bodyJunction, end.id)
            if (body.entry != null && bodyJunction.incoming.isEmpty()) {
                // 本体の全経路が終端文（break/continue/return）で閉じるとき、
                // FOR_END への構造辺が残りません。GUI の本体挿入と同じ規約で、
                // 最後の終端ノード（実行時のジャンプ先と一致する CONTINUE を優先）
                // の next を FOR_END へ繋ぎます。実行時はループ枠のジャンプが
                // 優先されるため、この辺は構造到達性のためにのみ存在します。
                val tail = body.terminals.lastOrNull { it.type == CommandType.CONTINUE }
                    ?: body.terminals.last()
                tail.next = end.id
            }
            // 空本体は FOR_START の trueNext を対向ノードへ置く（実行層の即時退出経路）です。
            start.trueNext = body.entry ?: end.id
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
                    return StmtChain(pre.first().id, Junction(), listOf(node))
                }
            }
            return terminalChain(node)
        }

        // ---- call 文 ----

        private fun lowerCallDisk(stmt: DslCallDisk): StmtChain =
            lowerCallResult(DslCallResult(stmt.diskId, stmt.args, stmt.pos), receiver = null)

        /**
         * `call "name"(arg = 値, ...)` を DISK_CALL ノードへ写像します。
         *
         * 引数は呼出し先の `defarg` 宣言（diskResolver が返す DiskArgument）と
         * 照合します。実行内変数参照は `argvar_<名>`（参照渡し）、リテラル・
         * ワールド変数・補間文字列は `arg_<名>`（テンプレート文字列）へ書き、
         * 複合式は一時変数へ実体化した上で `arg_<名>` の単一参照を使います。
         * `arg_<名>` は宣言型のスカラー値として解釈されるため、複合型
         * （location/item/block/entity）の引数は実行内変数でのみ指定できます。
         *
         * 宣言に無い引数名・型の不一致はここで拒否します。宣言側に存在するが
         * 呼出しで未指定の引数は `arg_` を書かないまま残し、GUI の半設定状態と
         * 同じく検証層（ExecutableScriptValidator）の検出へ委ねます。
         *
         * `receiver` は戻り値の受取先（`x = call ".."`）で、returnName/
         * returnScope へ写像します。エンティティ変数は戻り値を受け取れません。
         */
        private fun lowerCallResult(call: DslCallResult, receiver: DslAssignTarget?): StmtChain {
            val name = call.diskId.plainText()
                ?: error(call.pos, "call の対象は文字列リテラルで指定してください")
            val link = diskResolver(name)
                ?: error(call.pos, "call 対象のディスクが見つかりません: $name")
            // DSL 側の引数名を宣言名（モデルの変数名）へ照合します。
            val provided = LinkedHashMap<String, DslArg>()
            for (arg in call.args) {
                val argName = arg.name ?: error(arg.pos, "call の引数は名前付きで指定してください")
                val argument = link.arguments.firstOrNull { it.name == modelName(argName) }
                    ?: error(arg.pos, "call 対象 '${name}' に引数 '${argName}' は宣言されていません")
                if (provided.put(argument.name, arg) != null) {
                    error(arg.pos, "引数 '${argName}' が重複しています")
                }
            }
            val pre = mutableListOf<CommandNode>()
            val node = node(CommandType.DISK_CALL)
            node.params["diskId"] = link.diskId.toString()
            node.params["declaredArgs"] = encodeDeclaredArguments(link.arguments)
            node.snapshot = link.snapshot.deepCopy()
            for (argument in link.arguments) {
                val arg = provided[argument.name] ?: continue
                val value = arg.value
                // `arg = null` はその引数欄が未設定であることを表します。
                if (value is DslNull) continue
                if (value is DslVarRef) {
                    val actual = exprTypes.getValue(value)
                    if (actual != argument.type) {
                        error(value.pos, "引数 '${argument.name}' は ${argument.type.name.lowercase()} 型ですが、${actual.name.lowercase()} 型の変数は指定できません")
                    }
                    node.params["argvar_${argument.name}"] = modelName(value.name)
                    continue
                }
                when (argument.type) {
                    ValueType.NUMBER, ValueType.STRING, ValueType.BOOLEAN -> {
                        val actual = exprTypes.getValue(value)
                        if (actual != argument.type) {
                            error(value.pos, "引数 '${argument.name}' は ${argument.type.name.lowercase()} 型ですが、${actual.name.lowercase()} 型の値は指定できません")
                        }
                        node.params["arg_${argument.name}"] = scalarText(value, argument.type, pre)
                    }
                    // 複合型のリテラル受取はモデルが持たず、ワールド変数は
                    // argvar（実行内変数参照）の対象外のため、いずれも拒否します。
                    else -> error(value.pos, "${argument.type.name.lowercase()} 型の引数 '${argument.name}' は実行内変数で指定してください")
                }
            }
            when (receiver) {
                null -> Unit
                is DslAssignTarget.Variable -> {
                    node.params["returnName"] = modelName(receiver.name)
                    node.params["returnScope"] = VariableScope.EXECUTION.name
                }
                is DslAssignTarget.WorldVariable -> {
                    node.params["returnName"] = modelName(receiver.name)
                    node.params["returnScope"] = VariableScope.WORLD.name
                }
                is DslAssignTarget.EntityVariable ->
                    error(receiver.pos, "call の戻り値はエンティティ変数へ書き込めません")
            }
            return stmtChain(*(pre + node).toTypedArray())
        }

        // ---- 宣言部 ----

        /**
         * `trigger` 宣言を ScriptTrigger へ写像します。
         * 宣言がなければ既定の redstone（`effectiveTrigger` の既定と同一）です。
         */
        fun lowerTriggerDecl(decl: DslTriggerDecl?): ScriptTrigger {
            val spec = decl?.spec ?: return ScriptTrigger(TriggerKind.REDSTONE)
            return when (spec.name) {
                "timer" -> ScriptTrigger(
                    TriggerKind.TIMER,
                    intervalSeconds = literalNumber(spec.args.single().value),
                )
                "event" -> lowerEventTrigger(spec)
                else -> ScriptTrigger(TriggerKind.REDSTONE)
            }
        }

        /** `trigger event(...)` の詳細条件を EventTriggerConditions へ写像します。 */
        private fun lowerEventTrigger(spec: DslCallExpr): ScriptTrigger {
            var kind: EventTriggerKind? = null
            val conditions = EventTriggerConditions()
            var actorTarget: TargetSpec? = null
            var actorId: UUID? = null
            var entityTarget: TargetSpec? = null
            var entityId: UUID? = null
            var blockType: String? = null
            var blockIdentity: TriggerBlockIdentity? = null
            var clickSide: TriggerClickSide? = null
            var leverState: TriggerLeverState? = null
            var blockWorld: String? = null
            var blockX: Int? = null
            var blockY: Int? = null
            var blockZ: Int? = null
            spec.args.forEach { arg ->
                when (arg.name) {
                    "kind" -> kind = EventTriggerKind.parse(literalString(arg.value, "kind"))
                    "actor" -> actorTarget = targetSpecOf(arg.value)
                    "entity" -> entityTarget = targetSpecOf(arg.value)
                    "side" -> clickSide = when (literalString(arg.value, "side")) {
                        "left" -> TriggerClickSide.LEFT
                        else -> TriggerClickSide.RIGHT
                    }
                    "state" -> leverState = when (literalString(arg.value, "state")) {
                        "on" -> TriggerLeverState.ON
                        else -> TriggerLeverState.OFF
                    }
                    "block" -> blockType = literalString(arg.value, "block")
                    "actor_id" -> actorId = runCatching {
                        UUID.fromString(literalString(arg.value, "actor_id"))
                    }.getOrNull()
                    "entity_id" -> entityId = runCatching {
                        UUID.fromString(literalString(arg.value, "entity_id"))
                    }.getOrNull()
                    "block_world" -> blockWorld = literalString(arg.value, "block_world")
                    "block_x" -> blockX = literalNumber(arg.value).toInt()
                    "block_y" -> blockY = literalNumber(arg.value).toInt()
                    "block_z" -> blockZ = literalNumber(arg.value).toInt()
                }
            }
            // 個体位置は world・x・y・z が揃ったときだけ構築します。
            if (blockWorld != null && blockX != null && blockY != null && blockZ != null) {
                blockIdentity = TriggerBlockIdentity(blockWorld, blockX, blockY, blockZ)
            }
            return ScriptTrigger(
                kind = TriggerKind.EVENT,
                event = kind,
                conditions = conditions.copy(
                    actorTarget = actorTarget,
                    actorId = actorId,
                    blockType = blockType,
                    blockIdentity = blockIdentity,
                    entityTarget = entityTarget,
                    entityId = entityId,
                    clickSide = clickSide,
                    leverState = leverState,
                ),
            )
        }

        // ---- 命令呼出し ----

        private val commands = DSL_COMMAND_TABLE

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
                // tag_add／tag_remove は1引数ごとに1タグを追記するため重複指定を認めます。
                if (rule?.kind != ArgKind.TAG_OP && !seen.add(arg.name!!)) {
                    error(arg.pos, "引数 '${arg.name}' が重複しています")
                }
                if (rule != null) {
                    applyArg(node, arg, rule, pre)
                } else if (spec.extraParamPrefix != null) {
                    applyExtraParam(node, spec, arg, pre)
                } else {
                    error(arg.pos, "'${stmt.name}' に引数 '${arg.name}' はありません")
                }
            }
            spec.args.filter { it.value.required && it.key !in seen }.keys.firstOrNull()?.let {
                error(stmt.pos, "'${stmt.name}' には必須の引数 '$it' がありません")
            }
            spec.fixedParams.forEach { (key, value) -> node.params[key] = value }
            finishEntityOperation(node, stmt)
            return stmtChain(*(pre + node).toTypedArray())
        }

        /**
         * `summon(entity = ..., <prop> = <値>)` の型付きデータ引数です。
         * 引数名は EntityDataSchema の項目ID（`ai`・`custom_name` 等）に限り、
         * `summonData_<id>` 欄へ文字列化して書き込みます。
         */
        private fun applyExtraParam(node: CommandNode, spec: CommandSpec, arg: DslArg, pre: MutableList<CommandNode>) {
            val prefix = spec.extraParamPrefix ?: return
            val id = arg.name!!
            val definition = me.awabi2048.kantancommander.model.EntityDataSchema.definition(id)
                ?: error(arg.pos, "'summon' に項目 '${id}' はありません")
            val text = when (definition.valueKind) {
                me.awabi2048.kantancommander.model.EntityPropertyValueKind.BOOLEAN ->
                    scalarText(arg.value, ValueType.BOOLEAN, pre)
                me.awabi2048.kantancommander.model.EntityPropertyValueKind.INTEGER ->
                    scalarText(arg.value, ValueType.NUMBER, pre)
                me.awabi2048.kantancommander.model.EntityPropertyValueKind.ENUM,
                me.awabi2048.kantancommander.model.EntityPropertyValueKind.TEXT,
                -> scalarText(arg.value, ValueType.STRING, pre)
            }
            node.params["$prefix$id"] = text
        }

        /**
         * ENTITY_OPERATION の `value` 引数をパス種別ごとの欄へ振り分けます。
         * `property:<id>` → `propertyValue`、`attribute:<id>` → `attributeValue`。
         * パスが tag／未設定のときの `value` 指定はエラーです。
         */
        private fun finishEntityOperation(node: CommandNode, stmt: DslCallStmt) {
            if (node.type != CommandType.ENTITY_OPERATION) return
            val path = node.params["entityDataPath"] ?: ""
            if (path.isBlank()) {
                // パス未設定は旧GUIの半設定状態をそのまま写します。
                // 実行側では未解決として扱われ、value 指定だけは拒否します。
                if (node.params.remove("__dslValue") != null) {
                    error(stmt.pos, "entity_operation の value は property/attribute の指定時のみ使用できます")
                }
                return
            }
            val pending = node.params.remove("__dslValue") ?: return
            when {
                path.startsWith("property:") -> node.params["propertyValue"] = pending
                path.startsWith("attribute:") -> node.params["attributeValue"] = pending
                else -> error(stmt.pos, "entity_operation の value は property/attribute の指定時のみ使用できます")
            }
        }

        /** 1引数をルールに従ってノード欄・構造値・実体化ノードへ写像します。 */
        private fun applyArg(
            node: CommandNode,
            arg: DslArg,
            rule: ArgRule,
            pre: MutableList<CommandNode>,
        ) {
            // `arg = null` はその設定欄の未設定を表します。
            // spec スロットは未設定のまま、param 欄は既定値（空欄）へ戻します。
            if (arg.value is DslNull) {
                rule.param?.let { node.params.remove(it) }
                when (rule.kind) {
                    ArgKind.ITEM -> { node.params.remove("item"); node.params.remove("itemData") }
                    ArgKind.BLOCK -> node.params.remove("block")
                    ArgKind.PATH_PROPERTY, ArgKind.PATH_ATTRIBUTE -> node.params.remove("entityDataPath")
                    else -> Unit
                }
                return
            }
            when (rule.kind) {
                ArgKind.NUMBER -> node.params[rule.param!!] = scalarText(arg.value, ValueType.NUMBER, pre)
                ArgKind.STRING -> node.params[rule.param!!] = scalarText(arg.value, ValueType.STRING, pre)
                ArgKind.BOOLEAN -> node.params[rule.param!!] = scalarText(arg.value, ValueType.BOOLEAN, pre)
                ArgKind.LITERAL -> {
                    val text = literalString(arg.value, arg.name!!)
                    if (rule.enumValues != null && text !in rule.enumValues) {
                        error(arg.pos, "引数 '${arg.name}' は ${rule.enumValues.joinToString("/")} のいずれかです")
                    }
                    node.params[rule.param!!] = if (rule.uppercase) text.uppercase() else text
                }
                ArgKind.SLOT -> node.params[rule.param!!] = when (val v = arg.value) {
                    is DslNumberLiteral -> v.value.toInt().toString()
                    is DslStringLiteral -> v.plainText()?.uppercase()
                        ?: error(arg.pos, "slot は文字列リテラルで指定してください")
                    else -> error(arg.pos, "slot は文字列または数値リテラルで指定してください")
                }
                ArgKind.PATH_PROPERTY ->
                    node.params["entityDataPath"] = "property:" + literalString(arg.value, "property")
                ArgKind.PATH_ATTRIBUTE ->
                    node.params["entityDataPath"] = "attribute:" + literalString(arg.value, "attribute")
                ArgKind.TAG_OP -> {
                    // タグ操作はパス種別として tag を選択した状態と同義です。
                    node.params["entityDataPath"] = "tag"
                    val entry = rule.param!! + "=" + literalString(arg.value, arg.name!!)
                    val existing = node.params["tagEntries"]
                    node.params["tagEntries"] =
                        if (existing.isNullOrBlank()) entry else "$existing;$entry"
                }
                ArgKind.ENTITY -> assignSpec(node, rule.slot!!, targetSpecOf(arg.value))
                ArgKind.LOCATION -> if (rule.slot == ArgSlot.DEST_FACING) {
                    node.destinationFacingSpec = facingSpecOf(arg.value, pre)
                } else if (rule.slot == ArgSlot.DESTINATION && isEntityPositionArg(arg.value)) {
                    // TELEPORT の移動先は位置式と対象式で別欄を持ちます。
                    // `to = target(...)`／entity 変数は destinationTargetSpec です。
                    node.destinationTargetSpec = targetSpecOf(arg.value)
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
                ArgSlot.DEST_TARGET -> node.destinationTargetSpec = spec as TargetSpec
                ArgSlot.DEST_FACING -> node.destinationFacingSpec = spec as FacingSpec
                ArgSlot.COND_POS -> node.conditionPositionSpec = spec as PositionSpec
                ArgSlot.EXEC_POS -> node.executionPositionSpec = spec as PositionSpec
                ArgSlot.SOUND_POS -> node.soundPositionSpec = spec as PositionSpec
                ArgSlot.SUMMON_POS -> node.summonPositionSpec = spec as PositionSpec
                ArgSlot.PARTICLE_POS -> node.particlePositionSpec = spec as PositionSpec
                ArgSlot.PARTICLE_END -> node.particleEndPositionSpec = spec as PositionSpec
                ArgSlot.BLOCK_POS -> node.blockPositionSpec = spec as PositionSpec
                ArgSlot.BLOCK_FROM -> node.blockFromSpec = spec as PositionSpec
                ArgSlot.BLOCK_TO -> node.blockToSpec = spec as PositionSpec
                ArgSlot.FIREWORK_POS -> node.fireworkPositionSpec = spec as PositionSpec
                ArgSlot.STRUCTURE_POS -> node.structurePlacePositionSpec = spec as PositionSpec
                ArgSlot.GROUP_PIVOT -> node.groupPivotPositionSpec = spec as PositionSpec
            }
        }

        /**
         * teleport の `to` が対象式（`target(...)` か entity 型変数）かを返します。
         * 対象式は `destinationSpec`（位置）ではなく `destinationTargetSpec` へ写像します。
         */
        private fun isEntityPositionArg(expr: DslExpr): Boolean =
            exprTypes[expr] == ValueType.ENTITY

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
                    is DslSpecialPosition -> specialPositionKind(expr.base)
                    else -> error(expr.base.pos, "位置リテラルの基準は変数・disk・captured のみ指定できます")
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
            is DslCallExpr -> when (expr.name) {
                "location" -> {
                    val cx = componentArg(expr, "x")
                    val cy = componentArg(expr, "y")
                    val cz = componentArg(expr, "z")
                    val cyaw = componentArg(expr, "yaw")
                    val cpitch = componentArg(expr, "pitch")
                    PositionSpec(
                        kind = PositionKind.COORDINATES,
                        x = cx?.first, y = cy?.first, z = cz?.first,
                        yaw = cyaw?.first?.toFloat(), pitch = cpitch?.first?.toFloat(),
                        semanticsX = cx?.second ?: CoordinateSemantics.ABSOLUTE,
                        semanticsY = cy?.second ?: CoordinateSemantics.ABSOLUTE,
                        semanticsZ = cz?.second ?: CoordinateSemantics.ABSOLUTE,
                        semanticsYaw = cyaw?.second ?: CoordinateSemantics.ABSOLUTE,
                        semanticsPitch = cpitch?.second ?: CoordinateSemantics.ABSOLUTE,
                    )
                }
                "target" -> PositionSpec(PositionKind.TARGET, target = targetSpecOf(expr))
                "myworld_spawn" -> PositionSpec(
                    kind = PositionKind.MYWORLD_SPAWN,
                    myworldSpawn = when (
                        expr.args.firstOrNull { it.name == "kind" }?.let { literalString(it.value, "kind") }
                    ) {
                        null, "member" -> me.awabi2048.kantancommander.model.MyworldSpawnKind.MEMBER
                        "guest" -> me.awabi2048.kantancommander.model.MyworldSpawnKind.GUEST
                        else -> error(expr.pos, "myworld_spawn の kind は member/guest のいずれかです")
                    },
                )
                else -> error(expr.pos, "'${expr.name}' は位置として使えません")
            }
            is DslSpecialPosition -> specialPositionSpec(expr)
            is DslVarRef -> PositionSpec(PositionKind.TEMPORARY, tempName = modelName(expr.name))
            is DslWorldRef -> error(expr.pos, "ワールド変数の位置は位置式へ使えません")
            else -> {
                val temp = freshTemp()
                val plan = valuePlan(expr, ValueType.LOCATION, pre)
                pre += varNode(VariableScope.EXECUTION, temp, ValueType.LOCATION, plan)
                PositionSpec(PositionKind.TEMPORARY, tempName = temp)
            }
        }

        /** `disk`・`captured` などの予約位置式の種別です。 */
        private fun specialPositionKind(expr: DslSpecialPosition): PositionKind = when (expr.name) {
            "disk" -> PositionKind.DISK
            "captured" -> PositionKind.CAPTURED
            else -> error(expr.pos, "不明な位置式 '${expr.name}' です")
        }

        private fun specialPositionSpec(expr: DslSpecialPosition): PositionSpec =
            PositionSpec(specialPositionKind(expr))

        /** `target(...)` または変数参照を TargetSpec へ写像します。 */
        private fun targetSpecOf(expr: DslExpr): TargetSpec = when (expr) {
            is DslVarRef -> TargetSpec(TargetKind.TEMPORARY, tempName = modelName(expr.name))
            is DslCallExpr -> when (expr.name) {
                "target" -> targetSpecFromArgs(expr.args, expr.pos)
                else -> error(expr.pos, "'${expr.name}' は対象として使えません")
            }
            else -> error(expr.pos, "対象は target(...) または entity 型変数で指定してください")
        }

        /**
         * `target(...)`・`player(...)`・`exists(...)` 共通の引数群から
         * TargetSpec を構築します。`target =` 引数は呼出し側が個別に処理するため
         * ここでは kind 系の絞り込みだけを読みます。
         */
        private fun targetSpecFromArgs(args: List<DslArg>, pos: DslPos): TargetSpec =
            TargetSpec(
                kind = when (
                    args.firstOrNull { it.name == "kind" }?.let { literalString(it.value, "kind") }
                        ?: error(pos, "対象には kind = ... の指定が必要です")
                ) {
                    "nearest_player" -> TargetKind.NEAREST_PLAYER
                    "nearby_players" -> TargetKind.NEARBY_PLAYERS
                    "all_players" -> TargetKind.ALL_PLAYERS
                    "random_player" -> TargetKind.RANDOM_PLAYER
                    "nearest_entity" -> TargetKind.NEAREST_ENTITY
                    "nearby_entities" -> TargetKind.NEARBY_ENTITIES
                    else -> error(pos, "target の kind が不明です")
                },
                entityType = args.firstOrNull { it.name == "type" }?.let { literalString(it.value, "type") },
                maximumDistance = args.firstOrNull { it.name == "within" }?.let { literalNumber(it.value) },
                minimumDistance = args.firstOrNull { it.name == "farther" }?.let { literalNumber(it.value) },
                limit = args.firstOrNull { it.name == "limit" }?.let { literalNumber(it.value).toInt() },
                sort = args.firstOrNull { it.name == "sort" }?.let {
                    when (literalString(it.value, "sort")) {
                        "nearest" -> TargetSort.NEAREST
                        "furthest" -> TargetSort.FURTHEST
                        "random" -> TargetSort.RANDOM
                        else -> error(it.pos, "sort は nearest/furthest/random のいずれかです")
                    }
                },
                gameMode = args.firstOrNull { it.name == "gamemode" }?.let { gamemodeConst(it.value) },
                tag = args.firstOrNull { it.name == "tag" }?.let { literalString(it.value, "tag") },
                name = args.firstOrNull { it.name == "name" }?.let { literalString(it.value, "name") },
                dx = args.firstOrNull { it.name == "dx" }?.let { literalNumber(it.value) },
                dy = args.firstOrNull { it.name == "dy" }?.let { literalNumber(it.value) },
                dz = args.firstOrNull { it.name == "dz" }?.let { literalNumber(it.value) },
            )

        /**
         * `facing` 引数（向き指定）を FacingSpec へ写像します。
         * - `location(x=..,y=..,z=..)`: 座標点への注視（COORDINATES）
         * - `location(yaw=..,pitch=..)`: 角度指定（ROTATION、省略成分は 0）
         * - `target(...)`: 対象への注視（TARGET）
         * - `orientation(...)`: 対象と同じ向き（COPY_ORIENTATION）
         * - location 型変数: TEMPORARY 参照
         */
        private fun facingSpecOf(expr: DslExpr, pre: MutableList<CommandNode>): FacingSpec = when (expr) {
            is DslCallExpr -> when (expr.name) {
                "location" -> {
                    val pointArgs = expr.args.filter { it.name in setOf("x", "y", "z") }
                    if (pointArgs.isNotEmpty()) {
                        // 座標点の注視です。ROTATION との混在（x と yaw の併用）は
                        // モデルの種別表現で一意にならないため拒否します。
                        if (expr.args.any { it.name in setOf("yaw", "pitch") }) {
                            error(expr.pos, "facing の location は座標(x,y,z)か角度(yaw,pitch)のどちらか一方で指定してください")
                        }
                        FacingSpec(
                            kind = FacingKind.COORDINATES,
                            x = literalNumberArg(expr, "x"),
                            y = literalNumberArg(expr, "y"),
                            z = literalNumberArg(expr, "z"),
                        )
                    } else {
                        // ROTATION は有限値必須のため、省略成分は 0 へ正規化します。
                        // `~`/`^` 接頭辞は成分ごとの相対角度として保持します。
                        val cyaw = componentArg(expr, "yaw")
                        val cpitch = componentArg(expr, "pitch")
                        FacingSpec(
                            kind = FacingKind.ROTATION,
                            yaw = cyaw?.first?.toFloat() ?: 0f,
                            pitch = cpitch?.first?.toFloat() ?: 0f,
                            semanticsYaw = cyaw?.second ?: CoordinateSemantics.ABSOLUTE,
                            semanticsPitch = cpitch?.second ?: CoordinateSemantics.ABSOLUTE,
                        )
                    }
                }
                "target" -> FacingSpec(FacingKind.TARGET, target = targetSpecOf(expr))
                "orientation" -> expr.args.singleOrNull()?.let { arg ->
                    FacingSpec(FacingKind.COPY_ORIENTATION, target = targetSpecOf(arg.value))
                } ?: error(expr.pos, "orientation は対象を1個だけ指定してください")
                else -> error(expr.pos, "'${expr.name}' は facing へ使えません")
            }
            is DslVarRef -> FacingSpec(FacingKind.TEMPORARY, tempName = modelName(expr.name))
            else -> error(expr.pos, "facing は location(...)・target(...)・orientation(...)・location 型変数で指定してください")
        }

        /**
         * 位置・向きの成分引数（値と座標セマンティクス）を読みます。
         * `~`/`^` 接頭辞付き数値は相対・ローカル成分として保持します。
         */
        private fun componentArg(expr: DslCallExpr, name: String): Pair<Double, CoordinateSemantics>? =
            expr.args.firstOrNull { it.name == name }?.let { arg ->
                when (val v = arg.value) {
                    is DslComponentExpr -> v.value to v.semantics
                    else -> literalNumber(v) to CoordinateSemantics.ABSOLUTE
                }
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
            val entry = compileBlock(program.body, exit).entry
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

/**
 * 1文分の構造です。entry は開始ノード、exit は後続へ渡す開放辺の集まり、
 * terminals はこの文が作った終端ノード（BREAK/CONTINUE/RETURN_VALUE など
 * 開放辺を持たない末端）をプログラム順に保持します。FOR_END への構造辺の
 * 供給元として repeat 低下時に使います。
 */
private class StmtChain(
    val entry: UUID?,
    val exit: Junction,
    val terminals: List<CommandNode> = emptyList(),
)

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
    /** 乱数生成（RANDOM）です。`min..max` の範囲文字列を range 欄へ書きます。 */
    data class RandomRange(val range: String) : ValuePlan
}

/** 命令の引数仕様です。slot は構造値の格納先、param は params 欄です。 */
internal enum class ArgKind {
    NUMBER, STRING, BOOLEAN, ENTITY, LOCATION, ITEM, BLOCK, LITERAL,
    /**
     * PLACE_ITEM の slot です。文字列（`"hand"`/`"contents"`）は大文字へ、
     * 数値は整数文字列へ正規化します。
     */
    SLOT,
    /** ENTITY_OPERATION の `property = "id"` です。`entityDataPath` へ `property:<id>` を書きます。 */
    PATH_PROPERTY,
    /** ENTITY_OPERATION の `attribute = "id"` です。`entityDataPath` へ `attribute:<id>` を書きます。 */
    PATH_ATTRIBUTE,
    /**
     * ENTITY_OPERATION の `tag_add`／`tag_remove` です。param に `add`/`remove` を持ち、
     * `tagEntries` へ `<op>=<tag>` を追記します。
     */
    TAG_OP,
}
internal enum class ArgSlot {
    TARGET, SECONDARY_TARGET, DESTINATION, DEST_FACING, EXEC_POS,
    SOUND_POS, SUMMON_POS, PARTICLE_POS, PARTICLE_END, BLOCK_POS, BLOCK_FROM, BLOCK_TO,
    FIREWORK_POS, STRUCTURE_POS, GROUP_PIVOT, DEST_TARGET, COND_POS,
}
internal data class ArgRule(
    val kind: ArgKind,
    val required: Boolean,
    val param: String? = null,
    val slot: ArgSlot? = null,
    /** LITERAL 引数の許容値です。DSL では小文字で書きます。 */
    val enumValues: Set<String>? = null,
    /** LITERAL 値を大文字へ正規化して保存します（placeDestination・particleShape 等）。 */
    val uppercase: Boolean = false,
)
internal data class CommandSpec(
    val type: CommandType,
    val args: Map<String, ArgRule>,
    val fixedParams: Map<String, String> = emptyMap(),
    /**
     * `summon` の型付きデータ欄のように、テーブル未定義の引数名を
     * `<prefix><引数名>` の params キーとして受け入れる接頭辞です。
     * 値は EntityDataSchema の項目定義で検証します。
     */
    val extraParamPrefix: String? = null,
)

private fun compoundSymbol(op: DslAssignOp): String = when (op) {
    DslAssignOp.ADD -> "+"; DslAssignOp.SUB -> "-"; DslAssignOp.MUL -> "*"
    DslAssignOp.DIV -> "/"; DslAssignOp.MOD -> "%"; DslAssignOp.ASSIGN -> "="
}

/**
 * DSL の組み込み命令テーブルです。コンパイラ（DSL→モデル）と
 * プリンタ（モデル→DSL）が同じ引数写像を共有します。
 */
internal val DSL_COMMAND_TABLE: Map<String, CommandSpec> = mapOf(
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
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.EXEC_POS),
            )),
            "summon" to CommandSpec(CommandType.SUMMON_ENTITY, mapOf(
                "entity" to ArgRule(ArgKind.LITERAL, required = true, param = "summonEntity"),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.SUMMON_POS),
                "tags" to ArgRule(ArgKind.STRING, required = false, param = "tags"),
            ), extraParamPrefix = "summonData_"),
            "play_sound" to CommandSpec(CommandType.PLAY_SOUND, mapOf(
                "sound" to ArgRule(ArgKind.LITERAL, required = true, param = "sound"),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.SOUND_POS),
                "origin" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.EXEC_POS),
                // 再生対象（指定時はその対象へ向けて鳴らします）。
                "target" to ArgRule(ArgKind.ENTITY, required = false, slot = ArgSlot.TARGET),
                "volume" to ArgRule(ArgKind.NUMBER, required = false, param = "volume"),
                "pitch" to ArgRule(ArgKind.NUMBER, required = false, param = "pitch"),
                "scope" to ArgRule(ArgKind.LITERAL, required = false, param = "soundScope",
                    enumValues = setOf("position", "world"), uppercase = true),
                "category" to ArgRule(ArgKind.LITERAL, required = false, param = "soundCategory"),
                "operation" to ArgRule(ArgKind.LITERAL, required = false, param = "soundOperation",
                    enumValues = setOf("play", "stop"), uppercase = true),
            )),
            "entity_delete" to CommandSpec(CommandType.ENTITY_DELETE, mapOf(
                "target" to ArgRule(ArgKind.ENTITY, required = true, slot = ArgSlot.TARGET),
            )),
            "apply_effect" to CommandSpec(CommandType.APPLY_EFFECT, mapOf(
                "target" to ArgRule(ArgKind.ENTITY, required = true, slot = ArgSlot.TARGET),
                "effect" to ArgRule(ArgKind.LITERAL, required = true, param = "effect"),
                "seconds" to ArgRule(ArgKind.NUMBER, required = false, param = "seconds"),
                "level" to ArgRule(ArgKind.NUMBER, required = false, param = "level"),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.EXEC_POS),
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
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.EXEC_POS),
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
                "origin" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.EXEC_POS),
            )),
            "particle" to CommandSpec(CommandType.PARTICLE, mapOf(
                "particle" to ArgRule(ArgKind.LITERAL, required = true, param = "particle"),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.PARTICLE_POS),
                // 表示対象（複数対象の位置群へ0..N件描画します）。
                "target" to ArgRule(ArgKind.ENTITY, required = false, slot = ArgSlot.TARGET),
                "origin" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.EXEC_POS),
                "count" to ArgRule(ArgKind.NUMBER, required = false, param = "particleCount"),
                "speed" to ArgRule(ArgKind.NUMBER, required = false, param = "particleSpeed"),
                "delta_x" to ArgRule(ArgKind.NUMBER, required = false, param = "particleDeltaX"),
                "delta_y" to ArgRule(ArgKind.NUMBER, required = false, param = "particleDeltaY"),
                "delta_z" to ArgRule(ArgKind.NUMBER, required = false, param = "particleDeltaZ"),
                "data" to ArgRule(ArgKind.STRING, required = false, param = "particleData"),
            )),
            "particle_geometry" to CommandSpec(CommandType.PARTICLE_GEOMETRY, mapOf(
                "particle" to ArgRule(ArgKind.LITERAL, required = true, param = "particle"),
                "shape" to ArgRule(ArgKind.LITERAL, required = false, param = "particleShape",
                    enumValues = setOf("point_scatter", "line", "circle", "arc", "box", "sphere", "helix"),
                    uppercase = true),
                "data" to ArgRule(ArgKind.STRING, required = false, param = "particleData"),
                "density" to ArgRule(ArgKind.NUMBER, required = false, param = "particleDensity"),
                "radius" to ArgRule(ArgKind.NUMBER, required = false, param = "geometryRadius"),
                "height" to ArgRule(ArgKind.NUMBER, required = false, param = "geometryHeight"),
                "turns" to ArgRule(ArgKind.NUMBER, required = false, param = "geometryTurns"),
                "start_angle" to ArgRule(ArgKind.NUMBER, required = false, param = "geometryStartAngle"),
                "end_angle" to ArgRule(ArgKind.NUMBER, required = false, param = "geometryEndAngle"),
                "rotation" to ArgRule(ArgKind.NUMBER, required = false, param = "geometryRotation"),
                "axis_yaw" to ArgRule(ArgKind.NUMBER, required = false, param = "geometryAxisYaw"),
                "axis_pitch" to ArgRule(ArgKind.NUMBER, required = false, param = "geometryAxisPitch"),
                "step" to ArgRule(ArgKind.NUMBER, required = false, param = "geometryStep"),
                "wireframe" to ArgRule(ArgKind.BOOLEAN, required = false, param = "geometryWireframe"),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.PARTICLE_POS),
                "end_at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.PARTICLE_END),
                "target" to ArgRule(ArgKind.ENTITY, required = false, slot = ArgSlot.TARGET),
                "origin" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.EXEC_POS),
            )),
            "place_item" to CommandSpec(CommandType.PLACE_ITEM, mapOf(
                "item" to ArgRule(ArgKind.ITEM, required = true),
                "destination" to ArgRule(ArgKind.LITERAL, required = false, param = "placeDestination",
                    enumValues = setOf("equipment", "player_hotbar", "player_inventory",
                        "item_frame", "block_container"), uppercase = true),
                "slot" to ArgRule(ArgKind.SLOT, required = false, param = "slot"),
                "count" to ArgRule(ArgKind.NUMBER, required = false, param = "count"),
                "overwrite" to ArgRule(ArgKind.BOOLEAN, required = false, param = "overwrite"),
                // ENTITY 系配置先の対象（equipment/hotbar/inventory/item_frame）。
                "target" to ArgRule(ArgKind.ENTITY, required = false, slot = ArgSlot.TARGET),
                // BLOCK_CONTAINER の対象コンテナ位置です。
                "pos" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.BLOCK_POS),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.EXEC_POS),
            )),
            "entity_operation" to CommandSpec(CommandType.ENTITY_OPERATION, mapOf(
                "target" to ArgRule(ArgKind.ENTITY, required = true, slot = ArgSlot.TARGET),
                "property" to ArgRule(ArgKind.PATH_PROPERTY, required = false),
                "attribute" to ArgRule(ArgKind.PATH_ATTRIBUTE, required = false),
                // 値はパス種別で保存欄が分かれるため、finishEntityOperation が
                // propertyValue／attributeValue へ振り分けます。
                "value" to ArgRule(ArgKind.STRING, required = false, param = "__dslValue"),
                "tag_add" to ArgRule(ArgKind.TAG_OP, required = false, param = "add"),
                "tag_remove" to ArgRule(ArgKind.TAG_OP, required = false, param = "remove"),
            )),
            "place_structure" to CommandSpec(CommandType.STRUCTURE_PLACE, mapOf(
                "structure" to ArgRule(ArgKind.LITERAL, required = true, param = "structure"),
                "at" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.STRUCTURE_POS),
                "rotation" to ArgRule(ArgKind.LITERAL, required = false, param = "rotation",
                    enumValues = setOf("none", "clockwise_90", "clockwise_180", "counterclockwise_90")),
                "mirror" to ArgRule(ArgKind.LITERAL, required = false, param = "mirror",
                    enumValues = setOf("none", "left_right", "front_back")),
            )),
            "entity_group" to CommandSpec(CommandType.ENTITY_GROUP, mapOf(
                "operation" to ArgRule(ArgKind.LITERAL, required = true, param = "operation",
                    enumValues = setOf("create", "add", "remove", "delete", "set_pivot", "move", "rotate", "cleanup")),
                "group" to ArgRule(ArgKind.STRING, required = false, param = "group"),
                "target" to ArgRule(ArgKind.ENTITY, required = false, slot = ArgSlot.TARGET),
                "pivot" to ArgRule(ArgKind.LOCATION, required = false, slot = ArgSlot.GROUP_PIVOT),
                "move_x" to ArgRule(ArgKind.NUMBER, required = false, param = "moveX"),
                "move_y" to ArgRule(ArgKind.NUMBER, required = false, param = "moveY"),
                "move_z" to ArgRule(ArgKind.NUMBER, required = false, param = "moveZ"),
                "rotate_x" to ArgRule(ArgKind.NUMBER, required = false, param = "rotateX"),
                "rotate_y" to ArgRule(ArgKind.NUMBER, required = false, param = "rotateY"),
                "rotate_z" to ArgRule(ArgKind.NUMBER, required = false, param = "rotateZ"),
            )),
        )
