package me.awabi2048.kantancommander.gui

import java.util.UUID

/**
 * issue #13 の共同編集 presence です。
 *
 * 同一 script を開いている Player を可視化します。内容更新（revision）と
 * presence 等の ephemeral 状態は分離し、ここでは開閉だけを扱います。
 * 保存・検証・正本へは触れません。メモリ保持のみであり、再起動で消えます。
 *
 * 将来的に「選択中 node／編集中 field」を表示できるよう、記録単位は
 * script 単位のまま拡張できる形にします。
 */
class EditorPresenceTracker {
    /** Player ごとの編集中 script です。開いていない Player は含みません。 */
    private val editing = linkedMapOf<UUID, UUID>()

    /** script を開きます。同 Player の旧記録は新しい方へ置き換えます。 */
    fun open(scriptId: UUID, playerId: UUID) {
        editing[playerId] = scriptId
    }

    /** Player の記録を消します。開いていない Player は何もしません。 */
    fun close(playerId: UUID) {
        editing.remove(playerId)
    }

    /** 全記録を消します。無効化時の残留防止に使います。 */
    fun closeAll() {
        editing.clear()
    }

    /** 指定 script を開いている Player の一覧を返します。順序は開放順です。 */
    fun editorsFor(scriptId: UUID): List<UUID> =
        editing.filterValues { it == scriptId }.keys.toList()

    /** 指定 Player が開いている script を返します。開いていなければ null です。 */
    fun scriptFor(playerId: UUID): UUID? = editing[playerId]
}
