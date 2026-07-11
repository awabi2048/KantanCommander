package me.awabi2048.kantancommander.data

import java.nio.file.Files
import java.util.UUID
import java.util.logging.Logger
import me.awabi2048.kantancommander.model.DiskScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScriptStoreTest {
    @Test
    fun invalidCommandIsQuarantinedInsteadOfDropped() {
        val root = Files.createTempDirectory("kantan-script-store").toFile()
        val id = UUID.randomUUID()
        val file = root.resolve("$id.json")
        file.writeText("""
            {"id":"$id","name":"broken","owner":"${UUID.randomUUID()}","createdAt":1,
             "trigger":"REDSTONE_RISING","commands":[{"type":"NOT_A_COMMAND","params":{}}]}
        """.trimIndent())

        val store = ScriptStore(root, Logger.getLogger("ScriptStoreTest"))

        assertEquals(emptyList<DiskScript>(), store.listAll())
        assertTrue(root.resolve("corrupt").listFiles()?.isNotEmpty() == true)
        assertTrue(!file.exists())
    }
}
