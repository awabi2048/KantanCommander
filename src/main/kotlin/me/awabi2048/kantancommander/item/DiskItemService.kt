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
import me.awabi2048.kantancommander.model.DiskProfile
import me.awabi2048.kantancommander.model.effectiveProfile
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
import java.util.concurrent.ThreadLocalRandom

enum class DiskItemState {
    NOT_DISK,
    UNSET,
    WRITTEN,
}

object DiskItemService {
    const val STANDARD_ITEM_ID = "kantan.disk"
    const val SIMPLE_ITEM_ID = "kantan.simple_disk"
    private val customItemIdKey = NamespacedKey("kantancommander", "custom_item_id")
    private val diskIdKey = NamespacedKey("kantancommander", "disk_id")
    private val musicDiscModels = listOf(
        "music_disc_13", "music_disc_cat", "music_disc_blocks", "music_disc_chirp",
        "music_disc_far", "music_disc_mall", "music_disc_mellohi", "music_disc_stal",
        "music_disc_strad", "music_disc_ward", "music_disc_wait", "music_disc_pigstep",
        "music_disc_otherside", "music_disc_relic", "music_disc_creator", "music_disc_precipice",
    )
    private val baseMaterial = Material.POISONOUS_POTATO

    fun createUnset(name: String, player: Player, profile: DiskProfile = DiskProfile.STANDARD): ItemStack {
        val item = ItemStack(baseMaterial, 1)
        item.editMeta { meta ->
            meta.displayName(Component.text(name, NamedTextColor.YELLOW))
            meta.setItemModel(NamespacedKey("minecraft", if (profile == DiskProfile.SIMPLE) {
                "music_disc_11"
            } else musicDiscModels[ThreadLocalRandom.current().nextInt(musicDiscModels.size)]))
            meta.setMaxStackSize(1)
            meta.persistentDataContainer.set(customItemIdKey, PersistentDataType.STRING, itemId(profile))
        }
        applyCustomItemComponents(item)
        item.editMeta { meta ->
            meta.lore(
                CCSystem.getAPI().getLoreService().render(
                    CCSystem.getAPI().getLoreService().compose(
                        GuiLoreSpec.Rich(
                            listOf(GuiLoreLine.Data(
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_PROFILE),
                                KcI18n.text(player, if (profile == DiskProfile.SIMPLE) KcKeys.KANTAN_COMMANDER_CLEAN_PROFILE_SIMPLE else KcKeys.KANTAN_COMMANDER_CLEAN_PROFILE_STANDARD),
                                "§f",
                            )),
                            GuiLoreFrame.BOTH,
                        ),
                        listOf(me.awabi2048.kantancommander.gui.KcGui.action(
                            player,
                            "lore.click.shift_right",
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_ACTION_PLACE),
                        ))
                    )
                )
            )
        }
        return item
    }

    fun create(script: DiskScript, player: Player): ItemStack {
        val item = ItemStack(baseMaterial, 1)
        item.editMeta { meta ->
            meta.displayName(Component.text(script.name, NamedTextColor.YELLOW))
            meta.setItemModel(NamespacedKey("minecraft", if (script.effectiveProfile == DiskProfile.SIMPLE) {
                "music_disc_11"
            } else musicDiscModels[Math.floorMod(script.id.hashCode(), musicDiscModels.size)]))
            meta.setMaxStackSize(1)
            meta.persistentDataContainer.set(customItemIdKey, PersistentDataType.STRING, itemId(script.effectiveProfile))
            meta.persistentDataContainer.set(diskIdKey, PersistentDataType.STRING, script.id.toString())
        }
        applyCustomItemComponents(item)
        updateLore(item, script, player)
        return item
    }

    fun diskId(item: ItemStack?): UUID? {
        item ?: return null
        if (state(item) != DiskItemState.WRITTEN) return null
        val meta = item.itemMeta ?: return null
        val raw = meta.persistentDataContainer.get(diskIdKey, PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    fun state(item: ItemStack?): DiskItemState {
        if (item?.type != baseMaterial) return DiskItemState.NOT_DISK
        val meta = item.itemMeta ?: return DiskItemState.NOT_DISK
        if (profile(meta.persistentDataContainer.get(customItemIdKey, PersistentDataType.STRING)) == null) {
            return DiskItemState.NOT_DISK
        }
        return if (meta.persistentDataContainer.has(diskIdKey, PersistentDataType.STRING)) {
            DiskItemState.WRITTEN
        } else {
            DiskItemState.UNSET
        }
    }

    fun isDisk(item: ItemStack?): Boolean = state(item) != DiskItemState.NOT_DISK

    /** 未記入ディスクだけは参照先スクリプトがないため、アイテムIDを作成プロファイルの正データとします。 */
    fun unsetProfile(item: ItemStack?): DiskProfile? {
        if (state(item) != DiskItemState.UNSET) return null
        return profile(item?.itemMeta?.persistentDataContainer?.get(customItemIdKey, PersistentDataType.STRING))
    }

    private fun itemId(profile: DiskProfile) =
        if (profile == DiskProfile.SIMPLE) SIMPLE_ITEM_ID else STANDARD_ITEM_ID

    private fun profile(itemId: String?): DiskProfile? = when (itemId) {
        STANDARD_ITEM_ID -> DiskProfile.STANDARD
        SIMPLE_ITEM_ID -> DiskProfile.SIMPLE
        else -> null
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
                        GuiLoreLine.Data(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_COMMANDS), script.graph.nodes.size, "§f"),
                        GuiLoreLine.Data(
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_PROFILE),
                            KcI18n.text(player, if (script.effectiveProfile == DiskProfile.SIMPLE) KcKeys.KANTAN_COMMANDER_CLEAN_PROFILE_SIMPLE else KcKeys.KANTAN_COMMANDER_CLEAN_PROFILE_STANDARD),
                            "§f",
                        ),
                        GuiLoreLine.Data(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_OWNER), ownerName, "§f"),
                        GuiLoreLine.Data(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_TRIGGER), KcI18n.text(player, script.activation.key), "§f"),
                        GuiLoreLine.Spacer,
                        me.awabi2048.kantancommander.gui.KcGui.action(
                            player,
                            "lore.click.right",
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_ACTION_EDIT)
                        ),
                        me.awabi2048.kantancommander.gui.KcGui.action(
                            player,
                            "lore.click.shift_right",
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_ACTION_PLACE)
                        )
                    ),
                )
            )
            meta.lore(lore)
        }
    }
}
