package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.WorldVariableValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

class WorldVariableStoreTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `variables are isolated by MyWorld and survive store reload`() {
        val firstWorld = UUID.randomUUID()
        val secondWorld = UUID.randomUUID()
        WorldVariableStore(directory).apply {
            set(firstWorld, "wave", WorldVariableValue(VariableType.INTEGER, integerValue = 3))
            set(secondWorld, "wave", WorldVariableValue(VariableType.INTEGER, integerValue = 8))
        }

        val reloaded = WorldVariableStore(directory)
        assertEquals(3, reloaded.get(firstWorld, "wave")?.integerValue)
        assertEquals(8, reloaded.get(secondWorld, "wave")?.integerValue)
    }

    @Test
    fun `removing a variable is persisted`() {
        val world = UUID.randomUUID()
        val store = WorldVariableStore(directory)
        store.set(world, "open", WorldVariableValue(VariableType.BOOLEAN, booleanValue = true))
        store.remove(world, "open")

        assertNull(WorldVariableStore(directory).get(world, "open"))
    }

    @Test
    fun `deleting a MyWorld removes cached and persisted values`() {
        val world = UUID.randomUUID()
        val store = WorldVariableStore(directory)
        store.set(world, "shared", WorldVariableValue(VariableType.INTEGER, integerValue = 7))

        assertTrue(store.deleteWorld(world))
        assertTrue(store.list(world).isEmpty())
        assertTrue(WorldVariableStore(directory).list(world).isEmpty())
    }
}
