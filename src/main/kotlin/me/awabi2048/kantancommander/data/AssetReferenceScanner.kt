package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript

/**
 * issue #16 の asset 参照走査です。
 *
 * Structure（建造物名）・Entity Group（群名）はいずれも script 内の
 * コマンド設定から名前で参照されます。未参照 asset の検出・回収のため、
 * script 群から参照名集合を抽出します。Bukkit 実体を持たず、
 * ワールド全走査は行いません。
 */
object AssetReferenceScanner {
    /** STRUCTURE_SAVE／STRUCTURE_PLACE が参照する建造物名を集めます。 */
    fun structureNames(scripts: List<DiskScript>): Set<String> = scripts
        .flatMap { it.graph.nodes.values }
        .filter { it.type == CommandType.STRUCTURE_SAVE || it.type == CommandType.STRUCTURE_PLACE }
        .mapNotNull { it.params["structure"]?.takeIf(String::isNotBlank) }
        .toSet()

    /** ENTITY_GROUP が参照する群名を集めます。 */
    fun groupNames(scripts: List<DiskScript>): Set<String> = scripts
        .flatMap { it.graph.nodes.values }
        .filter { it.type == CommandType.ENTITY_GROUP }
        .mapNotNull { it.params["group"]?.takeIf(String::isNotBlank) }
        .toSet()
}
