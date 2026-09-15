package me.awabi2048.kantancommander.hittest

import me.awabi2048.kantancommander.model.SpatialVector
import me.awabi2048.kantancommander.model.toSpatialVector
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import java.util.UUID

/**
 * issue #23 の高精度 Hit Test の実行層です。
 *
 * Player の視線 ray と選択 geometry の交差から、見えている部位に近い
 * Entity を選択します。候補収集（broad phase）は Bukkit の近傍取得で行い、
 * 部位ごとの narrow phase は [HitTestMath] の純粋計算を使います。
 * 複数部位・Entity に hit した場合は ray 距離が最も小さいものを優先します。
 *
 * #14 の操作ツールが選択方法として利用できる独立 API であり、
 * 操作ツールの session／UI とは結合しません。書込みは行わず、
 * geometry cache の保持だけが内部状態です。
 */
class HitTestEngine(
    /** 選択対象から除外する実体です。system 実体の検閲などを渡します。 */
    private val exclusion: (Entity) -> Boolean = { false },
    private val maxCacheSize: Int = DEFAULT_MAX_CACHE_SIZE,
) {
    /** 選択結果です。部位は provider がある場合だけ付きます。 */
    data class HitResult(
        val entityId: UUID,
        val distance: Double,
        val position: SpatialVector,
        val part: ArmorStandGeometry.ArmorStandPart?,
    )

    /**
     * Bukkit 実体を持たない選択入力です。unload・再起動をまたぐ保持は行わず、
     * 1回の選択要求の中でだけ使います。
     */
    data class EntitySnapshot(
        val entityId: UUID,
        val armorStand: ArmorStandGeometry.ArmorStandState?,
        val marker: Boolean,
        /** provider がない場合の fallback 境界です。 */
        val bounds: HitTestMath.HitAabb?,
    )

    private data class CacheEntry(val snapshot: SnapshotKey, val boxes: List<ArmorStandGeometry.PartBoxes>)

    /**
     * cache 照合キーです。pose・装備ではなく geometry へ影響する値だけを持ち、
     * 変更時に cache を無効化します。装備の体積は geometry に含めないため、
     * 装備変更での無効化は不要です。
     */
    private data class SnapshotKey(
        val position: SpatialVector,
        val yawDegrees: Double,
        val poses: Map<ArmorStandGeometry.ArmorStandPart, ArmorStandGeometry.BoneRotation>,
        val small: Boolean,
        val arms: Boolean,
        val basePlate: Boolean,
        val scale: Double,
    )

    private val cache = object : LinkedHashMap<UUID, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, CacheEntry>): Boolean =
            size > maxCacheSize
    }

    /**
     * 視線先の Entity を選択します。候補未指定時は Player 周辺の近傍実体を
     * 対象にします。hit しない場合は null を返し、「選択不能」と同義にしません
     * （呼び出し側が bounding box 等の次善手段を選べます）。
     */
    fun findNearest(player: Player, maxDistance: Double, candidates: List<Entity>? = null): HitResult? {
        if (!maxDistance.isFinite() || maxDistance <= 0.0) return null
        val eye = player.eyeLocation
        val direction = eye.direction.normalize()
        if (!direction.isFinite()) return null
        val ray = HitTestMath.HitRay(
            SpatialVector(eye.x, eye.y, eye.z),
            SpatialVector(direction.x, direction.y, direction.z),
        )
        val resolved = candidates ?: player.getNearbyEntities(maxDistance, maxDistance, maxDistance)
        val snapshots = resolved
            .filter { it.world == player.world && it.isValid && !it.isDead && !exclusion(it) }
            .mapNotNull(::snapshotOf)
        return selectNearest(ray, snapshots, maxDistance)
    }

    /**
     * 選択の中核です。Bukkit 実体を持たないため単体試験できます。
     *
     * 各 snapshot は broad phase（全体 AABB）→ narrow phase（部位 OBB）の順に
     * 判定し、最も手前の hit を返します。
     */
    fun selectNearest(
        ray: HitTestMath.HitRay,
        snapshots: List<EntitySnapshot>,
        maxDistance: Double,
    ): HitResult? {
        var nearest: HitResult? = null
        snapshots.forEach { snapshot ->
            hitSnapshot(ray, snapshot, maxDistance)?.let { hit ->
                if (nearest == null || hit.distance < nearest!!.distance) nearest = hit
            }
        }
        return nearest
    }

    private fun hitSnapshot(
        ray: HitTestMath.HitRay,
        snapshot: EntitySnapshot,
        maxDistance: Double,
    ): HitResult? {
        val boxes = boxesFor(snapshot)
        if (boxes.isEmpty()) return null
        val all = boxes.flatMap { it.boxes }
        // broad phase: 全体 AABB に入らない候補は narrow phase を行いません。
        val union = HitTestMath.unionAabb(all) ?: return null
        if (HitTestMath.intersectRayAabb(ray, union, maxDistance) == null) return null
        // narrow phase: 部位ごとに交差し、最も手前を採用します。
        var nearest: HitResult? = null
        boxes.forEach { partBoxes ->
            partBoxes.boxes.forEach { box ->
                val distance = HitTestMath.intersectRayBox(ray, box, maxDistance) ?: return@forEach
                if (nearest == null || distance < nearest!!.distance) {
                    nearest = HitResult(
                        entityId = snapshot.entityId,
                        distance = distance,
                        position = SpatialVector(
                            ray.origin.x + ray.direction.x * distance,
                            ray.origin.y + ray.direction.y * distance,
                            ray.origin.z + ray.direction.z * distance,
                        ),
                        part = partBoxes.part,
                    )
                }
            }
        }
        return nearest
    }

    private fun boxesFor(snapshot: EntitySnapshot): List<ArmorStandGeometry.PartBoxes> {
        val state = snapshot.armorStand
        if (state != null) {
            val key = SnapshotKey(
                state.position,
                state.yawDegrees,
                state.poses,
                state.small,
                state.arms,
                state.basePlate,
                state.scale,
            )
            cache[snapshot.entityId]?.takeIf { it.snapshot == key }?.let { return it.boxes }
            // pose・transform・scale の変更時にだけ再構成します。
            val boxes = ArmorStandGeometry.buildBoxes(state, snapshot.marker)
            cache[snapshot.entityId] = CacheEntry(key, boxes)
            return boxes
        }
        // provider がない Entity は通常 bounding box へ fallback します。
        // `high precision unavailable` を選択不能と同義にしません。
        val bounds = snapshot.bounds ?: return emptyList()
        val size = SpatialVector(
            bounds.maximum.x - bounds.minimum.x,
            bounds.maximum.y - bounds.minimum.y,
            bounds.maximum.z - bounds.minimum.z,
        )
        return listOf(
            ArmorStandGeometry.PartBoxes(
                null,
                listOf(HitTestMath.aabbBox(bounds.minimum, size)),
            ),
        )
    }

    /**
     * Bukkit 実体から選択入力を写します。marker・無効値は呼び出し側で
     * 扱えるよう null にせず snapshot へ残します。
     */
    fun snapshotOf(entity: Entity): EntitySnapshot? {
        if (entity.type == EntityType.ARMOR_STAND && entity is ArmorStand) {
            val location = entity.location
            fun degrees(radians: Double): Double = Math.toDegrees(radians)
            return EntitySnapshot(
                entityId = entity.uniqueId,
                armorStand = ArmorStandGeometry.ArmorStandState(
                    position = location.toSpatialVector(),
                    yawDegrees = location.yaw.toDouble(),
                    poses = mapOf(
                        ArmorStandGeometry.ArmorStandPart.HEAD to ArmorStandGeometry.BoneRotation(
                            degrees(entity.headPose.x), degrees(entity.headPose.y), degrees(entity.headPose.z),
                        ),
                        ArmorStandGeometry.ArmorStandPart.BODY to ArmorStandGeometry.BoneRotation(
                            degrees(entity.bodyPose.x), degrees(entity.bodyPose.y), degrees(entity.bodyPose.z),
                        ),
                        ArmorStandGeometry.ArmorStandPart.LEFT_ARM to ArmorStandGeometry.BoneRotation(
                            degrees(entity.leftArmPose.x), degrees(entity.leftArmPose.y), degrees(entity.leftArmPose.z),
                        ),
                        ArmorStandGeometry.ArmorStandPart.RIGHT_ARM to ArmorStandGeometry.BoneRotation(
                            degrees(entity.rightArmPose.x), degrees(entity.rightArmPose.y), degrees(entity.rightArmPose.z),
                        ),
                        ArmorStandGeometry.ArmorStandPart.LEFT_LEG to ArmorStandGeometry.BoneRotation(
                            degrees(entity.leftLegPose.x), degrees(entity.leftLegPose.y), degrees(entity.leftLegPose.z),
                        ),
                        ArmorStandGeometry.ArmorStandPart.RIGHT_LEG to ArmorStandGeometry.BoneRotation(
                            degrees(entity.rightLegPose.x), degrees(entity.rightLegPose.y), degrees(entity.rightLegPose.z),
                        ),
                    ),
                    small = entity.isSmall,
                    arms = entity.hasArms(),
                    basePlate = entity.hasBasePlate(),
                    scale = entityScale(entity),
                ),
                marker = entity.isMarker,
                bounds = entity.boundingBox.toAabb(),
            )
        }
        return EntitySnapshot(
            entityId = entity.uniqueId,
            armorStand = null,
            marker = false,
            bounds = entity.boundingBox.toAabb(),
        )
    }

    /** cache を全破棄します。大量実体の入れ替え後に呼び出します。 */
    fun cleanup() {
        cache.clear()
    }

    /** cache 件数です。性能確認用であり、選択結果には影響しません。 */
    fun cacheSize(): Int = cache.size

    private fun entityScale(entity: Entity): Double = runCatching {
        (entity as? ArmorStand)?.getAttribute(Attribute.SCALE)?.value ?: 1.0
    }.getOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 1.0

    private fun org.bukkit.util.BoundingBox.toAabb(): HitTestMath.HitAabb? =
        HitTestMath.HitAabb(
            SpatialVector(minX, minY, minZ),
            SpatialVector(maxX, maxY, maxZ),
        ).takeIf { it.isValid() }

    private fun org.bukkit.util.Vector.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    companion object {
        const val DEFAULT_MAX_CACHE_SIZE = 512
    }
}
