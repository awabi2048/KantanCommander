package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType

/**
 * テストログのホバーに出す実効値を、エディタ内の表示へ合わせる対応付けです。
 *
 * 実行器が渡す内部キー（`seconds`等）をそのまま出すと、エディタの設定項目名
 * （「秒数」等）とずれて読み替えが必要になります。同じJSONキーでもコマンドごとに
 * 意味が異なるため、エディタと同じ[EditorMenuLayout.fields]の対応表を使います。
 * 値は実行時に展開された実効値のまま出し、項目名だけを合わせます。
 * ジェスチャーエディタの現在値行も展開前の原文を表示するため、書式は一致します。
 */
internal object TestLogParamPresentation {
    /**
     * 内部キーに対応するエディタ項目名です。対応がないときはnullを返し、
     * 呼び出し側で内部キーをそのまま表示します。
     */
    fun labelKey(type: CommandType, node: CommandNode?, paramKey: String): LocalizationKey<String>? =
        EditorMenuLayout.fields(type, node).firstOrNull { it.key == paramKey }?.label
}
