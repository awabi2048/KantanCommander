package me.awabi2048.kantancommander.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * issue #16 の原子書込の共通基盤です。
 *
 * ScriptStore・PlacementStore・WorldVariableStore・EntityGroupStore が
 * 個別に持っていた `.tmp` 書込＋`ATOMIC_MOVE` 置換を一箇所へ集約します。
 * 非原子フォールバックは行いません。プロセス停止・IO障害の瞬間に正本を
 * 失うためであり、非対応ファイルシステムでは例外で失敗させます。
 */
internal object AtomicFileWriter {
    /**
     * 内容を一時ファイルへ書いてから原子移動で置換します。
     *
     * [description] は失敗時の例外文言に使い、どの正本の保存かを識別します。
     */
    fun write(target: File, content: String, description: String, charset: java.nio.charset.Charset = Charsets.UTF_8) {
        val temporary = target.resolveSibling("${target.name}.tmp")
        temporary.writeText(content, charset)
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (failure: AtomicMoveNotSupportedException) {
            runCatching { Files.deleteIfExists(temporary.toPath()) }
            throw IllegalStateException("${description}を原子的に保存できないファイルシステムです", failure)
        }
    }
}
