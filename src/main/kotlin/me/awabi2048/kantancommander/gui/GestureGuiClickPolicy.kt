package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * クリック可能なメインハンド設定が受け付ける入力を一箇所で定義します。
 * CC-Systemでは左クリック系をPRIMARY、エンティティ右クリック系をSECONDARYへ
 * 正規化するため、片方だけを許可すると、入力イベントは消費されても効果音・Action
 * が発生しない状態になります。Shift版も同じ設定操作として扱います。
 */
internal object GestureGuiClickPolicy {
    val MAIN_HAND = setOf(
        GestureGuiGesture.PRIMARY,
        GestureGuiGesture.SECONDARY,
        GestureGuiGesture.SHIFT_PRIMARY,
        GestureGuiGesture.SHIFT_SECONDARY,
    )

    /** メインハンド入力の可否は画面生成時ではなく、実際のクリック時に判定します。 */
    fun hasMainHandItem(player: Player): Boolean =
        hasMainHandItem(player.inventory.itemInMainHand.type)

    /** Paperオブジェクトへ依存しない判定本体です。入力ポリシーの回帰テストにも使います。 */
    fun hasMainHandItem(material: Material): Boolean = material != Material.AIR
}
