package me.awabi2048.kantancommander.hittest

import me.awabi2048.kantancommander.model.SpatialVector

/**
 * issue #23 の ArmorStand 選択 geometry です。
 *
 * Bukkit の Entity bounding box は当たり判定用 AABB であり、頭・胴・腕・脚・
 * 装備など実際に見えている形状と一致しません。ここでは部位ごとの回転直方体を
 * pose・yaw・small・scale から再構成し、ray との narrow phase に使います。
 *
 * 寸法・pivot・階層の根拠は Mojang 公開の Bedrock geometry
 * （`resource_pack/models/entity/armor_stand.geo.json`）であり、Geyser が
 * Java の pose を同一ボーンへ写像して Java 版の見た目を再現していることから、
 * Java 版の選択形状としても採用します。単位は 16 分割であるため、
 * ブロック単位へ換算して保持します。
 *
 * 装備（手持ち・頭部アイテム等）の体積は含めません。表示可否の判定が
 * 複雑になるためであり、制限として明示します。hat（頭部の外層）は
 * ヘルメット装備時の表示層であるため通常の頭部には含めません。
 */
object ArmorStandGeometry {
    /** 選択部位です。#10 の ArmorStand 設定項目と名称を揃えます。 */
    enum class ArmorStandPart {
        HEAD,
        BODY,
        LEFT_ARM,
        RIGHT_ARM,
        LEFT_LEG,
        RIGHT_LEG,
        BASE_PLATE,
    }

    /** ボーン回転の度数 (x, y, z) です。Bukkit の EulerAngle（radian）から換算します。 */
    data class BoneRotation(val x: Double, val y: Double, val z: Double) {
        fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
    }

    /**
     * geometry 再構成の入力です。Bukkit 実体を持たず、実行層が Entity から
     * 写した値だけを使います。unload・再起動をまたぐ保持は行いません。
     */
    data class ArmorStandState(
        /** 足元中心の連続座標です。 */
        val position: SpatialVector,
        /** Bukkit yaw の度数です。0 で南方（+Z）を向きます。 */
        val yawDegrees: Double,
        /** 部位ごとの pose です。未指定部位は無回転です。 */
        val poses: Map<ArmorStandPart, BoneRotation> = emptyMap(),
        val small: Boolean = false,
        val arms: Boolean = false,
        val basePlate: Boolean = true,
        /** `generic.scale` 属性値です。small とは別に乗算します。 */
        val scale: Double = 1.0,
    ) {
        fun isValid(): Boolean =
            position.isFinite() && yawDegrees.isFinite() && scale.isFinite() && scale > 0.0 &&
                poses.values.all { it.isFinite() }
    }

    /**
     * 部位とその直方体群です。BODY のように複数 cube を持つ部位があります。
     * fallback（部位概念なし）の場合は null です。
     */
    data class PartBoxes(val part: ArmorStandPart?, val boxes: List<HitTestMath.HitBox>)

