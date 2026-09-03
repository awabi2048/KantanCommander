package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.WorldVariableValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
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
            set(firstWorld, "wave", WorldVariableValue(VariableType.NUMBER, numberValue = 3.0))
            set(secondWorld, "wave", WorldVariableValue(VariableType.NUMBER, numberValue = 8.0))
        }

        val reloaded = WorldVariableStore(directory)
        assertEquals(3.0, reloaded.get(firstWorld, "wave")?.numberValue)
        assertEquals(8.0, reloaded.get(secondWorld, "wave")?.numberValue)
    }

    @Test
    fun `removing a variable is persisted`() {
        val world = UUID.randomUUID()
        val store = WorldVariableStore(directory)
        store.set(world, "open", WorldVariableValue(VariableType.STRING, stringValue = "true"))
        store.remove(world, "open")

        assertNull(WorldVariableStore(directory).get(world, "open"))
    }

    @Test
    fun `deleting a MyWorld removes cached and persisted values`() {
        val world = UUID.randomUUID()
        val store = WorldVariableStore(directory)
        store.set(world, "shared", WorldVariableValue(VariableType.NUMBER, numberValue = 7.0))

        assertTrue(store.deleteWorld(world))
        assertTrue(store.list(world).isEmpty())
        assertTrue(WorldVariableStore(directory).list(world).isEmpty())
    }

    @Test
    fun `non finite and incomplete values are rejected before cache mutation`() {
        val world = UUID.randomUUID()
        val store = WorldVariableStore(directory)

        assertThrows(IllegalArgumentException::class.java) {
            store.set(world, "broken", WorldVariableValue(VariableType.NUMBER, numberValue = Double.NaN))
        }
        assertNull(store.get(world, "broken"))
    }

    @Test
    fun `type-only definitions use storage empty values without accepting a user initial value`() {
        val world = UUID.randomUUID()
        val store = WorldVariableStore(directory)

        assertTrue(store.define(world, "counter", VariableType.NUMBER))
        assertEquals(VariableType.NUMBER, store.definitions(world)["counter"]?.type)
        assertEquals(0.0, store.get(world, "counter")?.numberValue)

        assertTrue(store.define(world, "label", VariableType.STRING))
        assertEquals(VariableType.STRING, store.definitions(world)["label"]?.type)
        assertEquals("", store.get(world, "label")?.stringValue)
        assertTrue(!store.define(world, "counter", VariableType.STRING))
    }

    @Test
    fun `system variable names cannot be defined or edited as world variables`() {
        val world = UUID.randomUUID()
        val store = WorldVariableStore(directory)
        val value = WorldVariableValue(VariableType.NUMBER, numberValue = 1.0)

        assertThrows(IllegalArgumentException::class.java) {
            store.define(world, "CURRENT_LOOP_COUNT", value)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.set(world, "current_loop_count", value)
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.remove(world, "CURRENT_LOOP_COUNT")
        }
    }

    @Test
    fun `legacy values are converted and unsupported types are dropped`() {
        val world = UUID.randomUUID()
        directory.resolve("$world.json").writeText(
            """
            {
              "definitions": {
                "integer_value": {"type":"INTEGER","integerValue":3},
                "text_value": {"type":"TEXT","textValue":"hello"},
                "CURRENT_LOOP_COUNT": {"type":"NUMBER","numberValue":99},
                "old_flag": {"type":"BOOLEAN","booleanValue":true}
              },
              "values": {
                "integer_value": {"type":"INTEGER","integerValue":4},
                "text_value": {"type":"TEXT","textValue":"world"},
                "current_loop_count": {"type":"NUMBER","numberValue":99},
                "old_flag": {"type":"BOOLEAN","booleanValue":false}
              }
            }
            """.trimIndent(),
        )

        val store = WorldVariableStore(directory)
        assertEquals(4.0, store.get(world, "integer_value")?.numberValue)
        assertEquals("world", store.get(world, "text_value")?.stringValue)
        assertNull(store.get(world, "old_flag"))
        assertTrue(store.definitions(world).keys.none { it.equals("current_loop_count", ignoreCase = true) })
        assertEquals(VariableType.NUMBER, store.definitions(world)["integer_value"]?.type)
        assertEquals(VariableType.STRING, store.definitions(world)["text_value"]?.type)
    }
}
