package me.awabi2048.kantancommander.placement

import com.awabi2048.ccsystem.api.worldedit.WorldEditMoveOffset

/**
 * undo／redo 後の台帳再照合器です。
 *
 * undo／redo は専用イベントを持たないため、コマンド実行前の台帳
 * スナップショットと確定後の世界状態を比較し、失われた台帳セル
 * （`lost`）と新たに現れた未登録配置セル（`appeared`）を求めます。
 * 両集合が単一の平行移動で全単射になる場合だけ変位を返し、回転等で
 * 形状が変わった場合や件数が合わない場合は null として曖昧扱いにします。
 * Bukkit非依存のため単体テストします。
 */
internal object WorldEditUndoRematch {
    /**
     * 両集合を結ぶ単一の平行移動を求めます。
     *
     * 座標順に整列して差分が全件一致する場合だけ受理し、単独1件も
     * 同一セル（差分ゼロの自己対応は起こり得ない）でなければ受理します。
     * 空集合・件数不一致・非平行移動は null を返します。
     */
    fun matchTranslation(
        lost: List<Triple<Int, Int, Int>>,
        appeared: List<Triple<Int, Int, Int>>,
    ): WorldEditMoveOffset? {
        if (lost.isEmpty() || lost.size != appeared.size) return null
        val orderedLost = lost.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
        val orderedAppeared = appeared.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
        val first = orderedLost.first()
        val firstTarget = orderedAppeared.first()
        val offset = WorldEditMoveOffset(
            firstTarget.first - first.first,
            firstTarget.second - first.second,
            firstTarget.third - first.third,
        )
        val matched = orderedLost.zip(orderedAppeared).all { (source, target) ->
            target.first - source.first == offset.x &&
                target.second - source.second == offset.y &&
                target.third - source.third == offset.z
        }
        return if (matched) offset else null
    }
}
