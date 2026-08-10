package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.STRUCTURED_FORMAT_VERSION
import me.awabi2048.kantancommander.model.CommandFeaturePolicy
import me.awabi2048.kantancommander.model.DiskProfile
import me.awabi2048.kantancommander.gui.GraphLayoutEngine
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

class ScriptStore(
    private val dir: File,
    private val logger: Logger,
    private val limits: GraphLimits = GraphLimits(),
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    init {
        dir.mkdirs()
    }

    fun create(owner: UUID, name: String, profile: DiskProfile = DiskProfile.STANDARD): DiskScript =
        DiskScript(name = name, owner = owner, profile = profile).also(::save)

    fun createPlacement(
        owner: UUID,
        name: String,
        profile: DiskProfile = DiskProfile.STANDARD,
    ): DiskScript = DiskScript(name = name, owner = owner, listed = false, profile = profile).also(::save)

    fun copyForPlacement(source: DiskScript): DiskScript =
        source.copy(
            id = UUID.randomUUID(),
            createdAt = System.currentTimeMillis(),
            listed = false,
            graph = source.graph.deepCopy(),
        ).also(::save)

    fun copyForItem(source: DiskScript): DiskScript =
        source.copy(
            id = UUID.randomUUID(),
            createdAt = System.currentTimeMillis(),
            listed = false,
            graph = source.graph.deepCopy(),
        ).also(::save)

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
        val validation = validateRecursively(script.graph) + CommandFeaturePolicy.validate(script)
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
            ?.also { require(validateRecursively(it.graph).isEmpty()) }
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

    private fun validateRecursively(root: me.awabi2048.kantancommander.model.CommandGraph): List<String> {
        val errors = mutableListOf<String>()
        val visited = Collections.newSetFromMap(
            IdentityHashMap<me.awabi2048.kantancommander.model.CommandGraph, Boolean>()
        )
        fun validate(graph: me.awabi2048.kantancommander.model.CommandGraph, path: String) {
            if (!visited.add(graph)) {
                errors += "$path: 別ディスクのコピー内容が循環参照しています"
                return
            }
            GraphValidator.validate(graph, limits).forEach { errors += "$path: $it" }
            val layout = GraphLayoutEngine.layout(graph)
            if (layout.width > limits.maximumMapWidth) {
                errors += "$path: 描画幅が上限 ${limits.maximumMapWidth} を超えています"
            }
            if (layout.height > limits.maximumMapHeight) {
                errors += "$path: 描画高さが上限 ${limits.maximumMapHeight} を超えています"
            }
            graph.nodes.values.forEach { node ->
                node.snapshot?.let { validate(it, "$path/${node.id}") }
            }
            visited.remove(graph)
        }
        validate(root, "root")
        return errors
    }
}
