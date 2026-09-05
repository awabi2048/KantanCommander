package me.awabi2048.kantancommander.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
import java.util.UUID

/**
 * Gsonへ渡す前に、保存JSONが現行モデルの非null境界を満たすか確認します。
 *
 * Kotlinの非nullプロパティは、Gsonがリフレクションでインスタンスを生成するときには
 * 保証されません。未知のenum値や欠損した必須項目をそのまま復元すると、nullを含む
 * モデルがキャッシュへ入り、後段のcopy()や実行処理で無関係な操作として例外になります。
 * ここでは実行可能性（設定が未入力かどうか）ではなく、保存データの型・識別子・enumの
 * 整合性だけを検証し、編集途中の有効なスクリプトは隔離しないようにします。
 */
object PersistedScriptValidator {
    fun validate(root: JsonObject): List<String> {
        val errors = mutableListOf<String>()
        val path = "root"

        requireInteger(root, "formatVersion", path, errors)
        requireUuid(root, "id", path, errors)
        requireString(root, "name", path, errors)
        requireUuid(root, "owner", path, errors)
        requireInteger(root, "createdAt", path, errors)
        requireEnum(root, "activation", path, enumNames<ActivationMode>(), errors)
        requireObject(root, "timer", path, errors)?.let { timer ->
            requireBoolean(timer, "enabled", "$path/timer", errors)
            requireInteger(timer, "intervalSeconds", "$path/timer", errors)
        }
        requireObject(root, "graph", path, errors)?.let {
            validateGraph(it, "$path/graph", errors)
        }
        optionalBoolean(root, "listed", path, errors)
        optionalInteger(root, "revision", path, errors)
        optionalBoolean(root, "contentModified", path, errors)
        return errors
    }

    private fun validateGraph(graph: JsonObject, path: String, errors: MutableList<String>) {
        optionalUuid(graph, "entryNodeId", path, errors)
        val nodes = requireObject(graph, "nodes", path, errors) ?: return
        nodes.entrySet().forEach { (key, element) ->
            val nodePath = "$path/nodes/$key"
            if (runCatching { UUID.fromString(key) }.isFailure) {
                errors += "$nodePath: ノード保存キーがUUIDではありません"
            }
            val node = element.asObjectOrNull()
            if (node == null) {
                errors += "$nodePath: ノードがオブジェクトではありません"
                return@forEach
            }
            validateNode(node, nodePath, errors)
        }
    }

    private fun validateNode(node: JsonObject, path: String, errors: MutableList<String>) {
        requireUuid(node, "id", path, errors)
        requireEnum(node, "type", path, enumNames<CommandType>(), errors)
        requireStringMap(node, "params", path, errors)
        optionalStringArray(node, "configuredFields", path, errors)
        listOf("next", "trueNext", "falseNext", "pairedNodeId").forEach {
            optionalUuid(node, it, path, errors)
        }
        listOf("itemTempRef", "blockTempRef", "soundTempRef", "effectTempRef").forEach {
            optionalString(node, it, path, errors)
        }

        listOf(
            "targetSpec",
            "secondaryTargetSpec",
            "destinationTargetSpec",
            "temporaryEntityTargetSpec",
        ).forEach { field ->
            optionalObject(node, field, path, errors)?.let {
                validateTarget(it, "$path/$field", errors)
            }
        }
        listOf(
            "destinationSpec",
            "conditionPositionSpec",
            "blockPositionSpec",
            "blockFromSpec",
            "blockToSpec",
            "soundPositionSpec",
            "summonPositionSpec",
            "temporaryLocationPositionSpec",
        ).forEach { field ->
            optionalObject(node, field, path, errors)?.let {
                validatePosition(it, "$path/$field", errors)
            }
        }
        listOf("destinationFacingSpec", "temporaryLocationFacingSpec").forEach { field ->
            optionalObject(node, field, path, errors)?.let {
                validateFacing(it, "$path/$field", errors)
            }
        }
        optionalObject(node, "snapshot", path, errors)?.let {
            validateGraph(it, "$path/snapshot", errors)
        }
    }

