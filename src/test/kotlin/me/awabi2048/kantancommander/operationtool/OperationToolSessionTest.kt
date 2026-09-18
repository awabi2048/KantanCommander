package me.awabi2048.kantancommander.operationtool

import me.awabi2048.kantancommander.model.SpatialVector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class OperationToolSessionTest {
    private fun point(world: String, x: Double, y: Double, z: Double) =
        RegionPoint(world, SpatialVector(x, y, z))

    @Test
    fun `mode cycles in order and clears selections`() {
        val session = OperationToolSession()
        assertEquals(OperationToolMode.AREA, session.mode)
        session.setPos1(point("world", 0.0, 0.0, 0.0))
        session.addEntities(listOf(UUID.randomUUID()))
        assertEquals(OperationToolMode.ENTITY_RANGE, session.setMode(OperationToolMode.ENTITY_RANGE))
        assertTrue(session.selected().isEmpty())
        assertEquals(0, session.undoDepth())
        assertFalse(session.hasValidArea())
        assertEquals(OperationToolMode.ENTITY_DIRECT, session.mode.next())
        assertEquals(OperationToolMode.AREA, OperationToolMode.ENTITY_DIRECT.next())
    }

    @Test
    fun `entity selection is additive without duplicates`() {
        val session = OperationToolSession()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        assertEquals(setOf(first, second), session.addEntities(listOf(first, second)))
        assertEquals(emptySet<UUID>(), session.addEntities(listOf(first, second)))
        assertEquals(setOf(first, second), session.selected())
        assertEquals(1, session.undoDepth())
    }

    @Test
    fun `undo removes only the last operation diff`() {
        val session = OperationToolSession()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val third = UUID.randomUUID()
        session.addEntities(listOf(first, second))
        session.addEntities(listOf(second, third))
        assertEquals(setOf(third), session.undoLast())
        assertEquals(setOf(first, second), session.selected())
        assertEquals(setOf(first, second), session.undoLast())
        assertTrue(session.selected().isEmpty())
        assertTrue(session.undoLast().isEmpty())
    }

    @Test
    fun `selection respects the maximum count`() {
        val session = OperationToolSession(maxEntities = 2)
        val added = session.addEntities(listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
        assertEquals(2, added.size)
        assertEquals(2, session.selected().size)
    }

    @Test
    fun `area requires both corners in the same world`() {
        val session = OperationToolSession()
        session.setPos1(point("world", 1.0, 2.0, 3.0))
        assertFalse(session.hasValidArea())
        session.setPos2(point("other", 4.0, 5.0, 6.0))
        assertFalse(session.hasValidArea())
        session.setPos2(point("world", 4.0, 5.0, 6.0))
        assertTrue(session.hasValidArea())
        val bounds = session.areaBounds()
        assertEquals(3.0, bounds?.dx)
        assertEquals(3.0, bounds?.dy)
        assertEquals(3.0, bounds?.dz)
    }

    @Test
    fun `box edge limit cancels oversized boxes`() {
        val minimum = SpatialVector(0.0, 0.0, 0.0)
        assertTrue(OperationToolSession.isBoxEdgeWithinLimit(minimum, SpatialVector(128.0, 0.0, 0.0), 128.0))
        assertFalse(OperationToolSession.isBoxEdgeWithinLimit(minimum, SpatialVector(128.1, 0.0, 0.0), 128.0))
    }

    @Test
    fun `retainExisting drops vanished entities from selection and history`() {
        val session = OperationToolSession()
        val alive = UUID.randomUUID()
        val vanished = UUID.randomUUID()
        session.addEntities(listOf(alive, vanished))
        session.retainExisting { it == alive }
        assertEquals(setOf(alive), session.selected())
        assertEquals(setOf(alive), session.undoLast())
        assertTrue(session.selected().isEmpty())
    }

    @Test
    fun `editing box tracks bounds and cancels without losing selections`() {
        val session = OperationToolSession()
        val kept = UUID.randomUUID()
        session.addEntities(listOf(kept))
        session.startBoxEdit(EditingBox("world", SpatialVector(0.0, 0.0, 0.0), SpatialVector(1.0, 1.0, 1.0)))
        session.updateBoxEdit(SpatialVector(2.0, 3.0, 4.0))
        val (minimum, maximum) = session.editingBox?.bounds() ?: error("editing box expected")
        assertEquals(SpatialVector(0.0, 0.0, 0.0), minimum)
        assertEquals(SpatialVector(2.0, 3.0, 4.0), maximum)
        session.cancelBoxEdit()
        assertEquals(null, session.editingBox)
        assertEquals(setOf(kept), session.selected())
    }
}
