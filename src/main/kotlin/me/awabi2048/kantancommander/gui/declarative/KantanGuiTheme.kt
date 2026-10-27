package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.html.GestureGuiThemedEnvironment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiHover
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiThemes
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Kantan Commander 画面のテーマ適用環境です。
 *
 * `.html` リソース側はトークン名（`surface` / `accent-deep` / `danger` 等）と
 * 語彙 class（`title` / `caption` / `selected` 等）だけを記述し、実素材・文字サイズは
 * CC-System の標準テーマへ委譲します。画面ごとの環境組み立て（ID基準の文言・ホバー・
 * 実行可否・素材登録）はここへ集約し、匿名実装の複製を避けます。
 *
 * 状態に応じて変わる表示は各マップへ要素IDで登録します。登録のないIDは
 * テーマ語彙・生素材名の順で解決されます。
 */
object KantanGuiTheme {
    fun environment(
        components: Map<String, Component> = emptyMap(),
        hovers: Map<String, GestureGuiHover> = emptyMap(),
        guards: Map<String, (Player, GestureGuiGesture) -> Boolean> = emptyMap(),
        blocks: Map<String, BlockData> = emptyMap(),
        outlines: Map<String, BlockData> = emptyMap(),
        items: Map<String, ItemStack> = emptyMap(),
        textSizes: Map<String, Double> = emptyMap(),
        theme: com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiTheme = GestureGuiThemes.standard(),
    ): GestureGuiThemedEnvironment = GestureGuiThemedEnvironment(
        theme = theme,
        textById = { components[it] },
        hoverById = { hovers[it] },
        guardById = { guards[it] },
        blockById = { blocks[it] },
        outlineById = { outlines[it] },
        itemById = { items[it] },
        textSizeById = { textSizes[it] },
    )
}
