package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.model.EventTriggerKind
import org.bukkit.block.data.BlockData

/** issue #7 の信号変化の分類です。ボタン・レバー・感圧板を区別します。 */
internal enum class RedstoneTriggerCategory { BUTTON, LEVER, PRESSURE_PLATE }

/**
 * issue #7 の信号変化の分類とイベント種別への対応です。
 *
 * ボタン・レバーは `Switch` 型で、素材名（`*_BUTTON`／`LEVER`）により区別します。
 * 感圧板は加重板（`AnaloguePowerable`）または感圧板素材の `Powerable` で判定します。
 * オン／オフの変化辺に対応するイベント種別を返し、提供しない辺は null とします。
 * ボタンは押した時だけを提供し、戻った時は起動しません（issue #7 の初期一覧どおり）。
 * 感圧板は「踏まれた時」だけを提供し、離した時は起動しません。
 * Bukkit 実体を必要としない純粋判定であり、単体試験します。
 */
internal object EventTriggerClassification {
    fun redstoneCategory(data: BlockData, typeName: String): RedstoneTriggerCategory? = when {
        data is org.bukkit.block.data.type.Switch -> when {
            typeName.endsWith("_BUTTON") -> RedstoneTriggerCategory.BUTTON
            typeName == "LEVER" -> RedstoneTriggerCategory.LEVER
            else -> null
        }
        data is org.bukkit.block.data.AnaloguePowerable ||
            (data is org.bukkit.block.data.Powerable && typeName.endsWith("_PRESSURE_PLATE"))
        -> RedstoneTriggerCategory.PRESSURE_PLATE
        else -> null
    }

    /** 指定した変化辺（`on` が真なら通電）に対応する起動種別を返します。 */
    fun eventFor(category: RedstoneTriggerCategory, on: Boolean): EventTriggerKind? = when (category) {
        RedstoneTriggerCategory.BUTTON -> if (on) EventTriggerKind.BUTTON_ON else null
        RedstoneTriggerCategory.LEVER -> if (on) EventTriggerKind.LEVER_ON else EventTriggerKind.LEVER_OFF
        RedstoneTriggerCategory.PRESSURE_PLATE -> if (on) EventTriggerKind.PRESSURE_PLATE_ON else null
    }
}
