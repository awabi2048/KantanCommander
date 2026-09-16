package me.awabi2048.kantancommander.placement

import me.awabi2048.kantancommander.model.DiskPlacement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #review-1: 移動写像の一括評価試験です。
 *
 * 重なり移動（A→B・B→C）を逐次処理すると先行項目が後続の移動元登録を
 * 消すため、計画段階で重なりを分類します。Bukkit非依存の純粋判定です。
 */
class WorldEditMovePlannerTest {
    private fun item(key: String, scriptId: UUID, x: Int = 0): RelativePlacement =
        RelativePlacement(key, scriptId, "north", x, 64, 0, sourceWasGlass = true)

    private fun placement(world: String, x: Int, scriptId: UUID): DiskPlacement =
        DiskPlacement(world, x, 64, 0, scriptId, "north", null)

    private fun sourceOf(item: RelativePlacement): Triple<Int, Int, Int> =
        Triple(item.relativeX, 64, 0)

    @Test
    fun `overlapping chain keeps both scripts`() {
        // A→B・B→C の重なり：BセルはB項目の移動元でもあるため、
        // A項目の重なり既存に含めず、B項目の確定処理へ委ねます。
        val scriptA = UUID.randomUUID()
        val scriptB = UUID.randomUUID()
        val itemA = item("w,0,64,0", scriptA, 0)
        val itemB = item("w,1,64,0", scriptB, 1)
        val glass = setOf(Triple(1, 64, 0), Triple(2, 64, 0))
        val ledger = mapOf(
            Triple(1, 64, 0) to placement("w", 1, scriptB),
        )
        val batchKeys = setOf("w,0,64,0", "w,1,64,0")

        val plans = WorldEditMovePlanner.planMoveBatch(
            items = listOf(itemA, itemB),
            sourceOf = ::sourceOf,
            targetOf = { (x, y, z) -> Triple(x + 1, y, z) },
            inHeight = { true },
            isGlass = { it in glass },
            existingAt = { ledger[it] },
            batchSourceKeys = batchKeys,
        )

        val planA = plans.single { it.relative === itemA }
        val planB = plans.single { it.relative === itemB }
        // Aの移動元は消えている想定（glass集合に含めない）のため同スクリプト移動です。
        assertEquals(MoveItemAction.MOVE_SAME, planA.action)
        // Bセルは自バッチの移動元なので、Aの重なり既存にしません。
        assertNull(planA.overlappedExisting)
        assertEquals(Triple(2, 64, 0), planB.target)
        assertEquals(MoveItemAction.COPY_NEW, planB.action)
    }

    @Test
    fun `external overlap is removed only after batch success`() {
        // 自バッチ外の既存登録は計画に保持し、呼び出し側が全登録成功後に消します。
        val scriptA = UUID.randomUUID()
        val scriptOther = UUID.randomUUID()
        val itemA = item("w,0,64,0", scriptA, 0)
        val glass = setOf(Triple(1, 64, 0))
        val ledger = mapOf(Triple(1, 64, 0) to placement("w", 1, scriptOther))

        val plans = WorldEditMovePlanner.planMoveBatch(
            items = listOf(itemA),
            sourceOf = ::sourceOf,
            targetOf = { (x, y, z) -> Triple(x + 1, y, z) },
            inHeight = { true },
            isGlass = { it in glass },
            existingAt = { ledger[it] },
            batchSourceKeys = setOf("w,0,64,0"),
        )

        val plan = plans.single()
        assertEquals(MoveItemAction.MOVE_SAME, plan.action)
        assertEquals(scriptOther, plan.overlappedExisting?.scriptId)
    }

    @Test
    fun `same script target needs no work`() {
        val scriptA = UUID.randomUUID()
        val itemA = item("w,0,64,0", scriptA, 0)
        val glass = setOf(Triple(1, 64, 0))
        val ledger = mapOf(Triple(1, 64, 0) to placement("w", 1, scriptA))

        val plans = WorldEditMovePlanner.planMoveBatch(
            items = listOf(itemA),
            sourceOf = ::sourceOf,
            targetOf = { (x, y, z) -> Triple(x + 1, y, z) },
            inHeight = { true },
            isGlass = { it in glass },
            existingAt = { ledger[it] },
            batchSourceKeys = setOf("w,0,64,0"),
        )

        assertEquals(MoveItemAction.NOOP, plans.single().action)
        assertNull(plans.single().overlappedExisting)
    }

    @Test
    fun `missing target without source glass erases`() {
        val itemA = item("w,0,64,0", UUID.randomUUID(), 0)

        val plans = WorldEditMovePlanner.planMoveBatch(
            items = listOf(itemA),
            sourceOf = ::sourceOf,
            targetOf = { (x, y, z) -> Triple(x + 1, y, z) },
            inHeight = { true },
            isGlass = { false },
            existingAt = { null },
            batchSourceKeys = setOf("w,0,64,0"),
        )

        assertEquals(MoveItemAction.ERASE, plans.single().action)
    }

    @Test
    fun `missing target with remaining source skips`() {
        val itemA = item("w,0,64,0", UUID.randomUUID(), 0)
        val glass = setOf(Triple(0, 64, 0))

        val plans = WorldEditMovePlanner.planMoveBatch(
            items = listOf(itemA),
            sourceOf = ::sourceOf,
            targetOf = { (x, y, z) -> Triple(x + 1, y, z) },
            inHeight = { true },
            isGlass = { it in glass },
            existingAt = { null },
            batchSourceKeys = setOf("w,0,64,0"),
        )

        assertEquals(MoveItemAction.SKIP, plans.single().action)
    }

    @Test
    fun `out of height range skips`() {
        val itemA = item("w,0,64,0", UUID.randomUUID(), 0)
        val glass = setOf(Triple(0, 64, 0), Triple(1, 64, 0))

        val plans = WorldEditMovePlanner.planMoveBatch(
            items = listOf(itemA),
            sourceOf = ::sourceOf,
            targetOf = { (x, y, z) -> Triple(x + 1, y, z) },
            inHeight = { false },
            isGlass = { it in glass },
            existingAt = { null },
            batchSourceKeys = setOf("w,0,64,0"),
        )

        assertEquals(MoveItemAction.SKIP, plans.single().action)
        assertNull(plans.single().overlappedExisting)
    }
}
