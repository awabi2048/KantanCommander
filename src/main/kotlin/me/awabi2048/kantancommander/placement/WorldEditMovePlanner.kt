package me.awabi2048.kantancommander.placement

import me.awabi2048.kantancommander.model.DiskPlacement
import java.util.UUID

/**
 * 基準点からの相対差分で保持する配置項目です。
 *
 * `placementKey` は `"$world,$x,$y,$z"` 形式の台帳キーであり、移動元の
 * 特定に使います。`scriptId` と `facing` は移動先への再登録に使います。
 * `sourceWasGlass` は確保時に移動元が配置素材だったかを表し、既に drift
 * している台帳残存を移動判定から除外するために使います。
 */
internal data class RelativePlacement(
    val placementKey: String,
    val scriptId: UUID,
    val facing: String,
    val relativeX: Int,
    val relativeY: Int,
    val relativeZ: Int,
    val sourceWasGlass: Boolean,
)

/**
 * 移動項目の確定方針です。
 *
 * 重なり移動（A→B・B→C等）を逐次処理すると、先行項目が後続の移動元登録を
 * 「既存」として消すため、移動写像を一括評価してから確定します。
 */
internal enum class MoveItemAction {
    /** 同じスクリプトを移動先へ移します。移動元台帳は除去します。 */
    MOVE_SAME,
    /** 移動元が残存したままのため、複写相当として新しいスクリプトで登録します。 */
    COPY_NEW,
    /** 移動元も移動先も実体でないため、残った台帳を消去として掃除します。 */
    ERASE,
    /** 移動先に実体がなく移動元が残るため、台帳不変で確定を見送ります。 */
    SKIP,
    /** 既に正しく登録済みのため、何もしません。 */
    NOOP,
}

/** 移動計画の1項目です。Bukkit非依存のため単体テストします。 */
internal data class PlannedMoveItem(
    val relative: RelativePlacement,
    val source: Triple<Int, Int, Int>,
    val target: Triple<Int, Int, Int>,
    val action: MoveItemAction,
    val overlappedExisting: DiskPlacement?,
)

/**
 * issue #review-1: 移動写像の一括評価器です。
 *
 * 全項目の移動元→移動先を確定前に分類し、重なり（移動先が別項目の
 * 移動元と一致）を正しく扱います。Bukkit非依存のため単体テストします。
 */
internal object WorldEditMovePlanner {
    /**
     * @param items 確保時の移動項目（sourceWasGlass のものだけ渡します）
     * @param sourceOf 項目の移動元絶対座標
     * @param targetOf 移動元絶対座標の移動先絶対座標
     * @param inHeight 移動先がワールド高さ範囲内か
     * @param isGlass 現在その座標が配置素材か
     * @param existingAt 移動先の既存台帳（なければ null）
     * @param batchSourceKeys 自バッチの移動元台帳キー集合
     */
    fun planMoveBatch(
        items: List<RelativePlacement>,
        sourceOf: (RelativePlacement) -> Triple<Int, Int, Int>,
        targetOf: (Triple<Int, Int, Int>) -> Triple<Int, Int, Int>,
        inHeight: (Triple<Int, Int, Int>) -> Boolean,
        isGlass: (Triple<Int, Int, Int>) -> Boolean,
        existingAt: (Triple<Int, Int, Int>) -> DiskPlacement?,
        batchSourceKeys: Set<String>,
    ): List<PlannedMoveItem> {
        return items.map { item ->
            val source = sourceOf(item)
            val target = targetOf(source)
            if (!inHeight(target)) {
                return@map PlannedMoveItem(item, source, target, MoveItemAction.SKIP, null)
            }
            val targetGlass = isGlass(target)
            val sourceGlass = isGlass(source)
            if (!targetGlass) {
                // 移動先に実体がなく移動元もなければ消去、移動元が残れば失敗扱いで不変です。
                return@map PlannedMoveItem(
                    item, source, target,
                    if (!sourceGlass) MoveItemAction.ERASE else MoveItemAction.SKIP,
                    null,
                )
            }
            val existing = existingAt(target)
            if (existing != null && existing.scriptId == item.scriptId) {
                return@map PlannedMoveItem(item, source, target, MoveItemAction.NOOP, null)
            }
            // 移動先の既存登録が自バッチの移動元でもある場合（重なり）は、
            // その移動元の確定処理が台帳を扱うため、ここでは上書きしません。
            // スクリプト削除の対象は自バッチ外の既存だけです。
            val overlapped = if (existing != null && existing.key !in batchSourceKeys) existing else null
            PlannedMoveItem(
                item, source, target,
                if (!sourceGlass) MoveItemAction.MOVE_SAME else MoveItemAction.COPY_NEW,
                overlapped,
            )
        }
    }
}
