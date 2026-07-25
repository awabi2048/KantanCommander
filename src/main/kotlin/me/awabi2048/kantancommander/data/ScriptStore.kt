package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.STRUCTURED_FORMAT_VERSION
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

class ScriptStore(private val dir: File, private val logger: Logger) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    init {
        dir.mkdirs()
    }

    fun create(owner: UUID, name: String): DiskScript =
        DiskScript(name = name, owner = owner).also(::save)

    fun copyForPlacement(source: DiskScript): DiskScript =
        source.copy(id = UUID.randomUUID(), listed = false, graph = source.graph.deepCopy()).also(::save)

    fun copyToLibrary(source: DiskScript, owner: UUID, name: String = source.name): DiskScript =
        source.copy(
            id = UUID.randomUUID(),
            name = name,
            owner = owner,
            createdAt = System.currentTimeMillis(),
            listed = true,
            graph = source.graph.deepCopy(),
        ).also(::save)

    fun load(id: UUID): DiskScript? = file(id).takeIf(File::isFile)?.let(::read)

    fun save(script: DiskScript) {
        require(script.formatVersion == STRUCTURED_FORMAT_VERSION) { "unsupported script format" }
        val validation = GraphValidator.validate(script.graph)
        require(validation.isEmpty()) { validation.joinToString("; ") }
        atomicWrite(file(script.id), gson.toJson(script))
    }

    fun delete(id: UUID) {
        file(id).delete()
    }

    fun listAll(): List<DiskScript> =
        dir.listFiles { file -> file.isFile && file.extension.equals("json", true) }
            ?.mapNotNull(::read)
            ?.sortedBy(DiskScript::createdAt)
            ?: emptyList()

    fun listOwned(owner: UUID): List<DiskScript> = listAll().filter { it.owner == owner && it.listed }

    private fun file(id: UUID) = dir.resolve("$id.json")

    private fun atomicWrite(target: File, content: String) {
        val temporary = target.resolveSibling("${target.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun read(file: File): DiskScript? = try {
        gson.fromJson(file.readText(Charsets.UTF_8), DiskScript::class.java)
            ?.takeIf { it.formatVersion == STRUCTURED_FORMAT_VERSION }
            ?.also { require(GraphValidator.validate(it.graph).isEmpty()) }
    } catch (error: Exception) {
        quarantine(file, error)
        null
    }

    private fun quarantine(file: File, error: Exception) {
        val quarantine = dir.resolve("corrupt").also(File::mkdirs)
        val target = quarantine.resolve("${file.nameWithoutExtension}-${System.currentTimeMillis()}.json")
        runCatching { Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        logger.log(Level.WARNING, "構造化コマンドディスクを隔離しました: ${file.absolutePath}", error)
    }
}

object GraphValidator {
    fun validate(graph: CommandGraph): List<String> {
        val errors = mutableListOf<String>()
        val entry = graph.entryNodeId
        if (entry == null) {
            if (graph.nodes.isNotEmpty()) errors += "entryNodeIdがありません"
            return errors
        }
        if (entry !in graph.nodes) errors += "開始ノードが存在しません"
        graph.nodes.values.forEach { node ->
            node.outgoing().forEach { target ->
                if (target !in graph.nodes) errors += "${node.id}から存在しないノードを参照しています"
            }
            if (node.type == CommandType.CONDITION) {
                if (node.trueNext == null || node.falseNext == null || node.pairedNodeId == null) {
                    errors += "条件分岐${node.id}の枝または合流が未設定です"
                }
                val merge = node.pairedNodeId?.let(graph.nodes::get)
                if (merge?.type != CommandType.MERGE || merge.pairedNodeId != node.id) {
                    errors += "条件分岐${node.id}の対応合流が不正です"
                }
            }
            if (node.type == CommandType.MERGE) {
                val condition = node.pairedNodeId?.let(graph.nodes::get)
                if (condition?.type != CommandType.CONDITION || condition.pairedNodeId != node.id) {
                    errors += "合流${node.id}の対応条件が不正です"
                }
            }
        }
        if (entry in graph.nodes) {
            val visited = mutableSetOf<UUID>()
            val active = mutableSetOf<UUID>()
            fun visit(id: UUID) {
                if (!active.add(id)) {
                    errors += "循環があります: $id"
                    return
                }
                if (!visited.add(id)) {
                    active.remove(id)
                    return
                }
                graph.nodes[id]?.outgoing()?.forEach(::visit)
                active.remove(id)
            }
            visit(entry)
            (graph.nodes.keys - visited).forEach { errors += "到達不能ノードがあります: $it" }
        }
        return errors.distinct()
    }

    private fun CommandNode.outgoing(): List<UUID> =
        if (type == CommandType.CONDITION) listOfNotNull(trueNext, falseNext) else listOfNotNull(next)
}
