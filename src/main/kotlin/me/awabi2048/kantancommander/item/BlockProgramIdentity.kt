package me.awabi2048.kantancommander.item

import me.awabi2048.kantancommander.model.DiskScript
import org.bukkit.entity.Player

/**
 * 破壊時に出力するプログラム入り制御ブロックと、破壊者が既に持つ同内容品の判定です。
 *
 * 出力は毎回 `copyForItem` で新しいID・作成日時を持つため、ItemStackの直接比較では
 * 同一プログラムを検出できません。複製で変わる項目（ID・作成日時・一覧表示・
 * revision・折畳み状態）を除いた内容一致で「同一」とみなします。
 */
internal object BlockProgramIdentity {
    fun sameContent(first: DiskScript, second: DiskScript): Boolean =
        first.formatVersion == second.formatVersion &&
            first.name == second.name &&
            first.owner == second.owner &&
            first.activation == second.activation &&
            first.timer == second.timer &&
            first.graph == second.graph &&
            first.arguments == second.arguments &&
            first.contentModified == second.contentModified

    /** 破壊者が同内容のプログラム入り制御ブロックを既に持つかを返します。 */
    fun alreadyHeld(player: Player, program: DiskScript): Boolean =
        player.inventory.contents.any { item ->
            item != null &&
                KantanItemService.kind(item) == KantanItemKind.BLOCK &&
                KantanItemService.embeddedProgram(item)?.let { sameContent(it, program) } == true
        }
}
