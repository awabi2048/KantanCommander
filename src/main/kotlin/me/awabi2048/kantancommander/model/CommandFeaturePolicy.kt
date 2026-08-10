package me.awabi2048.kantancommander.model

/**
 * ディスク種別ごとの能力境界です。
 * GUIだけで候補を隠す実装にせず、保存・実行・出力も同じ許可集合を参照します。
 */
object CommandFeaturePolicy {
    private val simpleCommands = setOf(
        CommandType.TELEPORT,
        CommandType.GIVE_ITEM,
        CommandType.ENTITY_ACTION,
        CommandType.DISPLAY_TEXT,
        CommandType.WAIT,
        CommandType.CONTEXT,
    )

    fun allows(profile: DiskProfile, type: CommandType): Boolean =
        profile == DiskProfile.STANDARD || type in simpleCommands

    fun validate(script: DiskScript): List<String> = buildList {
        fun visit(graph: CommandGraph, path: String) {
            graph.nodes.values.forEach { node ->
                if (!allows(script.effectiveProfile, node.type)) {
                    add("$path/${node.id}: ${script.effectiveProfile}では${node.type}を使用できません")
                }
                node.snapshot?.let { visit(it, "$path/${node.id}/snapshot") }
            }
        }
        visit(script.graph, "root")
    }
}
