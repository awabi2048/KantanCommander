package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.model.EventTriggerKind
import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.entity.Player

/**
 * issue #7 の起動方法の表示を一箇所へ集約します。
 *
 * 下部パネルの起動方法カードとプログラム一覧は、どちらも実行時に採用する
 * [me.awabi2048.kantancommander.model.effectiveTrigger] の要約を示します。
 * 名称解決だけを担当し、設定値の保存や検証は行いません。
 */
internal object TriggerDisplay {
    /** 起動種別の固定名称です。選択肢の識別ラベルとしてそのまま示します。 */
    fun kindName(player: Player?, kind: TriggerKind): String = when (kind) {
        TriggerKind.TIMER -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_TRIGGER_TIMER)
        TriggerKind.REDSTONE -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_TRIGGER_REDSTONE)
        TriggerKind.EVENT -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TRIGGER)
    }

    /** イベント種別の固定名称です。選択肢の識別ラベルとしてそのまま示します。 */
    fun eventName(player: Player?, event: EventTriggerKind): String = when (event) {
        EventTriggerKind.BLOCK_BREAK -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_TRIGGER_EVENT_BLOCK_BREAK)
        EventTriggerKind.BLOCK_PLACE -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_TRIGGER_EVENT_BLOCK_PLACE)
        EventTriggerKind.BUTTON_ON -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_TRIGGER_EVENT_BUTTON_ON)
        EventTriggerKind.LEVER_ON -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_TRIGGER_EVENT_LEVER_ON)
        EventTriggerKind.LEVER_OFF -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_TRIGGER_EVENT_LEVER_OFF)
        EventTriggerKind.PRESSURE_PLATE_ON ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_TRIGGER_EVENT_PRESSURE_PLATE_ON)
        EventTriggerKind.ENTITY_LEFT_CLICK ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_TRIGGER_EVENT_ENTITY_LEFT_CLICK)
        EventTriggerKind.ENTITY_RIGHT_CLICK ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_TRIGGER_EVENT_ENTITY_RIGHT_CLICK)
        EventTriggerKind.BLOCK_LEFT_CLICK ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_TRIGGER_EVENT_BLOCK_LEFT_CLICK)
        EventTriggerKind.BLOCK_RIGHT_CLICK ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_TRIGGER_EVENT_BLOCK_RIGHT_CLICK)
    }

    /**
     * カード・一覧に示す起動方法の要約です。
     *
     * タイマーは従来どおり「有効＋間隔」を示し、信号・イベントは種別名を示します。
     * 現在値の表示欄であり、Name の固定ラベルには含めません。
     */
    fun summary(player: Player?, trigger: ScriptTrigger): String = when (trigger.kind) {
        TriggerKind.TIMER -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ENABLED) + " " +
            KcI18n.text(
                player,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INTERVAL_SECONDS,
                mapOf("value" to CommandDialogSpecs.formatSeconds(trigger.intervalSeconds)),
            )
        TriggerKind.REDSTONE -> kindName(player, TriggerKind.REDSTONE)
        TriggerKind.EVENT -> trigger.event?.let { eventName(player, it) }
            ?: kindName(player, TriggerKind.EVENT)
    }
}
