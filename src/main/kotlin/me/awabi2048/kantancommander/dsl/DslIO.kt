package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.effectiveTrigger
import java.io.File
import java.util.UUID

/**
 * DSL テキストとスクリプトモデルの入出力境界です。
 *
 * `.kcp` ファイル（純粋DSLテキスト）がユーザー向けの正本であり、この境界は
 * 文字列・ファイル単位でテキストとモデルを相互変換します。
 *
 * - import: DSLテキスト → 構文解析 → 意味検証 → 低級化 → DiskScript
 * - export: DiskScript → 正規形 DSL テキスト
 */
class DslIO(
    /** ワールド変数名→型の供給です（import 時の意味検証・条件評価に使います）。 */
    private val worldTypes: () -> Map<String, ValueType> = { emptyMap() },
    /** `call "name"` の対象ディスク解決です（import 時のリンク検証）。 */
    private val diskResolver: (String) -> DslDiskLink? = { null },
    /** export 時の DISK_CALL の diskId → 名前の逆引きです。 */
    private val diskNameResolver: (UUID) -> String? = { it.toString() },
) {
    /**
     * DSL テキストを解析・検証・低級化したスクリプトを返します。
     * プログラム名は `program "name"` の宣言名です。
     *
     * @throws DslParseException 構文エラー
     * @throws DslCompileException 意味検証・低級化の失敗
     */
    fun importScript(text: String): DiskScript {
        val program = DslParser.parse(text)
        val result = DslCompiler(worldTypes(), diskResolver).compile(program)
        return DiskScript(
            name = program.name,
            graph = result.graph,
            trigger = result.trigger,
            arguments = result.arguments,
        )
    }

    /**
     * スクリプトを正規形 DSL テキストへ復元します。
     *
     * @throws DslPrintException グラフが DSL で表現できない形を含む場合
     */
    fun exportScript(script: DiskScript): String =
        DslPrinter(diskNameResolver).print(
            script.graph, script.name, script.effectiveTrigger(), script.arguments,
        )

    /** UTF-8 テキストファイルから import します。 */
    fun importFile(file: File): DiskScript =
        importScript(file.readText(Charsets.UTF_8))

    /** 正規形 DSL を UTF-8 ファイルへ書き出します。 */
    fun exportFile(script: DiskScript, file: File) {
        file.parentFile?.mkdirs()
        file.writeText(exportScript(script), Charsets.UTF_8)
    }
}
