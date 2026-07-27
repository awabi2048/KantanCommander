package me.awabi2048.kantancommander.export

import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.FacingKind
import org.bukkit.Material
import java.io.File
import java.util.UUID

class VanillaDatapackExporter(private val scripts: ScriptStore, private val outputRoot: File) {
    fun export(root: DiskScript): ExportResult {
        val errors = mutableListOf<String>()
        validate(root, errors)
        if (errors.isNotEmpty()) return ExportResult.Failure(errors.distinct())

        val pack = outputRoot.resolve("kantan-${root.id}")
        val functions = pack.resolve("data/kantan/function").also(File::mkdirs)
        val loadTags = pack.resolve("data/minecraft/tags/function").also(File::mkdirs)
        pack.resolve("pack.mcmeta").writeText("""{"pack":{"pack_format":88,"description":"Kantan Commander export"}}""")
        functions.resolve("load.mcfunction").writeText("scoreboard objectives add kc_result dummy\nscoreboard objectives add kc_vars dummy\n", Charsets.UTF_8)
        loadTags.resolve("load.json").writeText("""{"values":["kantan:load"]}""", Charsets.UTF_8)
        compile(root).forEach { (name, content) ->
            functions.resolve("$name.mcfunction").writeText(content, Charsets.UTF_8)
        }
        return ExportResult.Success(pack)
    }

    private fun validate(script: DiskScript, errors: MutableList<String>) {
        script.graph.nodes.values.forEach { node ->
            when (node.type) {
                CommandType.GIVE_ITEM -> {
                    val item = node.string("item")
                    if (!item.startsWith("minecraft:") || Material.matchMaterial(item) == null) {
                        errors += "${script.id}/${node.id}: バニラに存在しないアイテムです: $item"
                    }
                    if (node.string("itemData").isNotBlank()) {
                        errors += "${script.id}/${node.id}: 保存されたItemStackメタデータは完全バニラ出力に未対応です"
                    }
                }
                CommandType.ENTITY_ACTION -> if (node.string("action") !in setOf("ride", "dismount")) {
                    errors += "${script.id}/${node.id}: プラグイン固有のエンティティ操作です"
                }
                CommandType.TELEPORT -> if (node.string("world").isNotBlank()) {
                    errors += "${script.id}/${node.id}: 出力先ワールドを検証できない固定ワールド参照です"
                }
                CommandType.DISK_CALL -> if (node.snapshot == null) {
                    errors += "${script.id}/${node.id}: コピー内容がありません"
                }
                CommandType.CONDITION -> validateCondition(script, node, errors)
                CommandType.CONTEXT -> validateContext(script, node, contextFrom(node), errors)
                CommandType.VARIABLE -> {
                    if (node.string("name").isBlank()) errors += "${script.id}/${node.id}: variable name is missing"
                    if (runCatching { VariableType.valueOf(node.string("type")) }.getOrNull() !in
                        setOf(VariableType.BOOLEAN, VariableType.INTEGER)
                    ) errors += "${script.id}/${node.id}: variable type is not vanilla-exportable"
                }
                CommandType.FOR_START -> {
                    listOf("start", "end", "step").forEach { field ->
                        if (node.string("${field}Source", "FIXED") == "FIXED" &&
                            node.string("${field}Value").toIntOrNull() == null
                        ) {
                            errors += "${script.id}/${node.id}: forの${field}値はバニラの32bit整数範囲で指定してください"
                        }
                    }
                    if (node.string("stepSource", "FIXED") == "FIXED" && node.string("stepValue").toIntOrNull() == 0) {
                        errors += "${script.id}/${node.id}: forの増分に0は指定できません"
                    }
                }
                else -> Unit
            }
            node.contextOverride?.let { validateContext(script, node, it, errors) }
        }
    }

    private fun validateCondition(script: DiskScript, node: CommandNode, errors: MutableList<String>) {
        val kind = runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrNull()
        if (kind == null) {
            errors += "${script.id}/${node.id}: 不明な条件種別です"
            return
        }
        if (kind == ConditionKind.VARIABLE_STATE && node.string("variable").isBlank()) {
            errors += "${script.id}/${node.id}: 一時変数名がありません"
        }
    }

