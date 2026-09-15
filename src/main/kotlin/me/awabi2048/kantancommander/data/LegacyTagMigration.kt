package me.awabi2048.kantancommander.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.awabi2048.kantancommander.model.EntityDataPath
import me.awabi2048.kantancommander.model.EntityTagEntries

/**
 * issue #32 の旧タグ操作の読込時正本化です。
 *
 * 旧 `ENTITY_ACTION`／`action=tag` ノードを新 `ENTITY_OPERATION` ノードへ
 * 読み込み時に変換します。対応表は1箇所に持ち、ファイル読込・埋め込み複写・
 * 制御ブロックアイテム復元の全経路で同じ関数を使います。
 * 新規保存は常に `ENTITY_OPERATION` だけを書きます。
 *
 * 対応内容:
 * - 操作種別は `entityDataPath=tag` へ固定します。
 * - 旧 `tagOperation`（add／remove、欠落時は add）と旧 `tag` を
 *   `tagEntries="<mode>=<tag>"` へ引き継ぎます。値の推測・正規化は行いません。
 * - 不明な旧操作は変換せず、そのまま検証層の不明扱いに委ねます。
 * - 明示設定は `action`／`tagOperation`／`tag` から
 *   `entityDataPath`／`tagEntries` へ付け替えます。
 *
 * 実行意味の注意: 新実装は全体最適の適用件数方針（F4: 1件以上適用なら成功、
 * 件数は構造化結果へ保持）に従います。旧実装の冪等な全件適用と同値です。
 */
internal object LegacyTagMigration {
    fun normalizeGraph(graph: JsonObject?) {
        val nodes = graph?.get("nodes")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val nodeIds = nodes.entrySet().map { it.key }.toList()
        nodeIds.forEach { nodeId ->
            val node = nodes[nodeId]?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val params = node["params"]?.takeIf { it.isJsonObject }?.asJsonObject
            if (node["type"]?.takeIf { it.isJsonPrimitive }?.asString == "ENTITY_ACTION" &&
                params?.get("action")?.takeIf { it.isJsonPrimitive }?.asString == "tag"
            ) {
                val operation = params["tagOperation"]
                    ?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)
                    ?: EntityTagEntries.MODE_ADD
                if (operation != EntityTagEntries.MODE_ADD && operation != EntityTagEntries.MODE_REMOVE) {
                    // 不明な旧操作は推測で読み替えず、検証層の不明扱いに委ねます。
                    return@forEach
                }
                val tag = params["tag"]?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                node.addProperty("type", "ENTITY_OPERATION")
                params.remove("action")
                params.remove("tagOperation")
                params.remove("tag")
                if (!params.has(EntityDataPath.PARAM_PATH)) {
                    params.addProperty(EntityDataPath.PARAM_PATH, EntityDataPath.TAG)
                }
                if (!params.has(EntityTagEntries.PARAM_ENTRIES)) {
                    params.addProperty(EntityTagEntries.PARAM_ENTRIES, "$operation=$tag")
                }
                rewriteConfiguredMarks(node)
            }
            normalizeGraph(node["snapshot"]?.takeIf { it.isJsonObject }?.asJsonObject)
            normalizeGraph(node["foldedGraph"]?.takeIf { it.isJsonObject }?.asJsonObject)
        }
    }

    private fun rewriteConfiguredMarks(node: JsonObject) {
        val marks = node["configuredFields"]?.takeIf { it.isJsonArray }?.asJsonArray
        val current = marks?.mapNotNull {
            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) return@mapNotNull null
            it.asString
        }.orEmpty()
        // 移行で確定した設定先を明示設定へ含めます。
        val rewritten = (current - setOf("action", "tagOperation", "tag") +
            setOf(EntityDataPath.PARAM_PATH, EntityTagEntries.PARAM_ENTRIES)).toMutableSet()
        val output = JsonArray()
        rewritten.forEach { output.add(it) }
        node.add("configuredFields", output)
    }
}
