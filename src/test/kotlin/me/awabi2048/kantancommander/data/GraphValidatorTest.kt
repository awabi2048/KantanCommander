package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphValidatorTest {
    @Test
    fun `paired true straight false branch and merge is valid`() {
        val condition = CommandType.CONDITION.newNode()
        val trueNode = CommandType.DISPLAY_TEXT.newNode()
        val falseNode = CommandType.WAIT.newNode()
        val merge = CommandType.MERGE.newNode()
        condition.trueNext = trueNode.id
        condition.falseNext = falseNode.id
        condition.pairedNodeId = merge.id
        trueNode.next = merge.id
        falseNode.next = merge.id
        merge.pairedNodeId = condition.id
        val graph = CommandGraph(condition.id, linkedMapOf(
            condition.id to condition, trueNode.id to trueNode, falseNode.id to falseNode, merge.id to merge
        ))
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }
}
