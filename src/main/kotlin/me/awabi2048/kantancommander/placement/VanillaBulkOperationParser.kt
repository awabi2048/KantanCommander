package me.awabi2048.kantancommander.placement

import kotlin.math.floor

/**
 * バニラ一括操作の影響範囲（同一ワールド内のブロック座標の直方体）です。
 *
 * WorldEdit選択範囲を使わない操作（`/clone`・`/fill`・`/setblock`）の
 * 引数座標だけから求めます。`PlayerCommandPreprocessEvent` は実行前のため、
 * まだ残っている配置物との包含判定に使います。
 */
internal data class ThreatenedBox(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
) {
    fun contains(x: Int, y: Int, z: Int): Boolean =
        x in minX..maxX && y in minY..maxY && z in minZ..maxZ
}

/**
 * Bukkitイベントを通さず実体だけを書き換えるバニラ一括操作の引数を解釈します。
 *
 * WorldEdit APIへ触れないため、WorldEdit不在時も安全に呼び出せます。
 * 相対座標（`~`）は実行プレイヤーのブロック位置を基準に解決します。
 * ローカル座標（`^`）や引数不足等で解釈できない場合は null を返し、
 * 呼び出し側は同一ワールド全体を警告対象にする保守側へ倒します。
 */
internal object VanillaBulkOperationParser {
    /** 座標解釈の対象コマンドかを返します。 */
    fun isRelevant(message: String): Boolean =
        when (com.awabi2048.ccsystem.api.worldedit.WorldEditCommands.normalizedCommandName(message)) {
            "clone", "fill", "setblock" -> true
            else -> false
        }

    /**
     * 影響範囲の直方体を返します。
     *
     * `clone` は複写元と貼付け先（複写元と同寸）の両方を返します。
     * 解釈できない場合は null を返します。
     */
    fun parse(message: String, originX: Int, originY: Int, originZ: Int): List<ThreatenedBox>? {
        val tokens = message.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        if (tokens.isEmpty()) return null
        val arguments = tokens.drop(1)
        return when (com.awabi2048.ccsystem.api.worldedit.WorldEditCommands.normalizedCommandName(tokens.first())) {
            "setblock" -> {
                val point = parsePoint(arguments, 0, originX, originY, originZ) ?: return null
                listOf(ThreatenedBox(point.first, point.second, point.third, point.first, point.second, point.third))
            }
            "fill" -> {
                val from = parsePoint(arguments, 0, originX, originY, originZ) ?: return null
                val to = parsePoint(arguments, 3, originX, originY, originZ) ?: return null
                listOf(boxOf(from, to))
            }
            "clone" -> {
                val from = parsePoint(arguments, 0, originX, originY, originZ) ?: return null
                val to = parsePoint(arguments, 3, originX, originY, originZ) ?: return null
                val destination = parsePoint(arguments, 6, originX, originY, originZ) ?: return null
                val source = boxOf(from, to)
                val sizeX = source.maxX - source.minX
                val sizeY = source.maxY - source.minY
                val sizeZ = source.maxZ - source.minZ
                val pasted = ThreatenedBox(
                    destination.first, destination.second, destination.third,
                    destination.first + sizeX, destination.second + sizeY, destination.third + sizeZ,
                )
                listOf(source, pasted)
            }
            else -> null
        }
    }

    private fun boxOf(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): ThreatenedBox =
        ThreatenedBox(
            minOf(a.first, b.first), minOf(a.second, b.second), minOf(a.third, b.third),
            maxOf(a.first, b.first), maxOf(a.second, b.second), maxOf(a.third, b.third),
        )

    private fun parsePoint(
        arguments: List<String>,
        offset: Int,
        originX: Int,
        originY: Int,
        originZ: Int,
    ): Triple<Int, Int, Int>? {
        if (arguments.size < offset + 3) return null
        val x = parseCoordinate(arguments[offset], originX) ?: return null
        val y = parseCoordinate(arguments[offset + 1], originY) ?: return null
        val z = parseCoordinate(arguments[offset + 2], originZ) ?: return null
        return Triple(x, y, z)
    }

    private fun parseCoordinate(token: String, origin: Int): Int? {
        // ローカル座標（^）はプレイヤーの向きを要するため、ここでは解釈しません。
        if (token.contains('^')) return null
        if (token.startsWith("~")) {
            val shift = token.drop(1).takeIf(String::isNotEmpty)?.toDoubleOrNull() ?: 0.0
            if (!shift.isFinite()) return null
            return floor(origin + shift).toInt()
        }
        val absolute = token.toDoubleOrNull() ?: return null
        if (!absolute.isFinite()) return null
        return floor(absolute).toInt()
    }
}
