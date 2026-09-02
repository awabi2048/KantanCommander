package me.awabi2048.kantancommander.gui

import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player

/**
 * 配置Gestureエディターの操作距離を、Minecraftの通常エンティティ到達距離へ揃えます。
 *
 * CC-Systemの画面ヒット判定も同じ属性を使いますが、こちらは画面の表示認可・共有参加を
 * 判定する層です。表示認可では視線rayそのものを再計算せず、画面中心（anchor）までの
 * 距離だけを確認し、実際のクリック到達性と遮蔽はGestureGui側の判定へ委譲します。
 */
internal object GestureEditorReachPolicy {
    const val DEFAULT_INTERACTION_RANGE: Double = 3.0

    /** Paperが属性を返さない場合も、CC-Systemの既存判定と同じ既定値・下限を使います。 */
    fun maximumInteractionRange(player: Player): Double =
        (player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)?.value ?: DEFAULT_INTERACTION_RANGE)
            .coerceAtLeast(1.0)

    fun isWithinInteractionRange(player: Player, editorAnchor: Location): Boolean {
        val editorWorld = editorAnchor.world ?: return false
        if (player.world.uid != editorWorld.uid) return false
        return isWithinInteractionRange(
            actorEye = player.eyeLocation,
            editorAnchor = editorAnchor,
            maximumRange = maximumInteractionRange(player),
        )
    }

    /** Bukkit Playerへ依存しない距離計算本体です。境界値の回帰テストから直接利用します。 */
    internal fun isWithinInteractionRange(
        actorEye: Location,
        editorAnchor: Location,
        maximumRange: Double,
    ): Boolean {
        val range = maximumRange.coerceAtLeast(1.0)
        val dx = actorEye.x - editorAnchor.x
        val dy = actorEye.y - editorAnchor.y
        val dz = actorEye.z - editorAnchor.z
        return dx * dx + dy * dy + dz * dz <= range * range
    }
}
