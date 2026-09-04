package me.awabi2048.kantancommander.execution

/** fillの事前検査で扱う1ブロック分の整数座標です。 */
data class BlockOperationCoordinate(
    val x: Int,
    val y: Int,
    val z: Int,
)

/**
 * ブロック変更を一部実行してから保護対象へ到達する状態を防ぐための純粋な検査です。
 * 呼び出し側はこの結果がfalseのときだけ、同じ範囲へ実際の変更を適用します。
 */
object BlockOperationProtectionPolicy {
    fun hasProtectedBlock(
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
        minZ: Int,
        maxZ: Int,
        isProtected: (BlockOperationCoordinate) -> Boolean,
    ): Boolean {
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    if (isProtected(BlockOperationCoordinate(x, y, z))) return true
                }
            }
        }
        return false
    }
}
