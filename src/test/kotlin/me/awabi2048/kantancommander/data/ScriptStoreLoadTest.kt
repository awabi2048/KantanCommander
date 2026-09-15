package me.awabi2048.kantancommander.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.logging.Logger

/**
 * issue #16 の再現負荷試験です。
 *
 * 300 program 規模で初期化・一覧・読込・保存を計測し、病的な増加が
 * ないことを固定します。測定値は標準出力へ残し、CIでは寛容な上限だけを
 * 検証します。metadata index・SQLite の要否判断の根拠にします。
 */
class ScriptStoreLoadTest {
    @TempDir
    lateinit var temp: File

    private fun measure(label: String, block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        val elapsed = (System.nanoTime() - start) / 1_000_000L
        println("[ScriptStoreLoad] $label: ${elapsed}ms")
        return elapsed
    }

    @Test
    fun `three hundred programs stay within operational bounds`() {
        val directory = File(temp, "load")
        val setup = ScriptStore(directory, Logger.getAnonymousLogger())
        val player = UUID.randomUUID()
        val ids = (1..PROGRAM_COUNT).map { index ->
            val script = setup.create(player, "load-$index")
            setup.recordHistory(player, script.id)
            script.id
        }
        assertEquals(PROGRAM_COUNT, ids.size)

        var reopened: ScriptStore? = null
        val initMillis = measure("init($PROGRAM_COUNT)") {
            reopened = ScriptStore(directory, Logger.getAnonymousLogger())
        }
        val store = reopened!!

        var listed = 0
        val listAllMillis = measure("listAll($PROGRAM_COUNT)") {
            listed = store.listAll().size
        }
        assertEquals(PROGRAM_COUNT, listed)

        var library = 0
        val libraryMillis = measure("listLibrary($PROGRAM_COUNT)") {
            library = store.listLibrary(player).size
        }
        assertEquals(PROGRAM_COUNT, library)

        var history = 0
        val historyMillis = measure("listHistory(capped)") {
            history = store.listHistory(player).size
        }
        assertEquals(StorageLifecycle.MAX_HISTORY_PER_PLAYER, history)

        val target = ids.last()
        val loadMillis = measure("load(single)") {
            assertTrue(store.load(target) != null)
        }

        val saveMillis = measure("save(single)") {
            val script = store.load(target)!!
            store.update(script.id, player, script.revision) { it.name = "load-renamed" }
        }

        // CI のばらつきを吸収する寛容な上限であり、桁違いの劣化だけを検出します。
        assertTrue(initMillis < 30_000L, "init: $initMillis")
        assertTrue(listAllMillis < 30_000L, "listAll: $listAllMillis")
        assertTrue(libraryMillis < 30_000L, "listLibrary: $libraryMillis")
        assertTrue(historyMillis < 10_000L, "listHistory: $historyMillis")
        assertTrue(loadMillis < 5_000L, "load: $loadMillis")
        assertTrue(saveMillis < 5_000L, "save: $saveMillis")
    }

    private companion object {
        const val PROGRAM_COUNT = 300
    }
}
