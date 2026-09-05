package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandValueRules
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

    @Test
    fun `definition and current value mismatches are discarded as one invalid pair`() {
        val world = UUID.randomUUID()
        directory.resolve("$world.json").writeText(
            """
            {
              "definitions": {
                "definition_only": {"type":"NUMBER","numberValue":1},
                "type_mismatch": {"type":"NUMBER","numberValue":2},
                "valid": {"type":"STRING","stringValue":"definition"}
              },
              "values": {
                "value_only": {"type":"STRING","stringValue":"orphan"},
                "type_mismatch": {"type":"STRING","stringValue":"wrong"},
                "valid": {"type":"STRING","stringValue":"current"}
              }
            }
            """.trimIndent(),
        )

        val store = WorldVariableStore(directory)

        assertEquals(setOf("valid"), store.list(world).keys)
        assertEquals(setOf("valid"), store.definitions(world).keys)
        assertEquals("current", store.get(world, "valid")?.stringValue)
        assertTrue(store.get(world, "definition_only") == null)
        assertTrue(store.get(world, "value_only") == null)
        assertTrue(store.get(world, "type_mismatch") == null)

        // 読み込み時の正規化結果は次のStoreでも同じ組として再利用できる形で
        // 保存され、起動のたびに不整合を再生成しません。
        val reloaded = WorldVariableStore(directory)
        assertEquals(setOf("valid"), reloaded.list(world).keys)
        assertEquals(setOf("valid"), reloaded.definitions(world).keys)
    }

    @Test
    fun `string values over the shared length limit are rejected before cache mutation`() {
        val world = UUID.randomUUID()
        val store = WorldVariableStore(directory)
        val overLimit = "a".repeat(CommandValueRules.WORLD_VARIABLE_STRING_MAX_LENGTH + 1)

        assertThrows(IllegalArgumentException::class.java) {
            store.set(world, "long", WorldVariableValue(VariableType.STRING, stringValue = overLimit))
        }
        assertThrows(IllegalArgumentException::class.java) {
            store.define(world, "long", WorldVariableValue(VariableType.STRING, stringValue = overLimit))
        }
        assertNull(store.get(world, "long"))

        // 上限ちょうどの値は受理されます。
        val atLimit = "a".repeat(CommandValueRules.WORLD_VARIABLE_STRING_MAX_LENGTH)
        store.set(world, "at_limit", WorldVariableValue(VariableType.STRING, stringValue = atLimit))
        assertEquals(atLimit, store.get(world, "at_limit")?.stringValue)
    }

    @Test
    fun `over length string data is discarded while loading`() {
        val world = UUID.randomUUID()
        val overLimit = "b".repeat(CommandValueRules.WORLD_VARIABLE_STRING_MAX_LENGTH + 1)
        val atLimit = "b".repeat(CommandValueRules.WORLD_VARIABLE_STRING_MAX_LENGTH)
        directory.resolve("$world.json").writeText(
            """
            {
              "definitions": {
                "too_long": {"type":"STRING","stringValue":"$overLimit"},
                "at_limit": {"type":"STRING","stringValue":"$atLimit"}
              },
              "values": {
                "too_long": {"type":"STRING","stringValue":"$overLimit"},
                "at_limit": {"type":"STRING","stringValue":"$atLimit"}
              }
            }
            """.trimIndent(),
        )

        val store = WorldVariableStore(directory)

        // 上限超過の保存値は編集Dialogの入力上限を超えるため、正規化で無効ペアとして
        // 破棄します。上限ちょうどの値は保持されます。
        assertNull(store.get(world, "too_long"))
        assertEquals(atLimit, store.get(world, "at_limit")?.stringValue)

        val reloaded = WorldVariableStore(directory)
        assertNull(reloaded.get(world, "too_long"))
        assertEquals(atLimit, reloaded.get(world, "at_limit")?.stringValue)
    }
}
