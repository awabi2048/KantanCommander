package me.awabi2048.kantancommander.item

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import me.awabi2048.kantancommander.data.LegacyConditionMigration
import me.awabi2048.kantancommander.data.LegacyEquipMigration
import me.awabi2048.kantancommander.data.LegacySummonMigration
import me.awabi2048.kantancommander.model.DiskScript
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 制御ブロックアイテムへ埋め込むプログラムの可搬スナップショットです。
 * UUID参照だけを保存すると元スクリプト削除後にアイテムの内容が失われるため、
 * JSON全体を圧縮してアイテム自身へ保持します。
 */
internal object ControlBlockProgramCodec {
    private const val MAX_ENCODED_LENGTH = 2_000_000
    private const val MAX_DECODED_LENGTH = 4_000_000
    private val gson = GsonBuilder().create()

    fun encode(script: DiskScript): String {
        val json = gson.toJson(script).toByteArray(Charsets.UTF_8)
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { gzip -> gzip.write(json) }
        }.toByteArray()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    fun decode(encoded: String): DiskScript? {
        if (encoded.isBlank() || encoded.length > MAX_ENCODED_LENGTH) return null
        return runCatching {
            val compressed = Base64.getUrlDecoder().decode(encoded)
            GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
                val json = readAtMost(gzip, MAX_DECODED_LENGTH)
                    ?: return@runCatching null
                val root = runCatching {
                    JsonParser.parseString(json.toString(Charsets.UTF_8)).asJsonObject
                }.getOrNull() ?: return@runCatching null
                // issue #21: 旧装備操作を PLACE_ITEM へ正本化します。
                // issue #27: 旧条件分類を判定対象へ正本化します。
                // ファイル読込と同じ正規化を適用し、古い埋め込み内容も
                // 現行の実行経路だけで扱います。
                LegacyEquipMigration.normalizeGraph(
                    root["graph"]?.takeIf { it.isJsonObject }?.asJsonObject,
                )
                LegacyConditionMigration.normalizeGraph(
                    root["graph"]?.takeIf { it.isJsonObject }?.asJsonObject,
                )
                LegacySummonMigration.normalizeGraph(
                    root["graph"]?.takeIf { it.isJsonObject }?.asJsonObject,
                )
                val script = gson.fromJson(root, DiskScript::class.java)
                    ?: return@runCatching null
                // issue #8: #8以前に符号化されたアイテムには arguments 欄がありません。
                // Gsonの復元ではKotlinの非null境界が保証されないため、欠落時は空へ正規化します。
                @Suppress("SENSELESS_COMPARISON")
                if (script.arguments == null) {
                    script.arguments = emptyList()
                }
                script
            }
        }.getOrNull()
    }

    /** 圧縮爆弾などの異常なアイテムデータでヒープを使い切らないための上限付き読込です。 */
    private fun readAtMost(input: GZIPInputStream, maximum: Int): ByteArray? {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return result.toByteArray()
            total += read
            if (total > maximum) return null
            result.write(buffer, 0, read)
        }
    }
}
