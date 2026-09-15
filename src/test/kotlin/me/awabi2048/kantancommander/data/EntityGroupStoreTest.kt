package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.SpatialVector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.logging.Logger

/**
 * issue #24 の群永続化を固定します。
 *
 * 再起動後の復元は「保存→別インスタンスで読込」の往復で検証し、
 * 壊れた保存は退避して空から再開します。unload 中の所属維持は
 * 記録自体を消さないことで担保し、解決可否の判断は実行層が行います。
 */
class EntityGroupStoreTest {
    @TempDir
    lateinit var temp: File

    private fun record(name: String = "my_group-01"): EntityGroupStore.GroupRecord {
        val now = System.currentTimeMillis()
        return EntityGroupStore.GroupRecord(
            id = UUID.randomUUID(),
            name = name,
            worldName = "world",
            members = listOf(
                EntityGroupStore.MemberRecord(UUID.randomUUID(), SpatialVector(1.0, 2.0, 3.0)),
                EntityGroupStore.MemberRecord(UUID.randomUUID(), null),
            ),
            pivot = SpatialVector(0.5, 64.0, 0.5),
            orientation = Triple(0.0, 90.0, 0.0),
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `groups survive a store restart with members pivot and orientation`() {
        val file = File(temp, "entity-groups.json")
        val expected = record()
        assertTrue(EntityGroupStore(file, Logger.getAnonymousLogger()).create(expected))

        val reloaded = EntityGroupStore(file, Logger.getAnonymousLogger()).get("my_group-01")

        assertEquals(expected, reloaded)
        assertEquals(listOf("my_group-01"), EntityGroupStore(file, Logger.getAnonymousLogger()).names())
    }

    @Test
    fun `duplicate creation does not overwrite the existing group`() {
        val file = File(temp, "entity-groups.json")
        val store = EntityGroupStore(file, Logger.getAnonymousLogger())
        val first = record()
        assertTrue(store.create(first))
        assertFalse(store.create(first.copy(id = UUID.randomUUID())))
        assertEquals(first, store.get("my_group-01"))
    }

    @Test
    fun `update and delete rewrite the persisted record`() {
        val file = File(temp, "entity-groups.json")
        val store = EntityGroupStore(file, Logger.getAnonymousLogger())
        val created = record()
        assertTrue(store.create(created))

        val updated = created.copy(pivot = SpatialVector(10.0, 64.0, 10.0))
        assertTrue(store.update(updated))
        assertEquals(updated, EntityGroupStore(file, Logger.getAnonymousLogger()).get("my_group-01"))

        assertTrue(store.delete("my_group-01"))
        assertEquals(null, EntityGroupStore(file, Logger.getAnonymousLogger()).get("my_group-01"))
        assertFalse(store.delete("my_group-01"))
    }

    @Test
    fun `corrupt stores are quarantined and restart empty`() {
        val file = File(temp, "entity-groups.json")
        file.writeText("{not json")
        val store = EntityGroupStore(file, Logger.getAnonymousLogger())

        assertEquals(emptyList<String>(), store.names())
        assertTrue(File(File(temp, "corrupt").path).isDirectory)
    }
}
