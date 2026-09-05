package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * ITEM／BLOCK／SOUND／EFFECTの設定元表示を両GUIで共有します。
 *
 * 設定元の意味を画面ごとに文字列合成すると、日本語の改行位置や翻訳差分が
 * Inventory／Gestureでずれます。ローカライズ済みのTextListを唯一の正本として、
 * 一行表示と改行表示の両方をここから生成します。
 */
internal fun typedValueSourceLabelKey(
    field: String,
    source: CommandValueSource,
): LocalizationKey<List<String>> = when {
    source == CommandValueSource.TEMPORARY ->
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_VALUE_SOURCE_TEMPORARY
    field == "item" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_VALUE_SOURCE_ITEM_MAINHAND
    field == "block" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_VALUE_SOURCE_BLOCK_MAINHAND
    field == "sound" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_VALUE_SOURCE_SOUND_DIALOG
    field == "effect" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_VALUE_SOURCE_EFFECT_DIALOG
    else -> error("設定元の表示に未対応のフィールドです: $field")
}

internal fun typedValueSourceLines(
    player: Player,
    field: String,
    source: CommandValueSource,
): List<String> = KcI18n.list(player, typedValueSourceLabelKey(field, source))
    .filter(String::isNotBlank)

/** TextListの意味行を、Gesture GUIのTextDisplayで実際の改行として描画します。 */
internal fun typedValueSourceComponent(lines: List<String>): Component =
    lines.foldIndexed(Component.empty()) { index, result, line ->
        val component = Component.text(line)
        if (index == 0) component else result.append(Component.newline()).append(component)
    }
