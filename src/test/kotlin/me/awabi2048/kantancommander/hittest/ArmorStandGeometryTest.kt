package me.awabi2048.kantancommander.hittest

import me.awabi2048.kantancommander.model.SpatialVector
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #23 の ArmorStand geometry を固定します。
 *
 * 既定 pose の部位寸法は Mojang 公開 geometry の golden 値と照合し、
 * yaw・small・pose・表示 flag の反映を検証します。Bukkit 実体を使わず、
 * 純粋な状態入力だけで検証します。
 */
class ArmorStandGeometryTest {
    private fun defaultState() = ArmorStandGeometry.ArmorStandState(
        position = SpatialVector(10.0, 64.0, -3.0),
        yawDegrees = 0.0,
        arms = true,
    )

    private fun part(
        boxes: List<ArmorStandGeometry.PartBoxes>,
        part: ArmorStandGeometry.ArmorStandPart,
    ): ArmorStandGeometry.PartBoxes? = boxes.firstOrNull { it.part == part }

    @Test
    fun `default pose matches the golden head dimensions`() {
        val boxes = ArmorStandGeometry.buildBoxes(defaultState(), marker = false)
        val head = part(boxes, ArmorStandGeometry.ArmorStandPart.HEAD)

        assertNotNull(head)
        assertEquals(1, head!!.boxes.size)
        val box = head.boxes.single()
        // 頭部 cube: origin [-1, 24, -1] size [2, 7, 2]（単位は16分割）。
        // 中心は (0, 24 + 3.5, 0) 単位 = (0, 1.71875, 0) ブロック。
        assertEquals(10.0, box.center.x, 1e-9)
        assertEquals(64.0 + 1.71875, box.center.y, 1e-9)
        assertEquals(-3.0, box.center.z, 1e-9)
        assertEquals(0.0625, box.halfExtents.x, 1e-9)
        assertEquals(0.21875, box.halfExtents.y, 1e-9)
        assertEquals(0.0625, box.halfExtents.z, 1e-9)
    }

    @Test
    fun `default pose spans the vanilla height`() {
        val boxes = ArmorStandGeometry.buildBoxes(defaultState(), marker = false)
        val union = HitTestMath.unionAabb(boxes.flatMap { it.boxes })

        assertNotNull(union)
        // 頭頂は 31/16 = 1.9375 であり、通常 hitbox（1.975）の内側です。
        assertEquals(64.0, union!!.minimum.y, 1e-9)
        assertEquals(64.0 + 1.9375, union.maximum.y, 1e-9)
    }

    @Test
    fun `body holds four cubes and arms hold one each`() {
        val boxes = ArmorStandGeometry.buildBoxes(defaultState(), marker = false)

        assertEquals(4, part(boxes, ArmorStandGeometry.ArmorStandPart.BODY)!!.boxes.size)
        assertEquals(1, part(boxes, ArmorStandGeometry.ArmorStandPart.LEFT_ARM)!!.boxes.size)
        assertEquals(1, part(boxes, ArmorStandGeometry.ArmorStandPart.RIGHT_ARM)!!.boxes.size)
        assertEquals(1, part(boxes, ArmorStandGeometry.ArmorStandPart.LEFT_LEG)!!.boxes.size)
        assertEquals(1, part(boxes, ArmorStandGeometry.ArmorStandPart.RIGHT_LEG)!!.boxes.size)
        assertEquals(1, part(boxes, ArmorStandGeometry.ArmorStandPart.BASE_PLATE)!!.boxes.size)
    }

    @Test
    fun `hidden arms and base plate are excluded`() {
        val boxes = ArmorStandGeometry.buildBoxes(defaultState().copy(arms = false, basePlate = false), marker = false)

        assertNull(part(boxes, ArmorStandGeometry.ArmorStandPart.LEFT_ARM))
        assertNull(part(boxes, ArmorStandGeometry.ArmorStandPart.RIGHT_ARM))
        assertNull(part(boxes, ArmorStandGeometry.ArmorStandPart.BASE_PLATE))
        assertNotNull(part(boxes, ArmorStandGeometry.ArmorStandPart.HEAD))
    }

    @Test
    fun `markers are not selectable`() {
        assertTrue(ArmorStandGeometry.buildBoxes(defaultState(), marker = true).isEmpty())
    }