    private fun validateTarget(spec: JsonObject, path: String, errors: MutableList<String>) {
        requireEnum(spec, "kind", path, enumNames<TargetKind>(), errors)
        requireEnum(spec, "sort", path, enumNames<TargetSort>(), errors)
        listOf(
            "entityType", "gameMode", "tag", "name", "tempName",
        ).forEach { optionalString(spec, it, path, errors) }
        listOf("minimumDistance", "maximumDistance", "dx", "dy", "dz").forEach {
            optionalFiniteNumber(spec, it, path, errors)
        }
        optionalInteger(spec, "limit", path, errors)
        optionalUuid(spec, "fixedEntityId", path, errors)
        optionalObject(spec, "searchOrigin", path, errors)?.let { origin ->
            val originPath = "$path/searchOrigin"
            optionalString(origin, "positionTemp", originPath, errors)
            optionalObject(origin, "position", originPath, errors)?.let {
                validatePosition(it, "$originPath/position", errors)
            }
        }
    }

    private fun validatePosition(spec: JsonObject, path: String, errors: MutableList<String>) {
        requireEnum(spec, "kind", path, enumNames<PositionKind>(), errors)
        listOf("x", "y", "z", "yaw", "pitch").forEach {
            optionalFiniteNumber(spec, it, path, errors)
        }
        optionalString(spec, "tempName", path, errors)
    }

    private fun validateFacing(spec: JsonObject, path: String, errors: MutableList<String>) {
        requireEnum(spec, "kind", path, enumNames<FacingKind>(), errors)
        listOf("x", "y", "z", "yaw", "pitch").forEach {
            optionalFiniteNumber(spec, it, path, errors)
        }
        optionalString(spec, "tempName", path, errors)
    }

    private fun requireObject(
        parent: JsonObject,
        field: String,
        path: String,
        errors: MutableList<String>,
    ): JsonObject? {
        val element = parent[field]
        if (element == null || element.isJsonNull) {
            errors += "$path/$field: 必須オブジェクトがありません"
            return null
        }
        if (!element.isJsonObject) {
            errors += "$path/$field: オブジェクトではありません"
            return null
        }
        return element.asJsonObject
    }

    private fun optionalObject(
        parent: JsonObject,
        field: String,
        path: String,
        errors: MutableList<String>,
    ): JsonObject? {
        val element = parent[field] ?: return null
        if (element.isJsonNull) return null
        if (!element.isJsonObject) {
            errors += "$path/$field: オブジェクトではありません"
            return null
        }
        return element.asJsonObject
    }

    private fun requireEnum(
        parent: JsonObject,
        field: String,
        path: String,
        allowed: Set<String>,
        errors: MutableList<String>,
    ) {
        val raw = parent[field]
        if (raw == null || raw.isJsonNull) {
            errors += "$path/$field: 必須enum値がありません"
            return
        }
        if (!raw.isJsonPrimitive || !raw.asJsonPrimitive.isString) {
            errors += "$path/$field: enum値が文字列ではありません"
            return
        }
        if (raw.asString !in allowed) {
            errors += "$path/$field: 未対応のenum値です (${raw.asString})"
        }
    }

    private fun requireUuid(parent: JsonObject, field: String, path: String, errors: MutableList<String>) {
        val raw = parent[field]
        if (raw == null || raw.isJsonNull) {
            errors += "$path/$field: 必須UUIDがありません"
            return
        }
        if (!raw.isJsonPrimitive || !raw.asJsonPrimitive.isString ||
            runCatching { UUID.fromString(raw.asString) }.isFailure
        ) {
            errors += "$path/$field: UUIDが不正です"
        }
    }

    private fun optionalUuid(parent: JsonObject, field: String, path: String, errors: MutableList<String>) {
        val raw = parent[field] ?: return
        if (raw.isJsonNull) return
        if (!raw.isJsonPrimitive || !raw.asJsonPrimitive.isString ||
            runCatching { UUID.fromString(raw.asString) }.isFailure
        ) {
            errors += "$path/$field: UUIDが不正です"
        }
    }

