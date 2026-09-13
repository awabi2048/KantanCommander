package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.model.EventTriggerKind
import org.bukkit.block.data.BlockData

/**
 * issue #7 の信号変化の分類です。
 *
 * ボタン・レバーは `Switch` 型、感圧板は加重板（`AnaloguePowerable`）または
 * 感圧板素材の `Powerable` で判定します。感圧板に専用型はないため、
 * 素材名で裏付けます。Bukkit 実体を必要としない純粋判定であり、単体試験します。
 * どちらでもない信号変化（ドア・トラップドア等）は対象外として null を返します。
 */
internal object EventTriggerClassification {
    fun switchOrPlate(data: BlockData, typeName: String): EventTriggerKind? = when {
        data is org.bukkit.block.data.type.Switch -> EventTriggerKind.SWITCH
        data is org.bukkit.block.data.AnaloguePowerable ||
            (data is org.bukkit.block.data.Powerable && typeName.endsWith("_PRESSURE_PLATE"))
        -> EventTriggerKind.PRESSURE_PLATE
        else -> null
    }
}
