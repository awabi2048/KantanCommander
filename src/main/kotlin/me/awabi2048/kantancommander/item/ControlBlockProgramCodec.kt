package me.awabi2048.kantancommander.item

import com.google.gson.GsonBuilder
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
                gson.fromJson(json.toString(Charsets.UTF_8), DiskScript::class.java)
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
