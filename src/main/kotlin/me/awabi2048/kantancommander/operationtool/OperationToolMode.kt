package me.awabi2048.kantancommander.operationtool

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import com.awabi2048.ccsystem.api.localization.LocalizationKey

/**
 * issue #14の操作ツールwandの選択モードです。
 *
 * スニーク + スクロールで [next] の順に循環します。モード変更時は
 * その時点の選択状態と選択Undo履歴を解除します（[OperationToolSession.setMode]）。
 */
enum class OperationToolMode {
    /** WorldEditに近い操作感のエリア選択です。左=pos1・右=pos2です。 */
    AREA,

    /** Block Displayによるリアルタイムな範囲選択です。常に加算型です。 */
    ENTITY_RANGE,

    /** 視線上のEntityを直接選択します。常に加算型です。 */
    ENTITY_DIRECT;

    /** スニーク + スクロールでの次のモードです。 */
    fun next(): OperationToolMode = when (this) {
        AREA -> ENTITY_RANGE
        ENTITY_RANGE -> ENTITY_DIRECT
        ENTITY_DIRECT -> AREA
    }

    /** モード表示名の言語キーです。固定ラベルと可変値を結合しません。 */
    fun displayNameKey(): LocalizationKey<String> = when (this) {
        AREA -> KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_MODE_AREA
        ENTITY_RANGE -> KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_MODE_ENTITY_RANGE
        ENTITY_DIRECT -> KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_MODE_ENTITY_DIRECT
    }
}
