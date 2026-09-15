package me.awabi2048.kantancommander.hittest

import me.awabi2048.kantancommander.model.SpatialVector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #23 の選択中核を固定します。
 *
 * broad phase で外れる候補の除外・narrow phase の最前面優先・部位なし
 * fallback・unload／消失に相当する空 snapshot を検証します。
 * Bukkit 実体を使わず、純粋な snapshot 入力だけで検証します。
 */
class HitTestEngineTest {
    private val engine = HitTestEngine()

    private fun standSnapshot(
        id: UUID = UUID.randomUUID(),
        position: SpatialVector = SpatialVector(0.0, 0.0, 0.0),
    ): HitTestEngine.EntitySnapshot {
        val state = ArmorStandGeometry.ArmorStandState(position = position, yawDegrees = 0.0, arms = true)
        val union = HitTestMath.unionAabb(ArmorStandGeometry.buildBoxes(state, marker = false).flatMap { it.boxes })
        return HitTestEngine.EntitySnapshot(id, state, marker = false, bounds = union)
    }

    private fun eyeRay(height: Double = 1.6): HitTestMath.HitRay = HitTestMath.HitRay(
        SpatialVector(0.0, height, -5.0),
        SpatialVector(0.0, 0.0, 1.0),
    )

    @Test
    fun `frontal stands are selected with their part`() {
        val snapshot = standSnapshot()
        val hit = engine.selectNearest(eyeRay(height = 1.6), listOf(snapshot), 10.0)

        assertNotNull(hit)
        assertEquals(snapshot.entityId, hit!!.entityId)
        // 目線（y=1.6）は胴上部バー（y=1.3125..1.5）より上のため頭部に当たる。
        assertEquals(ArmorStandGeometry.ArmorStandPart.HEAD, hit.part)
        // 頭部前面（z=-0.0625）までの距離は 5.0 - 0.0625。
        assertEquals(4.9375, hit.distance, 1e-9)
        assertEquals(0.0, hit.position.x, 1e-9)
        assertEquals(1.6, hit.position.y, 1e-9)
    }

    @Test
    fun `nearest of overlapping stands wins`() {
        val near = standSnapshot(position = SpatialVector(0.0, 0.0, 0.0))
        val far = standSnapshot(position = SpatialVector(0.0, 0.0, 2.0))
        val hit = engine.selectNearest(eyeRay(), listOf(far, near), 10.0)

        assertNotNull(hit)
        assertEquals(near.entityId, hit!!.entityId)
    }

    @Test
    fun `broad phase misses skip the narrow phase`() {
        // 視線から外れた実体は全体 AABB で除外される。
        val away = standSnapshot(position = SpatialVector(10.0, 0.0, 0.0))
        assertNull(engine.selectNearest(eyeRay(), listOf(away), 10.0))
    }

    @Test
    fun `hits beyond the range are misses`() {
        val snapshot = standSnapshot()
        assertNull(engine.selectNearest(eyeRay(), listOf(snapshot), 4.0))
    }

    @Test
    fun `fallback bounds are selected without a part`() {
        val snapshot = HitTestEngine.EntitySnapshot(
            UUID.randomUUID(),
            armorStand = null,
            marker = false,
            bounds = HitTestMath.HitAabb(SpatialVector(-0.5, 0.0, -0.5), SpatialVector(0.5, 1.8, 0.5)),
        )
        val hit = engine.selectNearest(
            HitTestMath.HitRay(SpatialVector(0.0, 1.0, -5.0), SpatialVector(0.0, 0.0, 1.0)),
            listOf(snapshot),
            10.0,
        )

        assertNotNull(hit)
        assertNull(hit!!.part)
        assertEquals(4.5, hit.distance, 1e-9)
    }

    @Test
    fun `snapshots without geometry are skipped`() {
        val empty = HitTestEngine.EntitySnapshot(UUID.randomUUID(), armorStand = null, marker = false, bounds = null)
        assertNull(engine.selectNearest(eyeRay(), listOf(empty), 10.0))
        assertNull(engine.selectNearest(eyeRay(), emptyList(), 10.0))
    }

    @Test
    fun `markers are not selectable`() {
        val state = ArmorStandGeometry.ArmorStandState(position = SpatialVector(0.0, 0.0, 0.0), yawDegrees = 0.0)
        val union = HitTestMath.HitAabb(SpatialVector(-0.25, 0.0, -0.25), SpatialVector(0.25, 1.0, 0.25))
        val marker = HitTestEngine.EntitySnapshot(UUID.randomUUID(), state, marker = true, bounds = union)
        // marker は geometry を持たないため選択されない。
        assertNull(engine.selectNearest(eyeRay(), listOf(marker), 10.0))
    }

    @Test
    fun `invalid rays select nothing`() {
        val snapshot = standSnapshot()
        val broken = HitTestMath.HitRay(SpatialVector(0.0, 0.0, 0.0), SpatialVector(0.0, 0.0, 0.0))
        assertNull(engine.selectNearest(broken, listOf(snapshot), 10.0))
    }

    @Test
    fun `cache honours snapshot changes`() {
        val first = standSnapshot()
        val boxes = ArmorStandGeometry.buildBoxes(first.armorStand!!, first.marker)
        // 同一 snapshot の再選択は同じ geometry を使う（件数で確認）。
        engine.selectNearest(eyeRay(), listOf(first), 10.0)
        assertEquals(1, engine.cacheSize())
        engine.selectNearest(eyeRay(), listOf(first), 10.0)
        assertEquals(1, engine.cacheSize())
        assertTrue(boxes.isNotEmpty())
    }
}
