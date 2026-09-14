package me.awabi2048.kantancommander.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * issue #21 の旧装備操作の読込時正本化です。
 *
 * 旧 `ENTITY_ACTION`／`action=equip` ノードを新 `PLACE_ITEM` ノードへ
 * 読み込み時に変換します。対応表は1箇所に持ち、ファイル読込・埋め込み複写・
 * 制御ブロックアイテム復元の全経路で同じ関数を使います。
 *
 * 対応内容:
 * - 配置先は旧equipの唯一の意味である装備スロット（`EQUIPMENT`）へ固定します。
 * - `slot`／`item`／`itemData`／`itemTempRef`／`overwrite` は同名キーのまま引き継ぎます。
 * - `count` は旧equipに存在しないため既定の `1` を設定します。保存データ内の
 *   数量復元はサーバーなしで確定できないため、装備品の単品扱いへ正規化します。
 * - `action` キーを除去し、明示設定（`configuredFields`）の `action` を
 *   `placeDestination` へ付け替えます。
 * - 不正な旧 `slot` 値は推測で読み替えず、そのまま引き継いで新検証層で
 *   明示エラーにします。
 *
 * 実行意味の注意: 旧実装は上書き拒否時の無操作でも成功を返しましたが、
 * 新実装は全体最適の適用件数方針（F5: 1件以上適用なら成功）に従います。
 * 上書き拒否で1件も適用できなかった場合は失敗になります。
 */
internal object LegacyEquipMigration {
    fun normalizeGraph(graph: JsonObject?) {
        val nodes = graph?.get("nodes")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val nodeIds = nodes.entrySet().map { it.key }.toList()
        nodeIds.forEach { nodeId ->
            val node = nodes[nodeId]?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val params = node["params"]?.takeIf { it.isJsonObject }?.asJsonObject
            if (node["type"]?.takeIf { it.isJsonPrimitive }?.asString == "ENTITY_ACTION" &&
                params?.get("action")?.takeIf { it.isJsonPrimitive }?.asString == "equip"
            ) {
                node.addProperty("type", "PLACE_ITEM")
                params.remove("action")
                if (!params.has("placeDestination")) params.addProperty("placeDestination", "EQUIPMENT")
                if (!params.has("count")) params.addProperty("count", "1")
                node["configuredFields"]?.takeIf { it.isJsonArray }?.asJsonArray?.let { marks ->
                    val renamed = marks.mapNotNull { element ->
                        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return@mapNotNull null
                        when (element.asString) {
                            "action" -> "placeDestination"
                            else -> element.asString
                        }
                    }.toMutableSet()
                    // 移行で確定した配置先を明示設定へ含めます。
                    renamed += "placeDestination"
                    val rewritten = JsonArray()
                    renamed.forEach { rewritten.add(it) }
                    node.add("configuredFields", rewritten)
                }
            }
            normalizeGraph(node["snapshot"]?.takeIf { it.isJsonObject }?.asJsonObject)
            normalizeGraph(node["foldedGraph"]?.takeIf { it.isJsonObject }?.asJsonObject)
        }
    }
}
