package me.awabi2048.kantancommander.item
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers
import io.papermc.paper.datacomponent.item.Tool
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * Kantan Commanderのアイテム種別。
 * 拡張コマンドブロック（設置用・常に未設定）とコマンドディスク（内容出力用・常に書き込み済み）の2種に分離する。
 */
enum class KantanItemKind { NONE, BLOCK, DISK }

object KantanItemService {
    const val BLOCK_ITEM_ID = "kantan.block"
    const val DISK_ITEM_ID = "kantan.disk"
    private val customItemIdKey = NamespacedKey("kantancommander", "custom_item_id")
    private val diskIdKey = NamespacedKey("kantancommander", "disk_id")

    /**
     * 拡張コマンドブロックはバニラのコマンドブロックに相当する設置用アイテム。
     * 通常のブロック配置判定（BlockPlaceEvent）を使うため、実体素材をINFESTED_STONEへ変更している。
     */
    private val blockMaterial = Material.INFESTED_STONE
    /** コマンドディスクはカスタムアイテムの慣習に合わせ、見た目用の毒じゃがいもを基底とする。 */
    private val diskMaterial = Material.POISONOUS_POTATO

    /** 未設定の拡張コマンドブロックを生成する。設置時に新しいスクリプトが作られる。 */
    fun createBlock(player: Player): ItemStack {
        val item = ItemStack(blockMaterial, 1)
        item.editMeta { meta ->
            meta.displayName(Component.text(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_NAME_BLOCK),
                NamedTextColor.AQUA,
            ))
            meta.setItemModel(NamespacedKey("minecraft", "test_block"))
            meta.persistentDataContainer.set(customItemIdKey, PersistentDataType.STRING, BLOCK_ITEM_ID)
        }
        applyCustomItemComponents(item)
        item.editMeta { meta ->
            meta.lore(
                CCSystem.getAPI().getLoreService().render(
                    CCSystem.getAPI().getLoreService().compose(
                        GuiLoreSpec.None,
                        listOf(me.awabi2048.kantancommander.gui.KcGui.action(
                            player,
                            "lore.click.place",
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_ACTION_PLACE_BLOCK),
                        )),
                    ),
                ),
            )
        }
        return item
    }

    /** 書き込み済みのコマンドディスクを生成する。内容はスクリプトの独立コピーを参照する。 */
    fun createDisk(script: DiskScript, player: Player): ItemStack {
        val item = ItemStack(diskMaterial, 1)
        item.editMeta { meta ->
            meta.displayName(Component.text(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_NAME_DISK),
                NamedTextColor.AQUA,
            ))
            meta.setItemModel(NamespacedKey("minecraft", "music_disc_otherside"))
            meta.setMaxStackSize(1)
            meta.persistentDataContainer.set(customItemIdKey, PersistentDataType.STRING, DISK_ITEM_ID)
            meta.persistentDataContainer.set(diskIdKey, PersistentDataType.STRING, script.id.toString())
        }
        applyCustomItemComponents(item)
        updateLore(item, script, player)
        return item
    }

    fun kind(item: ItemStack?): KantanItemKind {
        if (item == null) return KantanItemKind.NONE
        val meta = item.itemMeta ?: return KantanItemKind.NONE
        return when (meta.persistentDataContainer.get(customItemIdKey, PersistentDataType.STRING)) {
            BLOCK_ITEM_ID -> if (item.type == blockMaterial) KantanItemKind.BLOCK else KantanItemKind.NONE
            DISK_ITEM_ID -> if (item.type == diskMaterial) KantanItemKind.DISK else KantanItemKind.NONE
            else -> KantanItemKind.NONE
        }
    }

    fun isKantanItem(item: ItemStack?): Boolean = kind(item) != KantanItemKind.NONE

    /** コマンドディスクが参照するスクリプトUUID。拡張コマンドブロックや無関係アイテムではnull。 */
    fun diskId(item: ItemStack?): UUID? {
        if (kind(item) != KantanItemKind.DISK) return null
        val meta = item?.itemMeta ?: return null
        val raw = meta.persistentDataContainer.get(diskIdKey, PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    private fun applyCustomItemComponents(item: ItemStack) {
        item.setData(DataComponentTypes.TOOL, Tool.tool().build())
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build())

        // システム全体のカスタムアイテム慣習に合わせ、見た目用の毒じゃがいもを食べ物として扱わせない。
        item.unsetData(DataComponentTypes.FOOD)
        item.unsetData(DataComponentTypes.CONSUMABLE)
    }

    private fun composeLore(lines: List<GuiLoreLine>): GuiLoreSpec {
        val actions = lines.filterIsInstance<GuiLoreLine.Interaction>()
        val base = lines.filterNot { it is GuiLoreLine.Interaction }
        return CCSystem.getAPI().getLoreService().compose(
            if (base.isEmpty()) GuiLoreSpec.None else GuiLoreSpec.Rich(base, GuiLoreFrame.BOTH),
            actions,
        )
    }

    private fun updateLore(item: ItemStack, script: DiskScript, player: Player) {
        item.editMeta { meta ->
            val ownerName = Bukkit.getOfflinePlayer(script.owner).name ?: script.owner.toString().take(8)
            val lore = CCSystem.getAPI().getLoreService().render(
                composeLore(
                    listOf(
                        GuiLoreLine.Data(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_PROGRAM_NAME), script.name, "§f"),
                        GuiLoreLine.Data(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_COMMANDS), script.graph.nodes.size, "§f"),
                        GuiLoreLine.Data(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_OWNER), ownerName, "§f"),
                        GuiLoreLine.Data(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_TRIGGER), KcI18n.text(player, script.activation.key), "§f"),
                        GuiLoreLine.Spacer,
                        me.awabi2048.kantancommander.gui.KcGui.action(
                            player,
                            "lore.click.right",
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_ACTION_EDIT),
                        ),
                        me.awabi2048.kantancommander.gui.KcGui.action(
                            player,
                            "lore.click.shift_right",
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_ACTION_PLACE),
                        ),
                    ),
                ),
            )
            meta.lore(lore)
        }
    }
}