    /**
     * 状態から部位ごとの直方体を再構成します。
     *
     * 適用順序は「ボーン回転（子→親）→ entity yaw → 一様 scale → 平行移動」です。
     * marker は選択不能であるため空を返します。腕・台座は表示 flag が偽の
     * 場合に除外します。頭・胴・脚は常に表示されます。
     */
    fun buildBoxes(state: ArmorStandState, marker: Boolean): List<PartBoxes> {
        if (marker || !state.isValid()) return emptyList()
        val totalScale = (if (state.small) SMALL_SCALE else 1.0) * state.scale
        // yaw 0（南方・+Z向き）を基準とし、Bukkit yaw へ回転します。
        val yawRotation = Triple(0.0, -state.yawDegrees, 0.0)
        fun place(box: HitTestMath.HitBox, pivot: SpatialVector, pose: BoneRotation): HitTestMath.HitBox {
            val turned = HitTestMath.rotateBox(box, pivot, Triple(pose.x, pose.y, pose.z))
            val yawed = HitTestMath.rotateBox(turned, ORIGIN, yawRotation)
            val scaled = HitTestMath.scaleBox(yawed, totalScale)
            return HitTestMath.moveBox(scaled, state.position)
        }
        // body pose は子ボーン（頭・腕・脚）の親であるため、先に適用します。
        val bodyPose = state.poses[ArmorStandPart.BODY] ?: BoneRotation(0.0, 0.0, 0.0)
        val bodyEuler = Triple(bodyPose.x, bodyPose.y, bodyPose.z)
        fun placeWithBody(
            box: HitTestMath.HitBox,
            pivot: SpatialVector,
            part: ArmorStandPart,
        ): HitTestMath.HitBox {
            val own = state.poses[part] ?: BoneRotation(0.0, 0.0, 0.0)
            val bodied = HitTestMath.rotateBox(box, BODY_PIVOT, bodyEuler)
            // body 回転で pivot 自体が移動するため、回転後の pivot を求め直します。
            val pivotOffset = me.awabi2048.kantancommander.model.EntityGroupMath.rotateOffset(
                SpatialVector(pivot.x - BODY_PIVOT.x, pivot.y - BODY_PIVOT.y, pivot.z - BODY_PIVOT.z),
                bodyEuler,
            )
            val movedPivot = SpatialVector(
                BODY_PIVOT.x + pivotOffset.x,
                BODY_PIVOT.y + pivotOffset.y,
                BODY_PIVOT.z + pivotOffset.z,
            )
            val turned = HitTestMath.rotateBox(bodied, movedPivot, Triple(own.x, own.y, own.z))
            val yawed = HitTestMath.rotateBox(turned, ORIGIN, yawRotation)
            val scaled = HitTestMath.scaleBox(yawed, totalScale)
            return HitTestMath.moveBox(scaled, state.position)
        }
        return buildList {
            if (state.basePlate) {
                add(
                    PartBoxes(
                        ArmorStandPart.BASE_PLATE,
                        BASE_PLATE_CUBES.map { place(it, ORIGIN, BoneRotation(0.0, 0.0, 0.0)) },
                    ),
                )
            }
            add(
                PartBoxes(
                    ArmorStandPart.BODY,
                    BODY_CUBES.map { place(it, BODY_PIVOT, bodyPose) },
                ),
            )
            add(
                PartBoxes(
                    ArmorStandPart.HEAD,
                    HEAD_CUBES.map { placeWithBody(it, HEAD_PIVOT, ArmorStandPart.HEAD) },
                ),
            )
            if (state.arms) {
                add(
                    PartBoxes(
                        ArmorStandPart.LEFT_ARM,
                        LEFT_ARM_CUBES.map { placeWithBody(it, LEFT_ARM_PIVOT, ArmorStandPart.LEFT_ARM) },
                    ),
                )
                add(
                    PartBoxes(
                        ArmorStandPart.RIGHT_ARM,
                        RIGHT_ARM_CUBES.map { placeWithBody(it, RIGHT_ARM_PIVOT, ArmorStandPart.RIGHT_ARM) },
                    ),
                )
            }
            add(
                PartBoxes(
                    ArmorStandPart.LEFT_LEG,
                    LEFT_LEG_CUBES.map { placeWithBody(it, LEFT_LEG_PIVOT, ArmorStandPart.LEFT_LEG) },
                ),
            )
            add(
                PartBoxes(
                    ArmorStandPart.RIGHT_LEG,
                    RIGHT_LEG_CUBES.map { placeWithBody(it, RIGHT_LEG_PIVOT, ArmorStandPart.RIGHT_LEG) },
                ),
            )
        }
    }

    private val ORIGIN = SpatialVector(0.0, 0.0, 0.0)

    /** small は 0.5 倍であり、通常 hitbox（1.975）の半分と一致します。 */
    const val SMALL_SCALE = 0.5

    /** model 単位（16 単位＝1 ブロック）をブロック単位へ換算します。 */
    private fun units(value: Double): Double = value / 16.0

    private fun cube(originX: Double, originY: Double, originZ: Double, sizeX: Double, sizeY: Double, sizeZ: Double) =
        HitTestMath.aabbBox(
            SpatialVector(units(originX), units(originY), units(originZ)),
            SpatialVector(units(sizeX), units(sizeY), units(sizeZ)),
        )

    private val BODY_PIVOT = SpatialVector(units(0.0), units(24.0), units(0.0))
    private val HEAD_PIVOT = SpatialVector(units(0.0), units(24.0), units(0.0))
    private val LEFT_ARM_PIVOT = SpatialVector(units(5.0), units(22.0), units(0.0))
    private val RIGHT_ARM_PIVOT = SpatialVector(units(-5.0), units(22.0), units(0.0))
    private val LEFT_LEG_PIVOT = SpatialVector(units(1.9), units(12.0), units(0.0))
    private val RIGHT_LEG_PIVOT = SpatialVector(units(-1.9), units(12.0), units(0.0))

    private val BASE_PLATE_CUBES = listOf(
        cube(-6.0, 0.0, -6.0, 12.0, 1.0, 12.0),
    )
    private val BODY_CUBES = listOf(
        cube(-6.0, 21.0, -1.5, 12.0, 3.0, 3.0),
        cube(-3.0, 14.0, -1.0, 2.0, 7.0, 2.0),
        cube(1.0, 14.0, -1.0, 2.0, 7.0, 2.0),
        cube(-4.0, 12.0, -1.0, 8.0, 2.0, 2.0),
    )
    private val HEAD_CUBES = listOf(
        cube(-1.0, 24.0, -1.0, 2.0, 7.0, 2.0),
    )
    private val LEFT_ARM_CUBES = listOf(
        cube(5.0, 12.0, -1.0, 2.0, 12.0, 2.0),
    )
    private val RIGHT_ARM_CUBES = listOf(
        cube(-7.0, 12.0, -1.0, 2.0, 12.0, 2.0),
    )
    private val LEFT_LEG_CUBES = listOf(
        cube(0.9, 1.0, -1.0, 2.0, 11.0, 2.0),
    )
    private val RIGHT_LEG_CUBES = listOf(
        cube(-2.9, 1.0, -1.0, 2.0, 11.0, 2.0),
    )
}
