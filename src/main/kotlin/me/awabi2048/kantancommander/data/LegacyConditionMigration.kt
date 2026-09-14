package me.awabi2048.kantancommander.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * issue #27 の旧条件分類の読込時正本化です。
 *
 * 旧 `kind`（`PLAYER_STATE`／`VARIABLE_STATE`／`BLOCK_STATE`／
 * `CONTROL_BLOCK_STATE`／`TARGET_EXISTS`）だけを持つ条件ノードを、現行の
 * `subject` 欄へ読み込み時に変換します。対応表は1箇所に持ち、ファイル読込・
 * 埋め込み複写・制御ブロックアイテム復元の全経路で同じ関数を使います。
 * 新規保存は常に `subject` だけを書き、旧 `kind` を再保存しません。
 *
 * 対応内容:
 * - `PLAYER_STATE`→`PLAYER`、`VARIABLE_STATE`→`VARIABLE`、
 *   `BLOCK_STATE`→`BLOCK`、`CONTROL_BLOCK_STATE`→`CONTROL_BLOCK`。
 * - `TARGET_EXISTS` は対象種別で振り分けます。プレイヤー系対象なら `PLAYER`、
 *   それ以外（対象未設定・不明値を含む）は中立な `OTHER_ENTITY` とします。
 *   曖昧な値を勝手に推測せず、分類不能はその他扱いに固定します。
 * - 既に `subject` がある場合は変換せず、残存 `kind` 欄だけを除去します。
 * - 未知・欠落の `kind` は変換せず、そのまま検証層の未設定扱いに委ねます。
 * - 対象固有の詳細欄（sneaking/item/variable/operator/value/block 等）や
 *   反転指定は触れず、意味を維持します。
 */
internal object LegacyConditionMigration {
    private const val KIND_TARGET_EXISTS = "TARGET_EXISTS"

    private val KIND_TO_SUBJECT = mapOf(
        "PLAYER_STATE" to "PLAYER",
        "VARIABLE_STATE" to "VARIABLE",
        "BLOCK_STATE" to "BLOCK",
        "CONTROL_BLOCK_STATE" to "CONTROL_BLOCK",
    )

    /** 旧 `TARGET_EXISTS` をプレイヤー側へ振り分ける対象種別です。 */
    private val PLAYER_TARGET_KINDS = setOf(
        "NEAREST_PLAYER",
        "NEARBY_PLAYERS",
        "ALL_PLAYERS",
        "RANDOM_PLAYER",
    )

    fun normalizeGraph(graph: JsonObject?) {
        val nodes = graph?.get("nodes")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val nodeIds = nodes.entrySet().map { it.key }.toList()
        nodeIds.forEach { nodeId ->
            val node = nodes[nodeId]?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val type = node["type"]?.takeIf { it.isJsonPrimitive }?.asString
            if (type == "CONDITION" || type == "FOLDED_IF") {
                normalizeNode(node)
            }
            normalizeGraph(node["snapshot"]?.takeIf { it.isJsonObject }?.asJsonObject)
            normalizeGraph(node["foldedGraph"]?.takeIf { it.isJsonObject }?.asJsonObject)
        }
    }

    private fun normalizeNode(node: JsonObject) {
        val params = node["params"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val subject = params["subject"]
            ?.takeIf { it.isJsonPrimitive }
            ?.asString?.takeIf(String::isNotBlank)
        if (subject != null) {
            // 正本がある場合は変換せず、残存 `kind` 欄だけを除去します。
            if (params.has("kind")) {
                params.remove("kind")
                removeConfiguredMark(node, "kind")
            }
            return
        }
        val kind = params["kind"]
            ?.takeIf { it.isJsonPrimitive }
            ?.asString ?: return
        val mapped = KIND_TO_SUBJECT[kind] ?: if (kind == KIND_TARGET_EXISTS) {
            val targetKind = node["targetSpec"]
                ?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("kind")?.takeIf { it.isJsonPrimitive }?.asString
            if (targetKind in PLAYER_TARGET_KINDS) "PLAYER" else "OTHER_ENTITY"
        } else {
            // 未知の旧値は推測で読み替えず、検証層の未設定扱いに委ねます。
            return
        }
        params.addProperty("subject", mapped)
        params.remove("kind")
        removeConfiguredMark(node, "kind")
        addConfiguredMark(node, "subject")
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
        // 移行で確定した判定対象を明示設定へ含めます。
        val current = marks?.mapNotNull {
            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) return@mapNotNull null
            it.asString
        }.orEmpty()
        if (field in current) return
        val rewritten = JsonArray()
        // configuredFields がない旧保存にも正本の来歴を付けます。
        (current + field).forEach { rewritten.add(it) }
        node.add("configuredFields", rewritten)
    }
}
