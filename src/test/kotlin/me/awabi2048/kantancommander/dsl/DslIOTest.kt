package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphValidator
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ValueType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * DSL テキストとスクリプトモデルの入出力境界（DslIO）の検証です。
 * `.kcp` テキストが正本であり、import はモデルへの低級化、export は
 * グラフからの正規形復元、ファイル入出力の往復を確認します。
 */
class DslIOTest {
    private val io = DslIO(worldTypes = { mapOf("score" to ValueType.NUMBER) })

    private fun assertValid(script: DiskScript) {
        assertEquals(emptyList<String>(), GraphValidator.validate(script.graph), "構造検証")
        val errors = ExecutableScriptValidator.validate(script)
        assertEquals(emptyList<String>(), errors.map { "${it.code}:${it.message}" }, "実行検証")
    }

    @Test
    fun `import が名前・グラフを持つスクリプトを返す`() {
        val text = """
            program "hello" ;
                var x: number = 1;
                wait(1);
            
        """.trimIndent()
        val script = io.importScript(text)
        assertEquals("hello", script.name)
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
        val script = io.importScript(text)
        val text1 = io.exportScript(script)
        val script2 = io.importScript(text1)
        assertValid(script2)
        assertEquals(text1, io.exportScript(script2))
    }

    @Test
    fun `export の正規形でもコメントが保持される`() {
        val text = """
            program "r" ;
                var x: number = 1; // コメントは文へ所属して保持されます
                wait(1);
            
        """.trimIndent()
        val script = io.importScript(text)
        val exported = io.exportScript(script)
        assertTrue(exported.contains("program \"r\""))
        assertTrue(exported.contains("var x: number = 1;"))
        // コメントは文へ所属するため、正規形へ置き換わっても保持されます。
        assertTrue(exported.contains("// コメントは文へ所属して保持されます"))
    }

    @Test
    fun `表現できないグラフの export は例外になる`() {
        val base = io.importScript(
            """
            program "f" ;
                wait(1);
            
            """.trimIndent(),
        )
        // 折り畳みノードは DSL で表現できないため、export は失敗します。
        val folded = me.awabi2048.kantancommander.model.CommandType.FOLDED_IF.newNode()
        val entry = base.graph.entryNodeId!!
        base.graph.nodes.remove(entry)
        base.graph.nodes[folded.id] = folded
        base.graph.entryNodeId = folded.id
        assertThrows<DslPrintException> { io.exportScript(base) }
    }

    @Test
    fun `ファイルの入出力が往復する`(@TempDir dir: File) {
        val text = """
            program "file_io" ;
            import world;
            world.score = 3;
            
        """.trimIndent()
        val script = io.importScript(text)
        val file = dir.resolve("file_io.kcp")
        io.exportFile(script, file)
        assertTrue(file.exists())
        val reimported = io.importFile(file)
        assertEquals("file_io", reimported.name)
        assertValid(reimported)
    }

    @Test
    fun `import の構文エラーは例外として現れる`() {
        assertThrows<DslParseException> {
            io.importScript("var x: number = 1;")
        }
    }
}
