package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.SearchOriginSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphEditorTest {
    @Test
    fun `duplicate copies every command setting and inserts after the source`() {
        val graph = CommandGraph.empty()
        val source = configuredNode(CommandType.WAIT)
        val successor = CommandType.GIVE_ITEM.newNode()
        graph.entryNodeId = source.id
        source.next = successor.id
        graph.nodes[source.id] = source
        graph.nodes[successor.id] = successor

        val clone = requireNotNull(GraphEditor.duplicate(graph, source.id))

        assertNotEquals(source.id, clone.id)
        assertEquals(source.type, clone.type)
        assertEquals(source.params, clone.params)
        assertNotSame(source.params, clone.params)
        assertEquals(source.configuredFields, clone.configuredFields)
        assertNotSame(source.configuredFields, clone.configuredFields)
        assertEquals(source.targetSpec, clone.targetSpec)
        assertNotSame(source.targetSpec, clone.targetSpec)
        assertEquals(source.secondaryTargetSpec, clone.secondaryTargetSpec)
        assertNotSame(source.secondaryTargetSpec, clone.secondaryTargetSpec)
        assertEquals(source.destinationSpec, clone.destinationSpec)
        assertNotSame(source.destinationSpec, clone.destinationSpec)
        assertEquals(source.destinationTargetSpec, clone.destinationTargetSpec)
        assertNotSame(source.destinationTargetSpec, clone.destinationTargetSpec)
        assertEquals(source.destinationFacingSpec, clone.destinationFacingSpec)
        assertNotSame(source.destinationFacingSpec, clone.destinationFacingSpec)
        assertEquals(source.conditionPositionSpec, clone.conditionPositionSpec)
        assertNotSame(source.conditionPositionSpec, clone.conditionPositionSpec)
        assertEquals(source.blockPositionSpec, clone.blockPositionSpec)
        assertNotSame(source.blockPositionSpec, clone.blockPositionSpec)
        assertEquals(source.blockFromSpec, clone.blockFromSpec)
        assertNotSame(source.blockFromSpec, clone.blockFromSpec)
        assertEquals(source.blockToSpec, clone.blockToSpec)
        assertNotSame(source.blockToSpec, clone.blockToSpec)
        assertEquals(source.soundPositionSpec, clone.soundPositionSpec)
        assertNotSame(source.soundPositionSpec, clone.soundPositionSpec)
        assertEquals(source.summonPositionSpec, clone.summonPositionSpec)
        assertNotSame(source.summonPositionSpec, clone.summonPositionSpec)
        assertEquals(source.temporaryEntityTargetSpec, clone.temporaryEntityTargetSpec)
        assertNotSame(source.temporaryEntityTargetSpec, clone.temporaryEntityTargetSpec)
        assertEquals(source.temporaryLocationPositionSpec, clone.temporaryLocationPositionSpec)
        assertNotSame(source.temporaryLocationPositionSpec, clone.temporaryLocationPositionSpec)
        assertEquals(source.temporaryLocationFacingSpec, clone.temporaryLocationFacingSpec)
        assertNotSame(source.temporaryLocationFacingSpec, clone.temporaryLocationFacingSpec)
        assertEquals(source.itemTempRef, clone.itemTempRef)
        assertEquals(source.blockTempRef, clone.blockTempRef)
        assertEquals(source.soundTempRef, clone.soundTempRef)
        assertEquals(source.effectTempRef, clone.effectTempRef)
        assertEquals(source.snapshot, clone.snapshot)
        assertNotSame(source.snapshot, clone.snapshot)
        clone.params["custom"] = "changed"
        assertEquals("value", source.params["custom"])
        val snapshotNodeId = requireNotNull(requireNotNull(source.snapshot).entryNodeId)
        requireNotNull(clone.snapshot).nodes.getValue(snapshotNodeId).params["seconds"] = "5"
        assertEquals("1", requireNotNull(source.snapshot).nodes.getValue(snapshotNodeId).params["seconds"])
        assertEquals(successor.id, clone.next)
        assertEquals(clone.id, source.next)
        assertNull(clone.trueNext)
        assertNull(clone.falseNext)
        assertNull(clone.pairedNodeId)
        assertTrue(clone.id in graph.nodes)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `duplicate keeps a terminal successor position inside a branch`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val source = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val successor = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        val clone = requireNotNull(GraphEditor.duplicate(graph, source.id))

        assertEquals(clone.id, source.next)
        assertEquals(successor.id, clone.next)
        assertEquals(merge.id, successor.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `duplicate rejects structural nodes and the node count limit`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val forStart = GraphEditor.append(graph, CommandType.FOR_START)
        val forEnd = requireNotNull(forStart.pairedNodeId).let(graph.nodes::get)!!
        val structuralNodes = listOf(condition, merge, forStart, forEnd)
        val originalIds = graph.nodes.keys.toSet()

        structuralNodes.forEach { node ->
            assertFalse(GraphEditor.isDuplicable(node))
            assertFalse(GraphEditor.canDuplicate(graph, node.id))
            assertNull(GraphEditor.duplicate(graph, node.id))
        }

        val ordinary = GraphEditor.append(graph, CommandType.WAIT)
        assertFalse(GraphEditor.canDuplicate(graph, ordinary.id, graph.nodes.size))
        assertNull(GraphEditor.duplicate(graph, ordinary.id, graph.nodes.size))
        assertTrue(GraphEditor.canDuplicate(graph, ordinary.id, graph.nodes.size + 1))
        assertEquals(originalIds + ordinary.id, graph.nodes.keys)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `adjacent reorder swaps execution order without changing node identity`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val second = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val third = GraphEditor.append(graph, CommandType.GIVE_ITEM)

        assertTrue(GraphEditor.swapAdjacent(graph, second.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(second.id, graph.entryNodeId)
        assertEquals(first.id, second.next)
        assertEquals(third.id, first.next)

        assertTrue(GraphEditor.swapAdjacent(graph, second.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(first.id, graph.entryNodeId)
        assertEquals(second.id, first.next)
        assertEquals(third.id, second.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `swap availability matches structural and branch boundaries`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val second = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.WAIT, condition.id)
        GraphEditor.appendMerge(graph, condition.id)

        assertFalse(GraphEditor.canSwapAdjacent(graph, first.id, GraphEditor.ReorderDirection.LEFT))
        assertTrue(GraphEditor.canSwapAdjacent(graph, first.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.canSwapAdjacent(graph, second.id, GraphEditor.ReorderDirection.LEFT))
        assertFalse(GraphEditor.canSwapAdjacent(graph, condition.id, GraphEditor.ReorderDirection.RIGHT))
    }

    @Test
    fun `adjacent reorder works within a branch but does not exchange branch meaning`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueNode = GraphEditor.append(graph, CommandType.WAIT)
        val falseFirst = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val falseSecond = GraphEditor.append(graph, CommandType.GIVE_ITEM, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        assertTrue(GraphEditor.swapAdjacent(graph, falseSecond.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(falseSecond.id, condition.falseNext)
        assertEquals(falseFirst.id, falseSecond.next)
        assertEquals(merge.id, falseFirst.next)
        assertEquals(trueNode.id, condition.trueNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `structural nodes and branch boundary cannot be reordered as simple nodes`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val branchNode = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        GraphEditor.appendMerge(graph, condition.id)

        assertEquals(false, GraphEditor.swapAdjacent(graph, condition.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(false, GraphEditor.swapAdjacent(graph, branchNode.id, GraphEditor.ReorderDirection.LEFT))
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `false branch keeps insertion order`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueCommand = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val first = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val second = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        assertEquals(first.id, condition.falseNext)
        assertEquals(second.id, first.next)
        assertEquals(merge.id, second.next)
        assertEquals(merge.id, trueCommand.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `nested condition is appended to selected false branch`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val inner = GraphEditor.append(graph, CommandType.CONDITION, outer.id)
        GraphEditor.append(graph, CommandType.WAIT, inner.id)
        GraphEditor.appendMerge(graph, inner.id)
        val outerCommand = GraphEditor.append(graph, CommandType.GIVE_ITEM, outer.id)
        GraphEditor.appendMerge(graph, outer.id)

        assertEquals(inner.id, outer.falseNext)
        assertEquals(outerCommand.id, inner.pairedNodeId?.let(graph.nodes::get)?.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `for start creates a paired end node`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val end = start.pairedNodeId?.let(graph.nodes::get)

        assertEquals(CommandType.FOR_END, end?.type)
        assertEquals(end?.id, start.trueNext)
        assertEquals(start.id, end?.pairedNodeId)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `condition deletion is refused while false branch has an execution node`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        GraphEditor.appendMerge(graph, condition.id)

        assertEquals(false, GraphEditor.delete(graph, condition.id))
        assertTrue(condition.id in graph.nodes)
    }

    @Test
    fun `empty condition deletion also removes matching merge and promotes true path`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueNode = GraphEditor.append(graph, CommandType.WAIT)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val after = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)

        assertTrue(GraphEditor.delete(graph, condition.id))
        assertEquals(trueNode.id, graph.entryNodeId)
        assertEquals(after.id, trueNode.next)
        assertTrue(merge.id !in graph.nodes)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `for pair deletion is allowed only while body is empty`() {
        val empty = CommandGraph.empty()
        val emptyStart = GraphEditor.append(empty, CommandType.FOR_START)
        assertTrue(GraphEditor.delete(empty, emptyStart.id))

        val occupied = CommandGraph.empty()
        val occupiedStart = GraphEditor.append(occupied, CommandType.FOR_START)
        GraphEditor.appendToForBody(occupied, occupiedStart.id, CommandType.WAIT)
        assertEquals(false, GraphEditor.delete(occupied, occupiedStart.id))
    }

    @Test
    fun `outer condition cannot merge before nested condition`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.insert(graph, outer.id, GraphEditor.Edge.TRUE, CommandType.CONDITION)

        assertEquals(false, GraphEditor.canAppendMerge(graph, outer.id))
        assertThrows(IllegalArgumentException::class.java) {
            GraphEditor.appendMerge(graph, outer.id)
        }
    }

    @Test
    fun `nested open condition merges into its enclosing continuation`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)

        // 内側条件のTRUE枝は、挿入時点では親MERGEへ直結しています。ここをそのまま
        // 残すと親MERGEへの入力が3本になるため、appendMergeは直結を内側MERGEへ
        // 付け替え、内側MERGEのNEXTだけを親MERGEへ接続します。
        val innerMerge = GraphEditor.appendMerge(graph, inner.id, continuationId = outerMerge.id)

        assertEquals(innerMerge.id, inner.trueNext)
        assertEquals(innerMerge.id, inner.falseNext)
        assertEquals(outerMerge.id, innerMerge.next)
        assertEquals(outerMerge.id, outer.trueNext)
        assertEquals(inner.id, outer.falseNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `nested merge availability stops at the enclosing continuation`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        // 親合流の後ろに別の未合流条件があっても、内側条件の合流判定へ
        // それを混ぜると、無関係な後続経路のために操作ができなくなります。
        GraphEditor.append(graph, CommandType.CONDITION)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)

        assertTrue(GraphEditor.canAppendMerge(graph, inner.id, outerMerge.id))
        GraphEditor.appendMerge(graph, inner.id, outerMerge.id)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `normal insertion into a nested open branch keeps the branch as an early exit`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)

        val inserted = GraphEditor.insert(
            graph,
            inner.id,
            GraphEditor.Edge.FALSE,
            CommandType.WAIT,
            continuationId = outerMerge.id,
        )

        assertEquals(null, inner.pairedNodeId)
        assertEquals(outerMerge.id, inner.trueNext)
        assertEquals(inserted.id, inner.falseNext)
        assertEquals(null, inserted.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `normal insertion into an open condition in a for body remains a terminal`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val condition = GraphEditor.appendToForBody(graph, start.id, CommandType.CONDITION)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!

        val inserted = GraphEditor.insert(
            graph,
            condition.id,
            GraphEditor.Edge.FALSE,
            CommandType.WAIT,
            continuationId = end.id,
        )

        assertEquals(end.id, condition.trueNext)
        assertEquals(null, condition.next)
        assertEquals(inserted.id, condition.falseNext)
        assertEquals(null, inserted.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `merge without a continuation argument infers the enclosing for end`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val condition = GraphEditor.appendToForBody(graph, start.id, CommandType.CONDITION)
        val falseNode = GraphEditor.insert(graph, condition.id, GraphEditor.Edge.FALSE, CommandType.WAIT)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!

        // 旧呼び出し元が境界IDを渡さなくても、条件の所属FOR_ENDを直接参照から
        // 復元し、MERGEをFOR本体の末尾へ接続できることを確認します。
        val merge = GraphEditor.appendMerge(graph, condition.id)

        assertEquals(merge.id, condition.trueNext)
        assertEquals(falseNode.id, condition.falseNext)
        assertEquals(merge.id, falseNode.next)
        assertEquals(end.id, merge.next)
        assertEquals(null, end.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `nested terminal branch never inherits the enclosing for end`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val outer = GraphEditor.appendToForBody(graph, start.id, CommandType.CONDITION)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val inner = GraphEditor.insert(
            graph,
            outer.id,
            GraphEditor.Edge.FALSE,
            CommandType.CONDITION,
            continuationId = end.id,
        )
        val first = GraphEditor.insert(
            graph,
            inner.id,
            GraphEditor.Edge.FALSE,
            CommandType.WAIT,
            continuationId = end.id,
        )
        val second = GraphEditor.insert(
            graph,
            first.id,
            GraphEditor.Edge.NEXT,
            CommandType.DISPLAY_TEXT,
            continuationId = end.id,
        )

        // 外側条件のTRUE枝だけがFOR_ENDへ戻り、内側条件とそのFALSE枝は
        // それぞれの深さで独立した終端になります。
        assertEquals(end.id, outer.trueNext)
        assertEquals(inner.id, outer.falseNext)
        assertEquals(null, inner.trueNext)
        assertEquals(first.id, inner.falseNext)
        assertEquals(second.id, first.next)
        assertEquals(null, second.next)
        assertEquals(1, graph.nodes.values.count { node -> node.next == end.id } +
            graph.nodes.values.count { node -> node.trueNext == end.id } +
            graph.nodes.values.count { node -> node.falseNext == end.id })
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `condition insertion into a nested open branch creates a nested early-exit branch`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)

        val inserted = GraphEditor.insert(
            graph,
            inner.id,
            GraphEditor.Edge.FALSE,
            CommandType.CONDITION,
        )

        assertEquals(null, inserted.pairedNodeId)
        assertEquals(null, inserted.trueNext)
        assertEquals(null, inserted.falseNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `explicit merge can rejoin a nested early-exit branch`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)
        val terminal = GraphEditor.insert(graph, inner.id, GraphEditor.Edge.FALSE, CommandType.WAIT)

        val innerMerge = GraphEditor.appendMerge(graph, inner.id, continuationId = outerMerge.id)

        assertEquals(innerMerge.id, terminal.next)
        assertEquals(innerMerge.id, inner.trueNext)
        assertEquals(terminal.id, inner.falseNext)
        assertEquals(outerMerge.id, innerMerge.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `deleting nested merge restores the false branch as an early exit`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)
        val innerMerge = GraphEditor.appendMerge(graph, inner.id, continuationId = outerMerge.id)

        assertTrue(GraphEditor.delete(graph, innerMerge.id))
        assertEquals(null, inner.pairedNodeId)
        assertEquals(outerMerge.id, inner.trueNext)
        assertEquals(null, inner.falseNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `last false command may be deleted while branches remain merged`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        val falseCommand = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        assertTrue(GraphEditor.delete(graph, falseCommand.id))
        assertEquals(merge.id, condition.falseNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `deleting merge reopens branches and keeps successor on true path`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueCommand = GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)

        assertTrue(GraphEditor.delete(graph, merge.id))
        assertEquals(null, condition.pairedNodeId)
        assertEquals(after.id, trueCommand.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    /** 構造化設定とDISK_CALLのsnapshotを含む、複製対象の全設定を一つのテストデータへ集約します。 */
    private fun configuredNode(type: CommandType): CommandNode {
        val nested = CommandType.WAIT.newNode()
        return type.newNode().also { node ->
            node.params["custom"] = "value"
            node.configuredFields = linkedSetOf("custom", "seconds")
            node.targetSpec = TargetSpec(
                kind = TargetKind.NEAREST_ENTITY,
                entityType = "minecraft:zombie",
                minimumDistance = 1.5,
                maximumDistance = 8.0,
                limit = 2,
                gameMode = "survival",
                tag = "target",
                name = "Target",
                dx = 1.0,
                dy = 2.0,
                dz = 3.0,
                searchOrigin = SearchOriginSpec(
                    positionTemp = "origin",
                    position = PositionSpec(PositionKind.COORDINATES, 4.0, 5.0, 6.0),
                ),
            )
            node.secondaryTargetSpec = TargetSpec(TargetKind.FIXED_ENTITY, fixedEntityId = UUID.randomUUID())
            node.destinationSpec = PositionSpec(PositionKind.TEMPORARY, tempName = "destination")
            node.destinationTargetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            node.destinationFacingSpec = FacingSpec(FacingKind.ROTATION, yaw = 45f, pitch = 10f)
            node.conditionPositionSpec = PositionSpec(PositionKind.MYWORLD_SPAWN)
            node.blockPositionSpec = PositionSpec(PositionKind.CAPTURED)
            node.blockFromSpec = PositionSpec(PositionKind.COORDINATES, 1.0, 2.0, 3.0)
            node.blockToSpec = PositionSpec(PositionKind.COORDINATES, 4.0, 5.0, 6.0)
            node.soundPositionSpec = PositionSpec(PositionKind.TARGET)
            node.summonPositionSpec = PositionSpec(PositionKind.TEMPORARY, tempName = "summon")
            node.temporaryEntityTargetSpec = TargetSpec(TargetKind.TEMPORARY, tempName = "entity")
            node.temporaryLocationPositionSpec = PositionSpec(PositionKind.COORDINATES, 7.0, 8.0, 9.0)
            node.temporaryLocationFacingSpec = FacingSpec(FacingKind.COORDINATES, 10.0, 11.0, 12.0)
            node.itemTempRef = "item"
            node.blockTempRef = "block"
            node.soundTempRef = "sound"
            node.effectTempRef = "effect"
            node.snapshot = CommandGraph(nested.id, linkedMapOf(nested.id to nested))
        }
    }
}
