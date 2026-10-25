package me.awabi2048.kantancommander.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.awabi2048.kantancommander.model.EntityDataSchema

/**
 * issue #10 の召喚時表示名の読込時正本化です。
 *
 * 旧 `SUMMON_ENTITY` の独立 `customName` 欄を、共通スキーマの
 * 個別設定（`summonData_custom_name`）へ読み込み時に変換します。
 * 対応表は1箇所に持ち、ファイル読込・埋め込み複写・制御ブロックアイテム復元の
 * 全経路で同じ関数を使います。新規保存は個別設定だけを書きます。
 *
 * 対応内容:
 * - 非空の `customName` を `summonData_custom_name` へ引き継ぎます。
 * - 空の `customName` は未設定と同等のため除去します。
 * - 明示設定の `customName` は `summonData_custom_name` へ付け替えます。
 */
internal object LegacySummonMigration {
    fun normalizeGraph(graph: JsonObject?) {
        val nodes = graph?.get("nodes")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val nodeIds = nodes.entrySet().map { it.key }.toList()
        nodeIds.forEach { nodeId ->
            val node = nodes[nodeId]?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val params = node["params"]?.takeIf { it.isJsonObject }?.asJsonObject
            if (node["type"]?.takeIf { it.isJsonPrimitive }?.asString == "SUMMON_ENTITY" && params != null) {
                val customName = params["customName"]
                    ?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)
                val targetKey = EntityDataSchema.summonDataKey(EntityDataSchema.PROPERTY_CUSTOM_NAME)
                if (customName != null && !params.has(targetKey)) {
                    params.addProperty(targetKey, customName)
                }
                if (params.has("customName")) {
                    params.remove("customName")
                    removeConfiguredMark(node, "customName")
                    if (customName != null) addConfiguredMark(node, targetKey)
                }
            }
            normalizeGraph(node["foldedGraph"]?.takeIf { it.isJsonObject }?.asJsonObject)
        }
    }

    private fun removeConfiguredMark(node: JsonObject, field: String) {
        val marks = node["configuredFields"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        val rewritten = JsonArray()
        marks.mapNotNull {
            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) return@mapNotNull null
            it.asString
        }.filterTo(LinkedHashSet()) { it != field }.forEach { rewritten.add(it) }
        node.add("configuredFields", rewritten)
    }

    private fun addConfiguredMark(node: JsonObject, field: String) {
        val marks = node["configuredFields"]?.takeIf { it.isJsonArray }?.asJsonArray
        val current = marks?.mapNotNull {
            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) return@mapNotNull null
            it.asString
        }.orEmpty()
        if (field in current) return
        val rewritten = JsonArray()
        (current + field).forEach { rewritten.add(it) }
        node.add("configuredFields", rewritten)
    }
}
