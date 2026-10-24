package me.awabi2048.kantancommander.structure

import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.Bukkit
import java.util.UUID

/**
 * 建造物の管理単位です。
 *
 * 建造物はMyWorld単位で閉じた名前空間を持ち、保存・一覧・削除・設置の
 * すべてが対象ワールドのMyWorldへ解決されます。MyWorldに属さないワールド
 * （メイン・資源・イベント等）およびMyWorldManager未搭載の環境では
 * [Global] へ退避し、従来どおり `structures/` 直下を共有スコープとして
 * 使います。スコープ間で同一名を持ってよく、相互参照しません。
 */
sealed class StructureScope {
    /** MyWorldに属さないワールド・MWM未搭載環境で使う共有スコープです。 */
    data object Global : StructureScope() {
        override fun directoryName(): String? = null
    }

    /** MyWorld単位のスコープです。[id] は MyWorldManager の worldData.uuid です。 */
    data class MyWorld(val id: UUID) : StructureScope() {
        override fun directoryName(): String = id.toString()
    }

    /**
     * ストアルートからこのスコープのディレクトリ名を返します。
     * [Global] はルートそのものを指すため null です。
     */
    abstract fun directoryName(): String?
}

/**
 * 対象ワールドから建造物スコープを解決します。
 *
 * MyWorldManager の所有情報を正とし、公開APIの `findByWorldName` だけで
 * 判定します。MWM未搭載・未登録・解決例外の場合は [StructureScope.Global]
 * へ退避します。別スコープへ無断で読み替えない方針のため、MyWorldが
 * 解決できる場合は必ずMyWorldスコープを返します。
 */
object StructureScopes {
    fun forWorld(worldName: String): StructureScope {
        if (!Bukkit.getPluginManager().isPluginEnabled("MyWorldManager")) {
            return StructureScope.Global
        }
        val id = runCatching {
            MyWorldManagerApi.getWorldRepository()?.findByWorldName(worldName)?.uuid
        }.getOrNull() ?: return StructureScope.Global
        return StructureScope.MyWorld(id)
    }
}
