package me.awabi2048.kantancommander.gui

import java.util.UUID

/**
 * Slider／Checkbox等の連続操作と確定保存を分離する契約です。
 *
 * issue #15 と #18 の共有契約であり、#15側で保存・同期・破棄の基盤を、
 * #18側で操作コントロールの見た目と入力仕様を担当します。
 * 値はplayer／session-localな一時値として保持し、commit時にだけ
 * script revisionを進めます。Bukkitへ依存しないため単体テストで検証します。
 */
data class FieldPreviewKey(
    val nodeId: UUID,
    val fieldKey: String,
)

/**
 * セッション内に閉じた一時previewの保管場所です。
 *
 * params相当の文字列表現だけを扱い、意味解釈・検証・保存は呼び出し側で行います。
 * commit／cancelのどちらでも必ず空に戻し、detach時に残留させません。
 */
class TransientPreviewStore {
    private val previews = linkedMapOf<FieldPreviewKey, String?>()

    /** commit対象の正本世代です。Dialog系のCASと同様に保存時の期待世代に使います。 */
    var revisionBase: Long? = null
        private set

    fun begin(revisionBase: Long?) {
        this.revisionBase = revisionBase
    }

    fun update(nodeId: UUID, fieldKey: String, previewValue: String?) {
        previews[FieldPreviewKey(nodeId, fieldKey)] = previewValue
    }

    fun current(nodeId: UUID, fieldKey: String): String? =
        previews[FieldPreviewKey(nodeId, fieldKey)]

    fun snapshot(): Map<FieldPreviewKey, String?> = previews.toMap()

    val hasPreview: Boolean get() = previews.isNotEmpty()

    val count: Int get() = previews.size

    /**
     * commit適用のために内容を取り出して空に戻します。
     *
     * 取り出し後に保存が失敗した場合は呼び出し側で [restore] し、
     * 最終値の消失を防ぎます。
     */
    fun consumeForCommit(): Map<FieldPreviewKey, String?> {
        val taken = previews.toMap()
        previews.clear()
        return taken
    }

    fun restore(values: Map<FieldPreviewKey, String?>) {
        values.forEach { (key, value) -> previews[key] = value }
    }

    fun cancel() {
        previews.clear()
    }

    fun remove(nodeId: UUID, fieldKey: String) {
        previews.remove(FieldPreviewKey(nodeId, fieldKey))
    }

    fun removeNode(nodeId: UUID) {
        previews.keys.filter { it.nodeId == nodeId }.forEach(previews::remove)
    }

    /**
     * 他editorへの低頻度共有が必要かを判定します。
     *
     * 常時全送信は行わず、明示channelへ間引いた要点だけを送ります。
     * 共有が不要なpreviewはこの判定を通さず破棄します。
     */
    fun shouldSharePreview(nowMillis: Long, lastBroadcastAtMillis: Long, minIntervalMillis: Long = 200L): Boolean {
        if (!hasPreview) return false
        return nowMillis - lastBroadcastAtMillis >= minIntervalMillis
    }
}

/** 他editorへ間引いて送るpreview要点です。revisionは進めません。 */
data class SharedPreviewSummary(
    val scriptId: UUID,
    val revisionBase: Long?,
    val previews: Map<FieldPreviewKey, String?>,
    val updatedAtMillis: Long,
)

/** 共有previewの送信間引き条件を一箇所へ集約します。 */
object SharedPreviewThrottle {
    fun shouldBroadcast(nowMillis: Long, lastBroadcastAtMillis: Long, minIntervalMillis: Long = 200L): Boolean =
        nowMillis - lastBroadcastAtMillis >= minIntervalMillis
}
