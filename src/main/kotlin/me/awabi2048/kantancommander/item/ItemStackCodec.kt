package me.awabi2048.kantancommander.item

import org.bukkit.inventory.ItemStack
import java.util.Base64

object ItemStackCodec {
    fun encode(item: ItemStack): String =
        // 型・数量・メタデータ・データコンポーネントを含む実体をそのまま保存します。
        // GIVE_ITEMのcountは実行時の配布数として別管理されるため、配布処理側で
        // 必要なスタック数へ再分割します。ここでamountを1へ丸めると、装備操作の
        // や「設定中のアイテムを取得」で元の情報を復元できません。
        Base64.getEncoder().encodeToString(item.clone().serializeAsBytes())

    fun decode(raw: String): ItemStack? = runCatching {
        ItemStack.deserializeBytes(Base64.getDecoder().decode(raw))
    }.getOrNull()
}