    @Test
    fun `small halves every dimension`() {
        val normal = ArmorStandGeometry.buildBoxes(defaultState(), marker = false)
        val small = ArmorStandGeometry.buildBoxes(defaultState().copy(small = true), marker = false)
        val normalHead = part(normal, ArmorStandGeometry.ArmorStandPart.HEAD)!!.boxes.single()
        val smallHead = part(small, ArmorStandGeometry.ArmorStandPart.HEAD)!!.boxes.single()

        assertEquals(normalHead.halfExtents.x / 2.0, smallHead.halfExtents.x, 1e-9)
        assertEquals(normalHead.halfExtents.y / 2.0, smallHead.halfExtents.y, 1e-9)
        assertEquals(64.0 + 1.9375 / 2.0, HitTestMath.unionAabb(small.flatMap { it.boxes })!!.maximum.y, 1e-9)
    }

    @Test
    fun `scale attribute multiplies the whole model`() {
        val boxes = ArmorStandGeometry.buildBoxes(defaultState().copy(scale = 2.0), marker = false)
        val union = HitTestMath.unionAabb(boxes.flatMap { it.boxes })

        assertNotNull(union)
        assertEquals(64.0 + 1.9375 * 2.0, union!!.maximum.y, 1e-9)
        assertEquals(0.0625 * 2.0, part(boxes, ArmorStandGeometry.ArmorStandPart.HEAD)!!.boxes.single().halfExtents.x, 1e-9)
    }

    @Test
    fun `yaw rotation matches the bukkit facing convention`() {
        // yaw 90 度の実体の左腕は、yaw 0 度の実体を -90 度回転した位置と一致する。
        // Bukkit の向き（Location.getDirection）と同一規則であることを固定します。
        val facing = Location(null, 0.0, 0.0, 0.0, 90f, 0f).direction
        assertEquals(-1.0, facing.x, 1e-6)
        assertEquals(0.0, facing.y, 1e-6)
        assertEquals(0.0, facing.z, 1e-6)

        val straight = part(
            ArmorStandGeometry.buildBoxes(defaultState(), marker = false),
            ArmorStandGeometry.ArmorStandPart.LEFT_ARM,
        )!!.boxes.single()
        val turned = part(
            ArmorStandGeometry.buildBoxes(defaultState().copy(yawDegrees = 90.0), marker = false),
            ArmorStandGeometry.ArmorStandPart.LEFT_ARM,
        )!!.boxes.single()
        // 左腕（+X側）は yaw 90 度で +Z 側へ移動する（facing -X と整合）。
        assertEquals(0.375, straight.center.x - 10.0, 1e-9)
        assertEquals(0.375, turned.center.z - -3.0, 1e-6)
        assertEquals(0.0, turned.center.x - 10.0, 1e-6)
    }

    @Test
    fun `raised arms move above their pivot`() {
        // 左腕を真上へ180度回転する。腕は pivot から下方へ伸びているため、
        // 反転後は上方を向き、通常 hitbox（1.975）を超え得る。
        val state = defaultState().copy(
            poses = mapOf(
                ArmorStandGeometry.ArmorStandPart.LEFT_ARM to ArmorStandGeometry.BoneRotation(180.0, 0.0, 0.0),
            ),
        )
        val arm = part(ArmorStandGeometry.buildBoxes(state, marker = false), ArmorStandGeometry.ArmorStandPart.LEFT_ARM)!!
            .boxes.single()
        // pivot（y=1.375）より上方へ移動し、通常 hitbox（1.975）を超え得る。
        assertTrue(arm.center.y - 64.0 > 1.375)
        // 他部位は動かない。
        val head = part(ArmorStandGeometry.buildBoxes(state, marker = false), ArmorStandGeometry.ArmorStandPart.HEAD)!!
            .boxes.single()
        assertEquals(64.0 + 1.71875, head.center.y, 1e-9)
    }

    @Test
    fun `head pose turns the head around its own pivot`() {
        val state = defaultState().copy(
            poses = mapOf(
                ArmorStandGeometry.ArmorStandPart.HEAD to ArmorStandGeometry.BoneRotation(0.0, 90.0, 0.0),
            ),
        )
        val head = part(ArmorStandGeometry.buildBoxes(state, marker = false), ArmorStandGeometry.ArmorStandPart.HEAD)!!
            .boxes.single()
        // 正方形断面の頭部は Y 回転で中心が動かない。
        assertEquals(10.0, head.center.x, 1e-9)
        assertEquals(-3.0, head.center.z, 1e-9)
    }

    @Test
    fun `invalid states build nothing`() {
        assertTrue(
            ArmorStandGeometry.buildBoxes(
                defaultState().copy(position = SpatialVector(Double.NaN, 64.0, 0.0)),
                marker = false,
            ).isEmpty(),
        )
        assertTrue(
            ArmorStandGeometry.buildBoxes(defaultState().copy(scale = 0.0), marker = false).isEmpty(),
        )
    }
}
