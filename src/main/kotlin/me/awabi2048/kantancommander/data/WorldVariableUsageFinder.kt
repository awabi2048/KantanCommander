package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.VariableTemplate
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.UUID

/** ワールド内変数を使用している、同一MyWorld内の配置プログラムです。 */
data class WorldVariableUsage(
    val scriptId: UUID,
    val programName: String,
)

/** 削除境界の判定結果です。使用中の場合は削除せず、その一覧を返します。 */
data class WorldVariableRemovalResult(
    val removed: Boolean,
    val usages: List<WorldVariableUsage> = emptyList(),
)

/**
 * ワールド内変数の使用箇所を、保存済みプログラムから再計算します。
 *
 * ワールド内変数はMyWorld単位の正本なので、未配置の別ワールド用プログラムまで
 * 誤って削除不可にしないよう、現在のMyWorldにある配置だけを対象にします。
 * DISK_CALLのsnapshotも実行時に使われるグラフであるため、必ず再帰的に走査します。
 */
class WorldVariableUsageFinder(
    private val placements: () -> Iterable<DiskPlacement>,
    private val scripts: () -> Iterable<DiskScript>,
) {
    fun find(worldName: String, variableName: String): List<WorldVariableUsage> =
        findAll(worldName, listOf(variableName))[variableName].orEmpty()

    fun findAll(worldName: String, variableNames: Collection<String>): Map<String, List<WorldVariableUsage>> =
        findAll(worldName, variableNames, placements(), scripts())

    companion object {
        /**
         * ストアへ依存しない走査入口です。プログラム使用判定の境界を単体テストで
         * 固定し、一覧表示と削除確認が別々の参照規則を持たないようにします。
         */
        fun find(
            worldName: String,
            variableName: String,
            placements: Iterable<DiskPlacement>,
            scripts: Iterable<DiskScript>,
        ): List<WorldVariableUsage> =
            findAll(worldName, listOf(variableName), placements, scripts)[variableName].orEmpty()

        fun findAll(
            worldName: String,
            variableNames: Collection<String>,
            placements: Iterable<DiskPlacement>,
            scripts: Iterable<DiskScript>,
        ): Map<String, List<WorldVariableUsage>> {
            val normalizedTargets = variableNames
                .associateWith(::normalized)
                .filterValues(String::isNotBlank)
            if (normalizedTargets.isEmpty()) {
                return variableNames.associateWith { emptyList() }
            }

            val usagesByTarget = normalizedTargets.values
                .distinct()
                .associateWith { mutableListOf<WorldVariableUsage>() }
            val scriptById = scripts.associateBy(DiskScript::id)
            // 同じプログラムを同じMyWorldへ複数配置しても、チャット一覧には一度だけ出します。
            val placedScriptIds = placements.asSequence()
                .filter { it.world == worldName }
                .map(DiskPlacement::scriptId)
                .distinct()

            placedScriptIds.mapNotNull { scriptById[it] }.forEach { script ->
                val referencedNames = WorldVariableReferenceScanner.referencedNames(script.graph)
                normalizedTargets.values.distinct()
                    .filter(referencedNames::contains)
                    .forEach { target ->
                        usagesByTarget.getValue(target) += WorldVariableUsage(script.id, script.name)
                    }
            }

            val sortedUsages = usagesByTarget.mapValues { (_, usages) ->
                usages.sortedWith(
                    compareBy<WorldVariableUsage>(
                        { it.programName.lowercase(Locale.ROOT) },
                        { it.programName },
                        { it.scriptId.toString() },
                    ),
                )
            }
            return normalizedTargets.mapValues { (_, target) -> sortedUsages[target].orEmpty() }
        }

        private fun normalized(raw: String): String = raw.trim().lowercase(Locale.ROOT)
    }
}

/** 保存グラフ内のワールド内変数名を、GUIと同じ名前正規化で抽出します。 */
internal object WorldVariableReferenceScanner {
    fun referencedNames(graph: CommandGraph): Set<String> {
        val references = linkedSetOf<String>()
        val visited = Collections.newSetFromMap(IdentityHashMap<CommandGraph, Boolean>())

        fun add(raw: String?) {
            val name = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (name.isNotEmpty()) references += name
        }

        fun scan(current: CommandGraph) {
            if (!visited.add(current)) return
            current.nodes.values.forEach { node ->
                // 文字列・数値式・各種入力欄に埋め込まれた${name}を共通解析します。
                node.params.values.forEach { raw ->
                    VariableTemplate.references(raw).forEach(::add)
                }
                // VARIABLEの対象名とVARIABLE_STATEの比較対象は、${name}ではなく
                // 構造化された名前欄なので、テンプレート走査とは別に明示します。
                if (node.type == CommandType.VARIABLE) add(node.string("name"))
                if (
                    node.type == CommandType.CONDITION &&
                    node.string("kind").equals(ConditionKind.VARIABLE_STATE.name, ignoreCase = true)
                ) {
                    add(node.string("variable"))
                }
                node.snapshot?.let(::scan)
            }
        }

        scan(graph)
        return references
    }
}
