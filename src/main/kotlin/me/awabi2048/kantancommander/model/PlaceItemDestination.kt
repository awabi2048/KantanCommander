package me.awabi2048.kantancommander.model

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import org.bukkit.Material

/**
 * 「アイテムを配置する」(PLACE_ITEM) の配置先です。
 *
 * バニラ `/item replace entity|block` の slot 概念を、対象の capability ごとに
 * 分離した保存形式です。EntityType の巨大な分岐を各所へ増やさず、対象が持つ
 * 能力に応じてこの adapter が配置先を解決します。
 *
 * 保存値は GUI 表示と分離します。数値スロットは 0 始まりで保存し、GUI では
 * 1 始まりの意味表示へ変換します。Bukkit/Paper の内部番号をそのまま
 * 利用者へ見せません。
 */
enum class PlaceItemDestination(
    /** params["destination"] へ保存する値です。 */
    val paramValue: String,
    val labelKey: LocalizationKey<String>,
    /** エンティティ対象 (TargetSpec) を要求するかを返します。 */
    val requiresEntityTarget: Boolean,
    /** ブロック位置 (blockPositionSpec) を要求するかを返します。 */
    val requiresBlockPosition: Boolean,
) {
    EQUIPMENT("EQUIPMENT", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESTINATION_EQUIPMENT, true, false),
    PLAYER_HOTBAR("PLAYER_HOTBAR", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESTINATION_PLAYER_HOTBAR, true, false),
    PLAYER_INVENTORY("PLAYER_INVENTORY", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESTINATION_PLAYER_INVENTORY, true, false),
    ITEM_FRAME("ITEM_FRAME", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESTINATION_ITEM_FRAME, true, false),
    BLOCK_CONTAINER("BLOCK_CONTAINER", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESTINATION_BLOCK_CONTAINER, false, true),
    ;

    companion object {
        /** 装備スロットの保存名です。Bukkit の EquipmentSlot と同名にします。 */
        val EQUIPMENT_SLOTS = setOf("HAND", "OFF_HAND", "HEAD", "CHEST", "LEGS", "FEET")

        /** 額縁内容の固定スロット値です。 */
        const val ITEM_FRAME_CONTENTS = "CONTENTS"

        /** ホットバーの保存index範囲です (0始まり)。 */
        val HOTBAR_RANGE = 0..8

        /** メインインベントリの保存index範囲です (0始まり)。 */
        val INVENTORY_RANGE = 0..26

        /** ブロックコンテナの保存index範囲です (0始まり、バニラ container.* の最大域)。 */
        val CONTAINER_RANGE = 0..53

        fun from(raw: String?): PlaceItemDestination? =
            entries.firstOrNull { it.paramValue == raw }

        /** 配置先ごとの既定スロット値を返します。 */
        fun defaultSlot(destination: PlaceItemDestination): String = when (destination) {
            EQUIPMENT -> "HAND"
            PLAYER_HOTBAR, PLAYER_INVENTORY, BLOCK_CONTAINER -> "0"
            ITEM_FRAME -> ITEM_FRAME_CONTENTS
        }

        /** 配置先とスロット値の組合せが保存形式として正当かを返します。 */
        fun isValidSlot(destination: PlaceItemDestination, slot: String): Boolean = when (destination) {
            EQUIPMENT -> slot in EQUIPMENT_SLOTS
            PLAYER_HOTBAR -> slot.toIntOrNull() in HOTBAR_RANGE
            PLAYER_INVENTORY -> slot.toIntOrNull() in INVENTORY_RANGE
            ITEM_FRAME -> slot == ITEM_FRAME_CONTENTS
            BLOCK_CONTAINER -> slot.toIntOrNull() in CONTAINER_RANGE
        }

        /**
         * 保存スロット値を GUI 表示へ変換します。
         *
         * 数値スロットは 1 始まりの意味表示にし、保存形式 (0始まり) と分離します。
         * 不正値は表示用にそのまま返し、検証側で拒否します。
         */
        fun toDisplay(destination: PlaceItemDestination, slot: String): String = when (destination) {
            PLAYER_HOTBAR, PLAYER_INVENTORY, BLOCK_CONTAINER ->
                slot.toIntOrNull()?.let { (it + 1).toString() } ?: slot
            EQUIPMENT, ITEM_FRAME -> slot
        }

        /**
         * GUI 入力 (1始まり) を保存形式 (0始まり) へ変換します。
         *
         * 変換できない入力は null を返し、検証側で拒否します。
         */
        fun fromDisplay(destination: PlaceItemDestination, display: String): String? = when (destination) {
            PLAYER_HOTBAR, PLAYER_INVENTORY, BLOCK_CONTAINER ->
                display.trim().toIntOrNull()?.takeIf { it >= 1 }?.let { (it - 1).toString() }
                    ?.takeIf { isValidSlot(destination, it) }
            EQUIPMENT -> display.takeIf { it in EQUIPMENT_SLOTS }
            ITEM_FRAME -> display.takeIf { it == ITEM_FRAME_CONTENTS }
        }

        /**
         * バニラ `/item replace` の slot 記法へ変換します。
         *
         * 保存値が正当であることを前提にし、不正値では null を返します。
         */
        fun vanillaSlot(destination: PlaceItemDestination, slot: String): String? = when (destination) {
            EQUIPMENT -> when (slot) {
                "HAND" -> "weapon.mainhand"
                "OFF_HAND" -> "weapon.offhand"
                "HEAD" -> "armor.head"
                "CHEST" -> "armor.chest"
                "LEGS" -> "armor.legs"
                "FEET" -> "armor.feet"
                else -> null
            }
            PLAYER_HOTBAR -> slot.toIntOrNull()?.takeIf { it in HOTBAR_RANGE }?.let { "hotbar.$it" }
            PLAYER_INVENTORY -> slot.toIntOrNull()?.takeIf { it in INVENTORY_RANGE }?.let { "inventory.$it" }
            ITEM_FRAME -> "contents".takeIf { slot == ITEM_FRAME_CONTENTS }
            BLOCK_CONTAINER -> slot.toIntOrNull()?.takeIf { it in CONTAINER_RANGE }?.let { "container.$it" }
        }

        /**
         * 上書き禁止時の空き検査に使うNBTパスを返します。
         *
         * 実行側の空判定（nullまたは空気）と合わせ、NBT上に存在しない
         * スロットを空とみなします。空のスタックはシリアライズされないため、
         * パスの不存在は空と等価です。
         * バニラの `inventory.N` はホットバー（0〜8）を除く本体域の番号であり、
         * NBTのSlotバイト（本体域は9〜35）へ写像します。ホットバー・コンテナは
         * 保存indexがそのままSlotバイトです。
         * 装備先は対象の種類でNBT構造が変わり（MobのHandItems／ArmorItemsと
         * PlayerのSelectedItem／Inventoryでは空の表現が異なる）、出力時に
         * 対象種別を確定できないため等価な検査を作れず null を返します。
         * null の場合は出力検証で未対応として拒否します。
         */
        fun emptyCheckNbtPath(destination: PlaceItemDestination, slot: String): String? = when (destination) {
            BLOCK_CONTAINER -> slot.toIntOrNull()?.takeIf { it in CONTAINER_RANGE }?.let { "Items[{Slot:${it}b}]" }
            ITEM_FRAME -> "Item".takeIf { slot == ITEM_FRAME_CONTENTS }
            PLAYER_HOTBAR -> slot.toIntOrNull()?.takeIf { it in HOTBAR_RANGE }?.let { "Inventory[{Slot:${it}b}]" }
            PLAYER_INVENTORY -> slot.toIntOrNull()?.takeIf { it in INVENTORY_RANGE }?.let { "Inventory[{Slot:${it + 9}b}]" }
            EQUIPMENT -> null
        }

        /**
         * 配置個数が素材のスタック上限を超えるかを返します。
         *
         * リテラル整数だけを判定し、変数・一時参照混じりは false とします
         * （実行時に解決されるため、静的には保証も否定もできません）。
         * 上限値の解決は呼び出し側が行い、解決不能時（サーバーなし試験など）は
         * 検査を飛ばします。
         */
        fun countExceedsMax(rawCount: String, maxStackSize: Int): Boolean {
            val parsed = rawCount.toIntOrNull() ?: return false
            return parsed > maxStackSize
        }

        /**
         * 素材のスタック上限を返します。解決不能時は null を返します。
         *
         * 現行Paperでは上限がレジストリ参照のため、サーバーなし試験では
         * 解決できません。呼び出し側は null を検査飛ばしとして扱います。
         */
        fun materialMaxStackSize(material: Material): Int? =
            runCatching { material.maxStackSize }.getOrNull()

        /**
         * Player インベントリ操作用の Bukkit スロット番号へ変換します。
         *
         * ホットバーは 0..8 をそのまま、メインインベントリは storage 域 9..35 へ
         * 写像します。不正値では null を返します。
         */
        fun bukkitPlayerSlot(destination: PlaceItemDestination, slot: String): Int? = when (destination) {
            PLAYER_HOTBAR -> slot.toIntOrNull()?.takeIf { it in HOTBAR_RANGE }
            PLAYER_INVENTORY -> slot.toIntOrNull()?.takeIf { it in INVENTORY_RANGE }?.let { it + 9 }
            EQUIPMENT, ITEM_FRAME, BLOCK_CONTAINER -> null
        }
    }
}
