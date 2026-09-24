package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.logging.Logger

/**
 * issue #16 の保存 lifecycle を固定します。
 *
 * 履歴上限・解放・orphan 回収・関係の自己修復を検証します。
 * 配置参照の判定は注入し、配置中の正本を残すことを検証します。
 */
class ScriptStoreLifecycleTest {
    @TempDir
    lateinit var temp: File

    private fun directory(name: String): File = File(temp, "$name-${UUID.randomUUID()}")

    private fun createWithHistory(store: ScriptStore, player: UUID, name: String): UUID {
        val script = store.create(player, name)
        store.recordHistory(player, script.id)
        return script.id
    }

    @Test
    fun `history keeps only the newest entries per player`() {
        val store = ScriptStore(directory("history"), Logger.getAnonymousLogger())
        val player = UUID.randomUUID()
        val ids = (1..(StorageLifecycle.MAX_HISTORY_PER_PLAYER + 5)).map { index ->
            createWithHistory(store, player, "program-$index")
        }

        val history = store.listHistory(player).map { it.id }

        assertEquals(StorageLifecycle.MAX_HISTORY_PER_PLAYER, history.size)
        assertEquals(ids.takeLast(StorageLifecycle.MAX_HISTORY_PER_PLAYER).reversed(), history)
    }

    @Test
    fun `evicted history releases orphan files but keeps placed scripts`() {
        val placed = mutableSetOf<UUID>()
        val store = ScriptStore(
            directory("evict"),
            Logger.getAnonymousLogger(),
            isPlacedScript = { placed.contains(it) },
        )
        val player = UUID.randomUUID()
        // 履歴だけに残る記録を作ります。ライブラリ参照は外します。
        val first = createWithHistory(store, player, "first")
        store.removeFromLibrary(player, first)
        placed += first
        val second = createWithHistory(store, player, "second")
        store.removeFromLibrary(player, second)
        repeat(StorageLifecycle.MAX_HISTORY_PER_PLAYER) { index ->
            val filler = createWithHistory(store, player, "filler-$index")
            store.removeFromLibrary(player, filler)
        }

        // 上限超過で古い方から外れます。配置参照の first は残り、second は解放されます。
        assertTrue(store.load(first) != null)
        assertTrue(store.load(second) == null)
    }

    @Test
    fun `prune orphans collects files without relations or placements`() {
        val placed = mutableSetOf<UUID>()
        val store = ScriptStore(
            directory("prune"),
            Logger.getAnonymousLogger(),
            isPlacedScript = { placed.contains(it) },
        )
        val player = UUID.randomUUID()
        val library = store.create(player, "library")
        store.recordHistory(player, library.id)
        val placementOnly = store.createPlacement(player, "placement")
        val orphan = store.createPlacement(player, "orphan")
        placed += placementOnly.id

        // library は履歴に残るため orphan ではありません。
        // placementOnly は配置参照があるため残ります。
        val removed = store.pruneOrphans()

        assertEquals(1, removed)
        assertTrue(store.load(library.id) != null)
        assertTrue(store.load(placementOnly.id) != null)
        assertTrue(store.load(orphan.id) == null)
    }

    @Test
    fun `dangling relations repair themselves on startup`() {
        val dir = directory("dangling")
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val player = UUID.randomUUID()
        val script = store.create(player, "dangling")
        store.recordHistory(player, script.id)
        // 外部改変で正本だけを消します。
        File(dir, "${script.id}.json").delete()

        val reopened = ScriptStore(dir, Logger.getAnonymousLogger())

        assertTrue(reopened.listHistory(player).isEmpty())
        assertTrue(reopened.listLibrary(player).isEmpty())
    }

    @Test
    fun `asset references collect structure and group names from executable operations`() {
        val script = DiskScript(name = "assets", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.STRUCTURE_PLACE).apply { params["structure"] = "my_house-01" }
        GraphEditor.append(script.graph, CommandType.ENTITY_GROUP).apply { params["group"] = "my_group-01" }
        GraphEditor.append(script.graph, CommandType.WAIT)

        assertEquals(setOf("my_house-01"), AssetReferenceScanner.structureNames(listOf(script)))
        assertEquals(setOf("my_group-01"), AssetReferenceScanner.groupNames(listOf(script)))
        assertTrue(AssetReferenceScanner.structureNames(emptyList()).isEmpty())
        assertTrue(AssetReferenceScanner.groupNames(emptyList()).isEmpty())
    }
}
