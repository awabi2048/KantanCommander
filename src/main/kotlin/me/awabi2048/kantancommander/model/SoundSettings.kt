package me.awabi2048.kantancommander.model

import org.bukkit.SoundCategory

/**
 * 効果音コマンド（PLAY_SOUND）の操作種別です。
 *
 * PLAYとSTOPでパラメータ集合と検証・実行が完全に異なるため、同一CommandType内の
 * 切替として保持し、GUIのタブ構成・検証・実行をこの値で分岐させます。保存値は
 * 明示的な大文字文字列にし、欠落した旧データはPLAYとして扱います。
 */
enum class SoundOperation {
    PLAY,
    STOP,
    ;

    companion object {
        fun parse(raw: String?): SoundOperation =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: PLAY
    }
}

/**
 * PLAY_SOUND／STOPの保存キーとPaper API対応を一元化します。
 *
 * 再生（PLAY）は sound／volume／pitch／soundScope／soundPositionSpec／targetSpec、
 * 停止（STOP）は sound（任意・空＝全音）／soundCategory（任意・空＝全カテゴリ）／
 * targetSpec だけを使用します。未指定項目は絞り込み条件の緩和であり、エラーにしません。
 */
object SoundSettings {
    const val PARAM_OPERATION = "soundOperation"
    const val PARAM_SOUND = "sound"
    const val PARAM_VOLUME = "volume"
    const val PARAM_PITCH = "pitch"
    const val PARAM_SCOPE = "soundScope"
    const val PARAM_CATEGORY = "soundCategory"

    const val SCOPE_POSITION = "POSITION"
    const val SCOPE_WORLD = "WORLD"

    fun operation(node: CommandNode): SoundOperation = SoundOperation.parse(node.params[PARAM_OPERATION])

    /** 停止対象のカテゴリです。空文字は全カテゴリを意味します。 */
    fun category(raw: String?): SoundCategory? {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        return runCatching { SoundCategory.valueOf(normalized.uppercase()) }.getOrNull()
    }

    /** カテゴリ指定が空（全対象）または有効なSoundCategory名かを返します。 */
    fun isCategoryValue(raw: String): Boolean {
        if (raw.isBlank()) return true
        return runCatching { SoundCategory.valueOf(raw.trim().uppercase()) }.isSuccess
    }
}
