package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryOption
import com.awabi2048.ccsystem.api.gui.GuiMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiStructuredMenuEntrySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.MenuGesture
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

class KcMenuHolder(val owner: UUID, val id: String) : InventoryHolder {
    lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
}

object KcGui {
    val elements get() = CCSystem.getAPI().getGuiElementService()
    val layouts get() = CCSystem.getAPI().getGuiLayoutService()
    val sounds get() = CCSystem.getAPI().getMenuSoundService()

    fun title(raw: String) = elements.title(GuiNameSpec.Text(raw, GuiNameStyle.DEFAULT))

    fun inventory(player: Player, holder: KcMenuHolder, size: Int, title: String): Inventory {
        val inv = Bukkit.createInventory(holder, size, elements.title(GuiNameSpec.Text(title, GuiNameStyle.DEFAULT)))
        holder.inv = inv
        return inv
    }

    fun frame(inv: Inventory) {
        layouts.applyStandardFrame(inv)
    }

    fun item(
        material: Material,
        name: String,
        style: GuiNameStyle = GuiNameStyle.DEFAULT,
        lines: List<GuiLoreLine> = emptyList(),
        role: GuiElementRole = GuiElementRole.ACTION,
    ) =
        elements.item(GuiItemSpec(
            material,
            GuiNameSpec.Text(name, style),
            CCSystem.getAPI().getLoreService().compose(
                lines.filterNot { it is GuiLoreLine.Interaction }.let { base ->
                    if (base.isEmpty()) GuiLoreSpec.None else GuiLoreSpec.Rich(base, GuiLoreFrame.BOTH)
                },
                lines.filterIsInstance<GuiLoreLine.Interaction>(),
            ),
            role,
            1
        ))

    fun entry(
        player: Player,
        slot: Int,
        material: Material,
        name: String,
        style: GuiNameStyle = GuiNameStyle.DEFAULT,
        lines: List<GuiLoreLine> = emptyList(),
        role: GuiElementRole = GuiElementRole.ACTION,
        actions: List<GuiMenuActionIntent> = emptyList(),
    ) = elements.menuStructuredEntry(
        player,
        GuiStructuredMenuEntrySpec(
            slot = slot,
            item = GuiItemSpec(
                material = material,
                name = GuiNameSpec.Text(name, style),
                lore = CCSystem.getAPI().getLoreService().compose(
                    lines.filterNot { it is GuiLoreLine.Interaction }.let { base ->
                        if (base.isEmpty()) GuiLoreSpec.None else GuiLoreSpec.Rich(base, GuiLoreFrame.BOTH)
                    },
                    lines.filterIsInstance<GuiLoreLine.Interaction>(),
                ),
                role = role,
                amount = 1,
            ),
            actions = actions,
        ),
    )

    /**
     * Kantan Commanderの通常アイコンを、表示とクリック受付が分離しないCC-Systemの
     * 意味データ契約から生成します。色や区切りを画面側で手組みしないための正規入口です。
     */
    fun menuEntry(
        player: Player,
        slot: Int,
        material: Material,
        name: String,
        style: GuiNameStyle = GuiNameStyle.DEFAULT,
        role: GuiElementRole = GuiElementRole.ACTION,
        description: List<String> = emptyList(),
        data: List<GuiMenuEntryData> = emptyList(),
        options: List<GuiMenuEntryOption> = emptyList(),
        warnings: List<String> = emptyList(),
        dangers: List<String> = emptyList(),
        actions: List<GuiMenuActionIntent> = emptyList(),
        glint: Boolean? = null,
    ) = elements.menuEntry(
        player,
        GuiMenuEntrySpec(
            slot = slot,
            material = material,
            name = GuiNameSpec.Text(name, style),
            role = role,
            description = description,
            data = data,
            options = options,
            warnings = warnings,
            dangers = dangers,
            actions = actions,
            glint = glint,
        ),
    )

    fun action(player: Player, operationKey: String, action: String): GuiLoreLine.Interaction =
        GuiLoreLine.Interaction(player, gesture(operationKey), action)

    fun singleAction(player: Player, action: String): GuiLoreLine.Interaction =
        GuiLoreLine.Interaction(player, MenuGesture.ANY, action)

    private fun gesture(operationKey: String): MenuGesture = when (operationKey) {
        "lore.click.left" -> MenuGesture.LEFT
        "lore.click.right" -> MenuGesture.RIGHT
        "lore.click.shift_left" -> MenuGesture.SHIFT_LEFT
        "lore.click.shift_right" -> MenuGesture.SHIFT_RIGHT
        "lore.click.middle" -> MenuGesture.MIDDLE
        else -> MenuGesture.ANY
    }

    private fun commonText(player: Player, key: String): String =
        CCSystem.getAPI().getI18nString(player, key)
}
