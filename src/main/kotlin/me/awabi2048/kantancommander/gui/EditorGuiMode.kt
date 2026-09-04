package me.awabi2048.kantancommander.gui

import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.entity.Player

/**
 * 操作プレイヤーへ提示するかんたんコマンダーのエディター方式です。
 *
 * Gesture GUIはJava版の入力能力を前提とするため、統合版では同じ編集内容を
 * Inventory GUIへ送ります。表示方式の判定を入口から分離し、スニーク操作を含む
 * すべての編集導線で同じ環境判定を使えるようにします。
 */
internal enum class EditorGuiMode {
    GESTURE,
    INVENTORY,
}

internal object EditorGuiModeResolver {
    /** MyWorldManagerの公開APIを環境判定の唯一の正本として利用します。 */
    fun resolve(player: Player): EditorGuiMode =
        resolve(MyWorldManagerApi.getBedrockFormService()?.isBedrock(player) == true)

    /** 外部APIに依存しない表示方式の決定部分です。 */
    internal fun resolve(isBedrock: Boolean): EditorGuiMode =
        if (isBedrock) EditorGuiMode.INVENTORY else EditorGuiMode.GESTURE
}
