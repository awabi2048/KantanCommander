package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
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
        role: GuiElementRole = GuiElementRole.CONTENT,
    ) =
        elements.item(GuiItemSpec(
            material,
            GuiNameSpec.Text(name, style),
            if (lines.isEmpty()) GuiLoreSpec.None else GuiLoreSpec.Rich(lines, GuiLoreFrame.BOTH),
            role,
            1
        ))

    fun action(player: Player, operationKey: String, action: String): GuiLoreLine.Action =
        GuiLoreLine.Action(commonText(player, operationKey), action)

    fun singleAction(player: Player, action: String): GuiLoreLine.SingleAction {
        val operation = commonText(player, "lore.click.any")
        val resolvedText = CCSystem.getAPI().getI18nString(
            player,
            "lore.action_single_with_operation",
            mapOf("operation" to operation, "action" to action)
        )
        return GuiLoreLine.SingleAction(operation, action, resolvedText)
    }

    private fun commonText(player: Player, key: String): String =
        CCSystem.getAPI().getI18nString(player, key)
}
