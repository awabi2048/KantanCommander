package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ValueType
import java.io.File
import java.util.UUID

/**
 * DSL テキストとスクリプトモデルの入出力境界です。
 *
 * 永続化 JSON の `dsl` 欄にテキストを同居させる前提で、文字列・ファイル単位の
 * DSL 読み書きを提供します。対ユーザー入口（コマンド・GUI）は後工程で
 * ここへ接続します。
 *
 * - import: DSLテキスト → 構文解析 → 意味検証 → 低級化 → DiskScript
 *   （`dsl` 欄には入力テキストをそのまま保持します）
 * - export: DiskScript のグラフ → 正規形 DSL テキスト（読み取り専用）
 * - 更新: 正規形を再生成して `dsl` 欄を上書きします。グラフが DSL で
 *   表現できない形（折り畳みノード等）のときは null へ正規化します。
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
     * DSL テキストを解析・検証・低級化し、`dsl` 欄に入力テキストを保持した
     * スクリプトを返します。プログラム名は `program "name"` の宣言名です。
     *
     * @throws DslParseException 構文エラー
     * @throws DslCompileException 意味検証・低級化の失敗
     */
    fun importScript(text: String, owner: UUID): DiskScript {
        val program = DslParser.parse(text)
        val graph = DslCompiler(worldTypes(), diskResolver).compile(program)
        return DiskScript(name = program.name, owner = owner, graph = graph, dsl = text)
    }

    /**
     * スクリプトのグラフを正規形 DSL テキストへ復元します（読み取り専用）。
     *
     * @throws DslPrintException グラフが DSL で表現できない形を含む場合
     */
    fun exportScript(script: DiskScript): String =
        DslPrinter(diskNameResolver).print(script.graph, script.name)

    /**
     * 正規形を再生成して `dsl` 欄を更新した複製を返します。
     * 表現できないグラフでは `dsl` を null へ正規化します。
     */
    fun refreshDsl(script: DiskScript): DiskScript =
        script.copy(dsl = runCatching { exportScript(script) }.getOrNull())

    /** UTF-8 テキストファイルから import します。 */
    fun importFile(file: File, owner: UUID): DiskScript =
        importScript(file.readText(Charsets.UTF_8), owner)

    /**
     * 正規形 DSL を UTF-8 ファイルへ書き出し、`dsl` 欄を同じ内容へ更新した
     * 複製を返します。
     */
    fun exportFile(script: DiskScript, file: File): DiskScript {
        val text = exportScript(script)
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
        return script.copy(dsl = text)
    }
}