    private fun requireString(parent: JsonObject, field: String, path: String, errors: MutableList<String>) {
        val raw = parent[field]
        if (raw == null || raw.isJsonNull) {
            errors += "$path/$field: 必須文字列がありません"
            return
        }
        if (!raw.isJsonPrimitive || !raw.asJsonPrimitive.isString) {
            errors += "$path/$field: 文字列ではありません"
        }
    }

    private fun optionalString(parent: JsonObject, field: String, path: String, errors: MutableList<String>) {
        val raw = parent[field] ?: return
        if (raw.isJsonNull) return
        if (!raw.isJsonPrimitive || !raw.asJsonPrimitive.isString) {
            errors += "$path/$field: 文字列ではありません"
        }
    }

    private fun requireStringMap(parent: JsonObject, field: String, path: String, errors: MutableList<String>) {
        val raw = parent[field]
        if (raw == null || raw.isJsonNull) {
            errors += "$path/$field: 必須オブジェクトがありません"
            return
        }
        if (!raw.isJsonObject) {
            errors += "$path/$field: オブジェクトではありません"
            return
        }
        raw.asJsonObject.entrySet().forEach { (key, value) ->
            if (value.isJsonNull || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
                errors += "$path/$field/$key: 文字列値ではありません"
            }
        }
    }

    private fun optionalStringArray(parent: JsonObject, field: String, path: String, errors: MutableList<String>) {
        val raw = parent[field] ?: return
        if (raw.isJsonNull) return
        if (!raw.isJsonArray) {
            errors += "$path/$field: 配列ではありません"
            return
        }
        raw.asJsonArray.forEachIndexed { index, value ->
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
                errors += "$path/$field/$index: 文字列ではありません"
            }
        }
    }

    private fun requireInteger(parent: JsonObject, field: String, path: String, errors: MutableList<String>) {
        val raw = parent[field]
        if (raw == null || raw.isJsonNull) {
            errors += "$path/$field: 必須整数がありません"
            return
        }
        if (!raw.isJsonPrimitive || !raw.asJsonPrimitive.isNumber || raw.asString.toLongOrNull() == null) {
            errors += "$path/$field: 整数ではありません"
        }
    }

    private fun optionalInteger(parent: JsonObject, field: String, path: String, errors: MutableList<String>) {
        val raw = parent[field] ?: return
        if (raw.isJsonNull) return
        if (!raw.isJsonPrimitive || !raw.asJsonPrimitive.isNumber || raw.asString.toLongOrNull() == null) {
            errors += "$path/$field: 整数ではありません"
        }
    }

    private fun requireBoolean(parent: JsonObject, field: String, path: String, errors: MutableList<String>) {
        val raw = parent[field]
        if (raw == null || raw.isJsonNull) {
            errors += "$path/$field: 必須boolean値がありません"
            return
        }
        if (!raw.isJsonPrimitive || !raw.asJsonPrimitive.isBoolean) {
            errors += "$path/$field: boolean値ではありません"
        }
    }

    private fun optionalBoolean(parent: JsonObject, field: String, path: String, errors: MutableList<String>) {
        val raw = parent[field] ?: return
        if (raw.isJsonNull) return
        if (!raw.isJsonPrimitive || !raw.asJsonPrimitive.isBoolean) {
            errors += "$path/$field: boolean値ではありません"
        }
    }

    private fun optionalFiniteNumber(parent: JsonObject, field: String, path: String, errors: MutableList<String>) {
        val raw = parent[field] ?: return
        if (raw.isJsonNull) return
        if (!raw.isJsonPrimitive || !raw.asJsonPrimitive.isNumber ||
            raw.asString.toDoubleOrNull()?.isFinite() != true
        ) {
            errors += "$path/$field: 有限な数値ではありません"
        }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf(JsonElement::isJsonObject)?.asJsonObject

    private inline fun <reified E : Enum<E>> enumNames(): Set<String> =
        enumValues<E>().mapTo(linkedSetOf(), Enum<E>::name)
}
