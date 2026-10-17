package me.awabi2048.kantancommander.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.logging.Logger

/**
 * issue #16 の asset lifecycle を固定します。
 *
 * 未参照の建造物ファイルの検出・回収を検証します。
 * Bukkit 実体を使わず、参照名集合だけで検証します。
 */
class AssetLifecycleTest {
    @TempDir
    lateinit var temp: File

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

        val removed = store.deleteUnreferenced(
            me.awabi2048.kantancommander.structure.StructureScope.Global,
            setOf("used_house"),
        )

        assertEquals(listOf("old_house"), removed)
        assertTrue(File(directory, "used_house.nbt").isFile)
        assertTrue(!File(directory, "old_house.nbt").isFile)
        assertTrue(File(directory, "README.txt").isFile)
    }
}
