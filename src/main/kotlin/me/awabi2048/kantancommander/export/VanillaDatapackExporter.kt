package me.awabi2048.kantancommander.export

import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskCallMode
import me.awabi2048.kantancommander.model.DiskScript
import org.bukkit.Material
import java.io.File
import java.util.UUID

class VanillaDatapackExporter(private val scripts: ScriptStore, private val outputRoot: File) {
    fun export(root: DiskScript): ExportResult {
        val collected = linkedMapOf(root.id to root)
        val errors = mutableListOf<String>()
        collect(root, collected, errors, mutableSetOf())
        collected.values.forEach { validate(it, errors) }
        if (errors.isNotEmpty()) return ExportResult.Failure(errors.distinct())

        val pack = outputRoot.resolve("kantan-${root.id}")
        val functions = pack.resolve("data/kantan/function").also(File::mkdirs)
        pack.resolve("pack.mcmeta").writeText("""{"pack":{"pack_format":88,"description":"Kantan Commander export"}}""")
        collected.values.forEach { script ->
            val compiled = compile(script)
            compiled.forEach { (name, content) -> functions.resolve("$name.mcfunction").writeText(content, Charsets.UTF_8) }
        }
        return ExportResult.Success(pack)
    }

    private fun collect(script: DiskScript, all: MutableMap<UUID, DiskScript>, errors: MutableList<String>, active: MutableSet<UUID>) {
        if (!active.add(script.id)) {
            errors += "別ディスク参照が循環しています: ${script.id}"
            return
        }
        script.graph.nodes.values.filter { it.type == CommandType.DISK_CALL }.forEach { node ->
            if (node.string("mode") == DiskCallMode.SNAPSHOT.name) return@forEach
            val id = runCatching { UUID.fromString(node.string("diskId")) }.getOrNull()
            val target = id?.let(scripts::load)
            if (target == null) errors += "参照ディスクが存在しません: ${node.string("diskId")}"
            else if (all.putIfAbsent(target.id, target) == null) collect(target, all, errors, active)
        }
        active.remove(script.id)
    }

    private fun validate(script: DiskScript, errors: MutableList<String>) {
        script.graph.nodes.values.forEach { node ->
            when (node.type) {
                CommandType.GIVE_ITEM -> {
                    val item = node.string("item")
                    if (!item.startsWith("minecraft:") || Material.matchMaterial(item) == null) {
                        errors += "${script.id}/${node.id}: バニラに存在しないアイテムです: $item"
                    }
                }
                CommandType.ENTITY_ACTION -> if (node.string("action") !in setOf("ride", "dismount")) {
                    errors += "${script.id}/${node.id}: プラグイン固有のエンティティ操作です"
                }
                CommandType.TELEPORT -> if (node.string("world").isNotBlank()) {
                    errors += "${script.id}/${node.id}: 出力先ワールドを検証できない固定ワールド参照です"
                }
                CommandType.DISK_CALL -> if (node.string("mode") == DiskCallMode.SNAPSHOT.name && node.snapshot == null) {
                    errors += "${script.id}/${node.id}: コピー内容がありません"
                }
                else -> Unit
            }
        }
    }

    private fun compile(script: DiskScript): Map<String, String> {
        val output = linkedMapOf<String, String>()
        compileGraph(script.graph, script.id.toString(), output)
        return output
    }

    private fun compileGraph(graph: CommandGraph, prefix: String, output: MutableMap<String, String>) {
        val entry = graph.entryNodeId
        output[prefix] = entry?.let { "function kantan:${prefix}_$it\n" } ?: "# empty\n"
        graph.nodes.values.forEach { node ->
            val lines = mutableListOf<String>()
            if (node.type == CommandType.DISK_CALL && node.string("mode") == DiskCallMode.SNAPSHOT.name) {
                val snapshotPrefix = "${prefix}_snapshot_${node.id}"
                node.snapshot?.let { compileGraph(it, snapshotPrefix, output) }
                lines += "function kantan:$snapshotPrefix"
            } else {
                lower(node)?.let(lines::add)
            }
            if (node.type == CommandType.CONDITION) {
                val predicate = predicate(node)
                node.trueNext?.let { lines += "execute if $predicate run function kantan:${prefix}_$it" }
                node.falseNext?.let { lines += "execute unless $predicate run function kantan:${prefix}_$it" }
            } else if (node.type == CommandType.WAIT) {
                node.next?.let { lines += "schedule function kantan:${prefix}_$it ${node.int("ticks", 20).coerceAtLeast(1)}t replace" }
            } else {
                node.next?.let { lines += "function kantan:${prefix}_$it" }
            }
            output["${prefix}_${node.id}"] = lines.joinToString("\n", postfix = "\n")
        }
    }

    private fun lower(node: CommandNode): String? = when (node.type) {
        CommandType.TELEPORT -> "tp ${node.string("target", "@s")} ${node.string("destination", "~ ~ ~")}"
        CommandType.GIVE_ITEM -> "give ${node.string("target", "@s")} ${node.string("item")} ${node.int("count", 1)}"
        CommandType.ENTITY_ACTION -> if (node.string("action") == "dismount") "ride ${node.string("target", "@s")} dismount" else "ride ${node.string("target", "@s")} mount ${node.string("other")}"
        CommandType.DISPLAY_TEXT -> when (node.string("mode", "tellraw")) {
            "title" -> "title ${node.string("target", "@s")} title {\"text\":\"${escape(node.string("text"))}\"}"
            "actionbar" -> "title ${node.string("target", "@s")} actionbar {\"text\":\"${escape(node.string("text"))}\"}"
            else -> "tellraw ${node.string("target", "@s")} {\"text\":\"${escape(node.string("text"))}\"}"
        }
        CommandType.WAIT -> null
        CommandType.DISK_CALL -> if (node.string("mode") == DiskCallMode.LIVE_REFERENCE.name) "function kantan:${node.string("diskId")}" else "# embedded snapshot"
        CommandType.CONTEXT -> "# context ${node.params}"
        CommandType.CONDITION, CommandType.MERGE -> null
    }

    private fun predicate(node: CommandNode): String = when (runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrDefault(ConditionKind.TARGET_EXISTS)) {
        ConditionKind.TARGET_EXISTS -> "entity ${node.string("subject", "@s")}"
        ConditionKind.BLOCK_STATE -> "block ${node.string("position", "~ ~ ~")} ${node.string("block", "minecraft:air")}"
        ConditionKind.ITEM_POSSESSION -> "items entity ${node.string("subject", "@s")} inventory.* ${node.string("item", "minecraft:air")}"
        else -> "entity ${node.string("subject", "@s")}"
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}

sealed interface ExportResult {
    data class Success(val directory: File) : ExportResult
    data class Failure(val errors: List<String>) : ExportResult
}
