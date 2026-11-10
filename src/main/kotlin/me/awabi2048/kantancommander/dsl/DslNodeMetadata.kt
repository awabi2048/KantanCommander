package me.awabi2048.kantancommander.dsl

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import java.util.Base64

/** DSLの実行式に現れない編集状態を、その文に付属するコメントとして保持します。 */
internal object DslNodeMetadata {
    private const val PREFIX = "@kantan-state-v1:"
    private val conditionDetails = setOf("sneaking", "item", "itemData", "variable", "operator", "value", "block", "redstoneSignal")

    fun encode(node: CommandNode): String? {
        val marks = node.configuredFields.orEmpty()
        val details = if (node.type == CommandType.CONDITION) {
            val active = activeConditionDetails(node.string("subject"))
            node.params.filter { (key, value) ->
                key in conditionDetails && key !in active && value.isNotBlank() && value != node.type.defaults[key]
            }
        } else emptyMap()
        if (marks.isEmpty() && details.isEmpty()) return null
        val root = JsonObject().apply {
            addProperty("type", node.type.name)
            addProperty("subject", node.string("subject"))
            add("marks", JsonObject().apply {
                marks.sorted().forEach { key -> addProperty(key, node.params[key]) }
            })
            add("condition", JsonObject().apply { details.forEach { (key, value) -> addProperty(key, value) } })
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(root.toString().toByteArray(Charsets.UTF_8))
    }

    fun nodeType(texts: List<String>): CommandType? = texts.firstNotNullOfOrNull { text ->
        if (!text.startsWith(PREFIX)) return@firstNotNullOfOrNull null
        runCatching {
            val json = String(Base64.getUrlDecoder().decode(text.removePrefix(PREFIX)), Charsets.UTF_8)
            CommandType.valueOf(JsonParser.parseString(json).asJsonObject["type"].asString)
        }.getOrNull()
    }

    private fun activeConditionDetails(subject: String): Set<String> = when (subject) {
        "PLAYER" -> setOf("sneaking", "item", "itemData")
        "VARIABLE" -> setOf("variable", "operator", "value")
        "BLOCK" -> setOf("block")
        "CONTROL_BLOCK" -> setOf("redstoneSignal")
        else -> emptySet()
    }

    /** 文の実行値を優先し、一致する明示記録と現在の判定に使わない詳細のみを復元します。 */
    fun restore(node: CommandNode, texts: List<String>): List<String> = texts.filterNot { text ->
        if (!text.startsWith(PREFIX)) return@filterNot false
        runCatching {
            val json = String(Base64.getUrlDecoder().decode(text.removePrefix(PREFIX)), Charsets.UTF_8)
            val root = JsonParser.parseString(json).asJsonObject
            if (root["type"]?.asString != node.type.name) return@runCatching false
            val sameSubject = root["subject"]?.asString == node.string("subject")
            if (node.type == CommandType.CONDITION && sameSubject) {
                val active = activeConditionDetails(node.string("subject"))
                root.getAsJsonObject("condition")?.entrySet()?.forEach { (key, value) ->
                    if (key in conditionDetails && key !in active) node.params[key] = value.asString
                }
            }
            root.getAsJsonObject("marks")?.entrySet()?.forEach { (key, value) ->
                val expected = if (value.isJsonNull) null else value.asString
                if ((node.type != CommandType.CONDITION || sameSubject) && node.params[key] == expected) {
                    node.markConfigured(key)
                }
            }
            true
        }.getOrDefault(false)
    }
}
