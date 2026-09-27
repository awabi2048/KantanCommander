package me.awabi2048.kantancommander.operationtool

import me.awabi2048.kantancommander.model.RegionMath
import me.awabi2048.kantancommander.model.SpatialVector
import java.util.UUID

/**
 * issue #14の操作ツールwandのプレイヤー単位の選択状態です。
 *
 * Bukkit実体は保持せず、EntityはUUID・位置は [SpatialVector] で扱います。
 * Entity選択は範囲・直接のどちらでも常に加算型とし、各確定操作の差分を
 * 履歴として保持します。左クリックUndoはこの履歴のみ対象とし、タグ変更・
 * 削除・Group作成・Structure保存等のdomain操作は取り消しません。
 *
 * 選択状態の解除は、サーバー再起動（session破棄）・モード変更・ワールド移動・
 * 明示的全解除の場合に限ります。ツールの持ち替え・GUI閉鎖等だけでは解除しません。
 */
class OperationToolSession(
    /** Entity最大選択数です。EasyArmorStandsと初期値を揃え、設定値として扱います。 */
    val maxEntities: Int = DEFAULT_MAX_ENTITIES,
    /** Box選択の最大一辺です。EasyArmorStandsと初期値を揃え、設定値として扱います。 */
    val maxBoxEdge: Double = DEFAULT_MAX_BOX_EDGE,
) {
    var mode: OperationToolMode = OperationToolMode.AREA
        private set

    /** エリア選択のpos1です。ワールド名と連続座標を保持します。 */
    var pos1: RegionPoint? = null
        private set

    /** エリア選択のpos2です。ワールド名と連続座標を保持します。 */
    var pos2: RegionPoint? = null
        private set

    /** 現在編集中の未確定Boxです。確定・キャンセルでnullへ戻ります。 */
    var editingBox: EditingBox? = null
        private set

    private val selectedEntities = linkedSetOf<UUID>()
    private val undoHistory = ArrayDeque<Set<UUID>>()

    /** 現在のEntity選択集合のスナップショットです。 */
    fun selected(): Set<UUID> = selectedEntities.toSet()

    /**
     * ワールドへ紐付く選択状態（Entity選択・エリア指定・未確定Box）が残っているかを返します。
     *
     * ワールド移動時の解除判定に使います。選択Undo履歴だけが残っている状態は
     * 対象Entityを持たないためfalseとし、通知なしに解除できます。
     */
    fun hasSelectionState(): Boolean =
        selectedEntities.isNotEmpty() || pos1 != null || pos2 != null || editingBox != null

    /** 選択Undo履歴の深さです。 */
    fun undoDepth(): Int = undoHistory.size

    /**
     * モードを変更し、その時点の選択状態と選択Undo履歴をすべて解除します。
     *
     * 同じモードへの変更でも解除します。戻り値は変更後のモードです。
     */
    fun setMode(next: OperationToolMode): OperationToolMode {
        mode = next
        clearAll()
        return mode
    }

    /** 左クリックしたBlockをpos1として設定します。再指定時は更新します。 */
    fun setPos1(point: RegionPoint) {
        pos1 = point
    }

    /** 右クリックしたBlockをpos2として設定します。再指定時は更新します。 */
    fun setPos2(point: RegionPoint) {
        pos2 = point
    }

    /** 有効なエリア（同ワールドのpos1・pos2両指定）が選択済みかどうかを返します。 */
    fun hasValidArea(): Boolean {
        val first = pos1 ?: return false
        val second = pos2 ?: return false
        return first.worldName == second.worldName
    }

    /**
     * 有効なエリアのCuboid範囲を返します。無効な場合はnullです。
     *
     * 正規化は [RegionMath.cuboidFromCorners] へ委譲し、重複実装しません。
     */
    fun areaBounds(): RegionMath.CuboidBounds? {
        val first = pos1 ?: return null
        val second = pos2 ?: return null
        if (first.worldName != second.worldName) return null
        return RegionMath.cuboidFromCorners(first.position, second.position)
    }

    /** 未確定Boxの編集を開始します。以前の表示は置き換え対象です。 */
    fun startBoxEdit(box: EditingBox) {
        editingBox = box
    }

    /** 未確定Boxの編集中端点を更新します。未編集中は何もしません。 */
    fun updateBoxEdit(current: SpatialVector) {
        editingBox = editingBox?.copy(current = current)
    }

    /** 未確定Boxをキャンセルします。確定済み選択自体は保持します。 */
    fun cancelBoxEdit() {
        editingBox = null
    }

    /**
     * 候補Entityを選択集合へ加算します。
     *
     * 既に選択済みの重複は追加せず、未選択の候補のみを追加します。
     * 上限到達後はそれ以上追加しません。新たに追加された差分が空でない場合のみ
     * 履歴へ積みます。戻り値は今回新たに追加されたEntityです。
     */
    fun addEntities(candidates: Collection<UUID>): Set<UUID> {
        val added = linkedSetOf<UUID>()
        for (candidate in candidates) {
            if (selectedEntities.size + added.size >= maxEntities) break
            if (selectedEntities.contains(candidate) || !added.add(candidate)) continue
        }
        if (added.isNotEmpty()) {
            selectedEntities.addAll(added)
            undoHistory.addLast(added.toSet())
        }
        return added
    }

    /**
     * 直前の確定済み選択操作をUndoします。
     *
     * その操作で新たに追加されたEntityのみを選択から外します。
     * 戻り値は外されたEntityであり、履歴が空の場合は空集合です。
     */
    fun undoLast(): Set<UUID> {
        val last = undoHistory.removeLastOrNull() ?: return emptySet()
        selectedEntities.removeAll(last)
        return last
    }

    /** 消失済みEntityを選択集合と履歴から外します。操作直前に呼び出します。 */
    fun retainExisting(alive: (UUID) -> Boolean) {
        selectedEntities.retainAll(alive)
        for (index in undoHistory.indices) {
            undoHistory[index] = undoHistory[index].filterTo(linkedSetOf(), alive)
        }
        while (undoHistory.isNotEmpty() && undoHistory.last().isEmpty()) {
            undoHistory.removeLast()
        }
        while (undoHistory.isNotEmpty() && undoHistory.first().isEmpty()) {
            undoHistory.removeFirst()
        }
    }

    /**
     * 現在の選択と選択Undo履歴・エリア指定・未確定Boxをすべて解除します。
     *
     * スニーク + 左クリック等の明示的全解除とモード変更時に使用します。
     */
    fun clearAll() {
        selectedEntities.clear()
        undoHistory.clear()
        pos1 = null
        pos2 = null
        editingBox = null
    }

    companion object {
        /** Entity最大選択数の既定値です。EasyArmorStandsと初期値を揃えます。 */
        const val DEFAULT_MAX_ENTITIES = 64

        /** Box選択の最大一辺の既定値です。EasyArmorStandsと初期値を揃えます。 */
        const val DEFAULT_MAX_BOX_EDGE = 128.0

        /**
         * Boxの一辺が上限以内かどうかを返します。上限超過時は現在のBox選択を
         * キャンセルするため、呼び出し側はfalseの場合に [cancelBoxEdit] します。
         */
        fun isBoxEdgeWithinLimit(minimum: SpatialVector, maximum: SpatialVector, maxEdge: Double): Boolean {
            val edge = maxOf(
                maximum.x - minimum.x,
                maximum.y - minimum.y,
                maximum.z - minimum.z,
            )
            return edge.isFinite() && edge <= maxEdge
        }

        /**
         * エリア選択の表示用AABBを返します。
         *
         * [RegionMath.cuboidFromCorners] のblock含み（+1.0）を表示へ換算し、
         * 選択範囲と表示範囲を一致させます。
         */
        fun areaDisplayBounds(bounds: RegionMath.CuboidBounds): Pair<SpatialVector, SpatialVector> {
            val minimum = bounds.minimum
            val maximum = SpatialVector(
                minimum.x + bounds.dx + 1.0,
                minimum.y + bounds.dy + 1.0,
                minimum.z + bounds.dz + 1.0,
            )
            return minimum to maximum
        }
    }
}

/** エリア選択の端点です。Block単位へのsnapは行わず連続座標で保持します。 */
data class RegionPoint(
    val worldName: String,
    val position: SpatialVector,
)

/**
 * 未確定Boxの編集中状態です。
 *
 * [anchor] は開始時点の視点から視線方向へ2.0 block進んだ基準点であり、
 * [current] は現在の視点から視線方向へ2.0 block進んだ点を毎tick更新します。
 */
data class EditingBox(
    val worldName: String,
    val anchor: SpatialVector,
    val current: SpatialVector,
) {
    /** 2点から形成される連続座標のAABB最小・最大点です。 */
    fun bounds(): Pair<SpatialVector, SpatialVector> {
        val minimum = SpatialVector(
            kotlin.math.min(anchor.x, current.x),
            kotlin.math.min(anchor.y, current.y),
            kotlin.math.min(anchor.z, current.z),
        )
        val maximum = SpatialVector(
            kotlin.math.max(anchor.x, current.x),
            kotlin.math.max(anchor.y, current.y),
            kotlin.math.max(anchor.z, current.z),
        )
        return minimum to maximum
    }
}
