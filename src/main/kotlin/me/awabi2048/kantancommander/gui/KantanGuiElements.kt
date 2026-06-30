package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiFreeLayout
import com.awabi2048.ccsystem.api.gui.GuiPagedListLayout
import com.awabi2048.ccsystem.api.gui.GuiSettingsLayout
import org.bukkit.inventory.Inventory

/**
 * Kantan Commander 側のGUI入口。
 * レイアウトと標準フレームの取得元をCC-Systemに集約し、画面ごとの固定値が増殖しないようにする。
 */
internal object KantanGuiElements {
    private val layouts
        get() = CCSystem.getAPI().getGuiLayoutService()

    fun pagedListLayout(): GuiPagedListLayout = layouts.pagedList54()

    fun settingsLayout(): GuiSettingsLayout = layouts.settings54()

    fun commandPaletteLayout(): GuiFreeLayout = layouts.free45()

    fun applyStandardFrame(inventory: Inventory) {
        layouts.applyStandardFrame(inventory)
    }
}
