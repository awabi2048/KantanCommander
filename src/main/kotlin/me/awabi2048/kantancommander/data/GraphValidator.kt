package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import java.util.UUID

object GraphValidator {
    fun validate(graph: CommandGraph, limits: GraphLimits = GraphLimits()): List<String> {
        val errors = mutableListOf<String>()
        if (graph.nodes.size > limits.maximumNodeCount) {
            errors += "ノード数が上限 ${limits.maximumNodeCount} を超えています"
        }
        val entry = graph.entryNodeId
        if (entry == null) {
            if (graph.nodes.isNotEmpty()) errors += "開始ノードがありません"
            return errors
        }
        if (entry !in graph.nodes) errors += "開始ノードが存在しません"

        graph.nodes.values.forEach { node ->
            node.outgoing().filterNot(graph.nodes::containsKey).forEach {
                errors += "${node.id} が存在しないノード $it を参照しています"
            }
            when (node.type) {
                CommandType.CONDITION -> validateCondition(graph, node, errors)
                CommandType.MERGE -> validatePair(graph, node, CommandType.CONDITION, errors)
                CommandType.FOR_START -> validatePair(graph, node, CommandType.FOR_END, errors)
                CommandType.FOR_END -> validatePair(graph, node, CommandType.FOR_START, errors)
                else -> Unit
            }
        }

        if (entry in graph.nodes) {
            val visited = mutableSetOf<UUID>()
            val active = mutableSetOf<UUID>()
            fun visit(id: UUID, depth: Int) {
                if (depth > limits.maximumBranchDepth) {
                    errors += "構造の深さが上限 ${limits.maximumBranchDepth} を超えています"
                    return
                }
                if (!active.add(id)) {
                    errors += "実行グラフに循環があります: $id"
                    return
                }
                if (!visited.add(id)) {
                    active.remove(id)
                    return
                }
                val node = graph.nodes[id]
                val nextDepth = when (node?.type) {
                    CommandType.CONDITION, CommandType.FOR_START -> depth + 1
                    CommandType.MERGE, CommandType.FOR_END -> (depth - 1).coerceAtLeast(0)
                    else -> depth
                }
                node?.outgoing()?.forEach { visit(it, nextDepth) }
                active.remove(id)
            }
            visit(entry, 0)
            (graph.nodes.keys - visited).forEach { errors += "到達不能ノードがあります: $it" }
        }
        return errors.distinct()
    }

    private fun validateCondition(graph: CommandGraph, node: CommandNode, errors: MutableList<String>) {
        if (node.pairedNodeId != null) validatePair(graph, node, CommandType.MERGE, errors)
    }

    private fun validatePair(
        graph: CommandGraph,
        node: CommandNode,
        expected: CommandType,
        errors: MutableList<String>,
    ) {
        val pair = node.pairedNodeId?.let(graph.nodes::get)
        if (pair?.type != expected || pair.pairedNodeId != node.id) {
            errors += "${node.type} ${node.id} の対応ノードが不正です"
        }
    }

    private fun CommandNode.outgoing(): List<UUID> =
        when (type) {
            CommandType.CONDITION -> listOfNotNull(trueNext, falseNext)
            CommandType.FOR_START -> listOfNotNull(trueNext, pairedNodeId)
            else -> listOfNotNull(next)
        }
}

data class GraphLimits(
    val maximumNodeCount: Int = 512,
    val maximumMapWidth: Int = 1024,
    val maximumMapHeight: Int = 256,
    val maximumBranchDepth: Int = 32,
)