    private fun validateContext(script: DiskScript, node: CommandNode, context: ExecutionContextSpec, errors: MutableList<String>) {
        if (context.target?.kind == TargetKind.FIXED_ENTITY || context.executor?.kind == TargetKind.FIXED_ENTITY) {
            errors += "${script.id}/${node.id}: 固定エンティティ参照は完全バニラ出力できません"
        }
    }

    private fun compile(script: DiskScript): Map<String, String> =
        linkedMapOf<String, String>().also { compileGraph(script.graph, script.id.toString(), it) }

    private fun compileGraph(graph: CommandGraph, prefix: String, output: MutableMap<String, String>) {
        output[prefix] = graph.entryNodeId?.let { "function kantan:${prefix}_$it\n" } ?: "# empty\n"
        graph.nodes.values.forEach { node ->
            val lines = mutableListOf<String>()
            when {
                node.type == CommandType.FOR_START -> {
                    val loop = loopName(node.id)
                    lines += assignLoopValue(loop, "value", node, "start")
                    lines += assignLoopValue(loop, "end", node, "end")
                    lines += assignLoopValue(loop, "step", node, "step")
                    lines += "scoreboard players set #${loop}_count kc_vars 1"
                    lines += "function kantan:${prefix}_${node.id}_check"
                    output["${prefix}_${node.id}_check"] = loopCheck(graph, prefix, node)
                }
                node.type == CommandType.FOR_END -> {
                    val start = node.pairedNodeId?.let(graph.nodes::get)
                    if (start != null) {
                        val loop = loopName(start.id)
                        lines += assignLoopValue(loop, "step", start, "step")
                        lines += "scoreboard players operation #${loop}_value kc_vars += #${loop}_step kc_vars"
                        lines += "scoreboard players add #${loop}_count kc_vars 1"
                        lines += "function kantan:${prefix}_${start.id}_check"
                    }
                }
                node.type == CommandType.BREAK -> {
                    enclosingFor(graph, node.id)?.pairedNodeId?.let(graph.nodes::get)?.next?.let {
                        lines += "function kantan:${prefix}_$it"
                    }
                }
                node.type == CommandType.CONTINUE -> {
                    enclosingFor(graph, node.id)?.pairedNodeId?.let {
                        lines += "function kantan:${prefix}_$it"
                    }
                }
                node.type == CommandType.DISK_CALL -> {
                    val snapshotPrefix = "${prefix}_snapshot_${node.id}"
                    node.snapshot?.let { compileGraph(it, snapshotPrefix, output) }
                    lines += storeResult(node, "function kantan:$snapshotPrefix")
                }
                node.type == CommandType.CONTEXT -> {
                    node.next?.let { lines += wrapContext(contextFrom(node), "function kantan:${prefix}_$it") }
                }
                else -> lower(node)?.let { command ->
                    val contextual = node.contextOverride?.let { wrapContext(it, command) } ?: command
                    lines += storeResult(node, contextual)
                }
            }

            when (node.type) {
                CommandType.CONDITION -> {
                    val predicate = predicate(node)
                    val trueCheck = if (node.boolean("inverted")) "unless" else "if"
                    val falseCheck = if (node.boolean("inverted")) "if" else "unless"
                    node.trueNext?.let {
                        lines += "execute $trueCheck $predicate run function kantan:${prefix}_$it"
                    }
                    node.falseNext?.let {
                        lines += "execute $falseCheck $predicate run function kantan:${prefix}_$it"
                    }
                }
                CommandType.WAIT ->
                    node.next?.let { lines += "schedule function kantan:${prefix}_$it ${node.int("ticks", 20).coerceAtLeast(1)}t replace" }
                CommandType.CONTEXT -> Unit
                CommandType.FOR_START, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> Unit
                else -> node.next?.let { lines += "function kantan:${prefix}_$it" }
            }
            output["${prefix}_${node.id}"] = lines.joinToString("\n", postfix = "\n")
        }
    }

