package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.SpatialVector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.logging.Logger

/**
 * issue #16 の asset lifecycle を固定します。
 *
 * 未参照の Entity Group・建造物ファイルの検出・回収を検証します。
 * Bukkit 実体を使わず、参照名集合だけで検証します。
 */
class AssetLifecycleTest {
    @TempDir
    lateinit var temp: File

    private fun group(name: String): EntityGroupStore.GroupRecord {
        val now = System.currentTimeMillis()
        return EntityGroupStore.GroupRecord(
            id = UUID.randomUUID(),
            name = name,
            worldName = "world",
            members = emptyList(),
            pivot = SpatialVector(0.0, 64.0, 0.0),
            orientation = Triple(0.0, 0.0, 0.0),
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `unreferenced groups are collected`() {
        val store = EntityGroupStore(File(temp, "groups.json"), Logger.getAnonymousLogger())
        assertTrue(store.create(group("used_group")))
        assertTrue(store.create(group("old_group")))

        val removed = store.deleteUnreferenced(setOf("used_group"))

        assertEquals(listOf("old_group"), removed)
        assertTrue(store.exists("used_group"))
        assertTrue(!store.exists("old_group"))
        assertTrue(store.deleteUnreferenced(setOf("used_group")).isEmpty())
    }

    @Test
    fun `unreferenced structure files are collected`() {
        val directory = File(temp, "structures").also { it.mkdirs() }
        File(directory, "used_house.nbt").writeBytes(byteArrayOf(1, 2, 3))
        File(directory, "old_house.nbt").writeBytes(byteArrayOf(4, 5, 6))
        // 命名規則外のファイルは一覧対象外であり、回収対象にもなりません。
        File(directory, "README.txt").writeText("keep")
        val store = me.awabi2048.kantancommander.structure.StructureStore(
            Logger.getAnonymousLogger(),
            directory,
        )

        val removed = store.deleteUnreferenced(setOf("used_house"))

        assertEquals(listOf("old_house"), removed)
        assertTrue(File(directory, "used_house.nbt").isFile)
        assertTrue(!File(directory, "old_house.nbt").isFile)
        assertTrue(File(directory, "README.txt").isFile)
    }
}
