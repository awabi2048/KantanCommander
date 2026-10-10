package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphValidator
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ValueType
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * DSL テキストとスクリプトモデルの入出力境界（DslIO）の検証です。
 * import は `dsl` 欄への出所記録、export はグラフからの正規形復元、
 * ファイル入出力の往復を確認します。
 */
class DslIOTest {
    private val io = DslIO(worldTypes = { mapOf("score" to ValueType.NUMBER) })

    private fun assertValid(script: DiskScript) {
        assertEquals(emptyList<String>(), GraphValidator.validate(script.graph), "構造検証")
        val errors = ExecutableScriptValidator.validate(script)
        assertEquals(emptyList<String>(), errors.map { "${it.code}:${it.message}" }, "実行検証")
    }

    @Test
    fun `import が名前・グラフ・出所テキストを持つスクリプトを返す`() {
        val text = """
            program "hello" ;
                var x: number = 1;
                wait(1);
            
        """.trimIndent()
        val script = io.importScript(text, UUID.randomUUID())
        assertEquals("hello", script.name)
        assertEquals(text, script.dsl)
        assertValid(script)
    }

    @Test
    fun `export が正規形テキストを返し往復が不動点になる`() {
        val text = """
            program "round" ;
                var x: number = 1;
                if (x > 0) {
                    x += 2;
                }
                wait(1);
            
        """.trimIndent()
        val script = io.importScript(text, UUID.randomUUID())
        val text1 = io.exportScript(script)
        val script2 = io.importScript(text1, script.owner)
        assertValid(script2)
        assertEquals(text1, io.exportScript(script2))
    }

    @Test
    fun `refreshDsl が dsl 欄を正規形へ更新する`() {
        val text = """
            program "r" ;
                var x: number = 1; // コメントは正規形で消えます
                wait(1);
            
        """.trimIndent()
        val script = io.importScript(text, UUID.randomUUID())
        val refreshed = io.refreshDsl(script)
        assertTrue(refreshed.dsl!!.contains("program \"r\""))
        assertTrue(refreshed.dsl!!.contains("var x: number = 1;"))
        // 元テキスト（コメント入り）は正規形へ置き換わります。
        assertTrue(!refreshed.dsl!!.contains("//"))
    }

    @Test
    fun `表現できないグラフの refreshDsl は null へ正規化する`() {
        val base = io.importScript(
            """
            program "f" ;
                wait(1);
            
            """.trimIndent(),
            UUID.randomUUID(),
        )
        // 折り畳みノードは DSL で表現できないため、dsl は null へ正規化されます。
        val folded = me.awabi2048.kantancommander.model.CommandType.FOLDED_IF.newNode()
        val entry = base.graph.entryNodeId!!
        base.graph.nodes.remove(entry)
        base.graph.nodes[folded.id] = folded
        base.graph.entryNodeId = folded.id
        assertNull(io.refreshDsl(base).dsl)
    }

    @Test
    fun `ファイルの入出力が往復する`(@TempDir dir: File) {
        val text = """
            program "file_io" ;
            import world;
            world.score = 3;
            
        """.trimIndent()
        val script = io.importScript(text, UUID.randomUUID())
        val file = dir.resolve("file_io.kc")
        io.exportFile(script, file)
        assertTrue(file.exists())
        val reimported = io.importFile(file, UUID.randomUUID())
        assertEquals("file_io", reimported.name)
        assertValid(reimported)
    }

    @Test
    fun `import の構文エラーは例外として現れる`() {
        assertThrows<DslParseException> {
            io.importScript("var x: number = 1;", UUID.randomUUID())
        }
    }
}