    private fun lower(node: CommandNode): String? = when (node.type) {
        CommandType.TELEPORT -> "tp ${effectiveTarget(node)} ${node.string("destination", "~ ~ ~")}"
        CommandType.GIVE_ITEM -> "give ${effectiveTarget(node)} ${node.string("item")} ${node.int("count", 1)}"
        CommandType.ENTITY_ACTION ->
            if (node.string("action") == "dismount") "ride ${effectiveTarget(node)} dismount"
            else "ride ${effectiveTarget(node)} mount ${node.string("other")}"
        CommandType.DISPLAY_TEXT -> when (node.string("mode", "tellraw")) {
            "title" -> "title ${effectiveTarget(node)} title {\"text\":\"${escape(node.string("text"))}\"}"
            "actionbar" -> "title ${effectiveTarget(node)} actionbar {\"text\":\"${escape(node.string("text"))}\"}"
            else -> "tellraw ${effectiveTarget(node)} {\"text\":\"${escape(node.string("text"))}\"}"
        }
        CommandType.DISK_CALL -> null
        CommandType.VARIABLE -> lowerVariable(node)
        CommandType.WAIT, CommandType.CONTEXT, CommandType.CONDITION, CommandType.MERGE,
        CommandType.FOR_START, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> null
    }

    private fun predicate(node: CommandNode): String = when (
        ConditionKind.valueOf(node.string("kind", ConditionKind.TARGET_EXISTS.name))
    ) {
        ConditionKind.TARGET_EXISTS -> "entity ${node.string("subject", "@s")}"
        ConditionKind.ENTITY_STATE -> when (node.string("state", "sneaking")) {
            "sneaking" -> "entity ${node.string("subject", "@s")}[nbt={Pose:\"CROUCHING\"}]"
            "on_ground" -> "entity ${node.string("subject", "@s")}[nbt={OnGround:1b}]"
            else -> "entity ${node.string("subject", "@s")}"
        }
        ConditionKind.VARIABLE_STATE ->
            "score ${variableHolder(node.string("variable"))} kc_vars matches ${scoreRange(node.string("operator", ">="), node.int("value"))}"
        ConditionKind.BLOCK_STATE ->
            "block ${node.string("position", "~ ~ ~")} ${node.string("block", "minecraft:air")}"
        ConditionKind.ITEM_POSSESSION ->
            "items entity ${node.string("subject", "@s")} inventory.* ${node.string("item", "minecraft:air")}"
    }

    private fun lowerVariable(node: CommandNode): String? {
        val holder = variableHolder(node.string("name"))
        return when (VariableOperation.valueOf(node.string("operation", VariableOperation.SET.name))) {
            VariableOperation.SET -> "scoreboard players set $holder kc_vars ${node.int("value")}"
            VariableOperation.ADD -> "scoreboard players add $holder kc_vars ${node.int("value")}"
            VariableOperation.SUBTRACT -> "scoreboard players remove $holder kc_vars ${node.int("value")}"
            VariableOperation.CLEAR -> "scoreboard players reset $holder kc_vars"
            VariableOperation.TOGGLE, VariableOperation.STORE_POSITION, VariableOperation.STORE_TARGET -> null
        }
    }

    private fun variableHolder(name: String) =
        "#v_${name.lowercase().replace(Regex("[^a-z0-9_.-]"), "_").take(32)}"

    private fun loopName(id: UUID) = "for_${id.toString().replace("-", "").take(12)}"

    private fun assignLoopValue(loop: String, target: String, node: CommandNode, field: String): String {
        val destination = "#${loop}_$target"
        return if (node.string("${field}Source", "FIXED") == "TEMPORARY") {
            "scoreboard players operation $destination kc_vars = ${variableHolder(node.string("${field}Value"))} kc_vars"
        } else {
            "scoreboard players set $destination kc_vars ${node.string("${field}Value", if (field == "step") "1" else "0")}"
        }
    }

    private fun loopCheck(graph: CommandGraph, prefix: String, start: CommandNode): String {
        val loop = loopName(start.id)
        val body = start.trueNext
        val end = start.pairedNodeId
        val after = end?.let(graph.nodes::get)?.next
        val bodyFunction = body?.takeUnless { it == end }?.let { "function kantan:${prefix}_$it" }
        val lines = mutableListOf<String>()
        lines += assignLoopValue(loop, "end", start, "end")
        lines += "scoreboard players set #${loop}_run kc_vars 0"
        if (bodyFunction != null) {
            lines += "execute if score #${loop}_step kc_vars matches 1.. if score #${loop}_value kc_vars <= #${loop}_end kc_vars run scoreboard players set #${loop}_run kc_vars 1"
            lines += "execute if score #${loop}_step kc_vars matches ..-1 if score #${loop}_value kc_vars >= #${loop}_end kc_vars run scoreboard players set #${loop}_run kc_vars 1"
            lines += "execute if score #${loop}_run kc_vars matches 1 run $bodyFunction"
        }
        after?.let { lines += "execute if score #${loop}_run kc_vars matches 0 run function kantan:${prefix}_$it" }
        return lines.joinToString("\n", postfix = "\n")
    }

    private fun enclosingFor(graph: CommandGraph, target: UUID): CommandNode? =
        graph.nodes.values.firstOrNull { start ->
            start.type == CommandType.FOR_START &&
                reachableBefore(graph, start.trueNext, start.pairedNodeId, target)
        }

    private fun reachableBefore(graph: CommandGraph, start: UUID?, stop: UUID?, target: UUID): Boolean {
        val visited = mutableSetOf<UUID>()
        fun visit(id: UUID?): Boolean {
            if (id == null || id == stop || !visited.add(id)) return false
            if (id == target) return true
            val node = graph.nodes[id] ?: return false
            return when (node.type) {
                CommandType.CONDITION -> visit(node.trueNext) || visit(node.falseNext)
                CommandType.FOR_START -> visit(node.trueNext) || visit(node.pairedNodeId)
                else -> visit(node.next)
            }
        }
        return visit(start)
    }

    private fun effectiveTarget(node: CommandNode): String =
        selector(node.targetSpec ?: node.contextOverride?.target ?: TargetSpec(TargetKind.EXECUTOR))

    private fun contextFrom(node: CommandNode) = node.contextOverride ?: ExecutionContextSpec()

    private fun wrapContext(context: ExecutionContextSpec, command: String): String {
        val clauses = buildList {
            (context.target ?: context.executor)?.let { add("as ${selector(it)}") }
            context.position?.let {
                when (it.kind) {
                    PositionKind.COORDINATES, PositionKind.CAPTURED -> add("positioned ${it.x} ${it.y} ${it.z}")
                    PositionKind.EXECUTOR -> add("at @s")
                    PositionKind.TARGET -> context.target?.let { target -> add("at ${selector(target)}") }
                    else -> Unit
                }
            }
            context.facing?.let { facing ->
                when (facing.kind) {
                    FacingKind.TARGET -> context.target?.let { add("facing entity ${selector(it)} eyes") }
                    FacingKind.COORDINATES -> add("facing ${facing.x} ${facing.y} ${facing.z}")
                    FacingKind.ROTATION, FacingKind.CAPTURED -> add("rotated ${facing.yaw} ${facing.pitch}")
                    else -> Unit
                }
            }
        }
        return if (clauses.isEmpty()) command else "execute ${clauses.joinToString(" ")} run $command"
    }

    private fun storeResult(node: CommandNode, command: String): String =
        "execute store success score ${scoreHolder(node.id)} kc_result run $command"

    private fun scoreHolder(id: UUID) = "#n_${id.toString().replace("-", "")}"

    private fun selector(spec: TargetSpec): String = when (spec.kind) {
        TargetKind.EXECUTOR, TargetKind.ACTIVATOR -> "@s"
        TargetKind.INHERITED_TARGET -> "@s"
        TargetKind.NEAREST_PLAYER -> "@p"
        TargetKind.NEARBY_PLAYERS -> "@a"
        TargetKind.RANDOM_PLAYER -> "@r"
        TargetKind.NEAREST_ENTITY -> "@e[limit=1,sort=nearest]"
        TargetKind.NEARBY_ENTITIES -> "@e"
        TargetKind.FIXED_ENTITY -> "@e[limit=0]"
    }

    private fun scoreRange(operator: String, value: Int): String = when (operator) {
        "==" -> value.toString()
        "!=" -> value.toString()
        ">" -> "${value + 1}.."
        "<" -> "..${value - 1}"
        "<=" -> "..$value"
        else -> "$value.."
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}

sealed interface ExportResult {
    data class Success(val directory: File) : ExportResult
    data class Failure(val errors: List<String>) : ExportResult
}
