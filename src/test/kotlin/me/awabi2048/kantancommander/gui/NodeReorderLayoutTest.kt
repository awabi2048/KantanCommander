package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NodeReorderLayoutTest {
    @Test
    fun `button size is half of the node icon edge`() {
        // 要求どおり、入れ替え・折り畳みボタンはノードアイコン一辺の50%とします。
        assertEquals(0.09, NodeReorderLayout.buttonSize(0.18), 1.0e-9)
        assertEquals(0.5, NodeReorderLayout.buttonSize(1.0), 1.0e-9)
    }

    @Test
    fun `buttons stack above the icon with a uniform gap`() {
        val iconSize = 0.18
        val buttonSize = NodeReorderLayout.buttonSize(iconSize)
        val buttonY = NodeReorderLayout.buttonY(0.0, iconSize, buttonSize)
        // ボタン底面はアイコン上面から隙間だけ離れます。
        assertEquals(0.09 + 0.008 + 0.045, buttonY, 1.0e-9)
        assertEquals(buttonY - buttonSize / 2.0, iconSize / 2.0 + NodeReorderLayout.BUTTON_GAP, 1.0e-9)
        // ホバーはボタン列のさらに上へ退避します。
        assertEquals(buttonY + buttonSize / 2.0 + 0.03, NodeReorderLayout.hoverY(buttonY, buttonSize), 1.0e-9)
    }

    @Test
    fun `side buttons sit adjacent and centered as a pair`() {
        val buttonSize = NodeReorderLayout.buttonSize(0.18)
        val offset = NodeReorderLayout.sideOffset(buttonSize)
        // 中心→左右の距離は半辺＋半隙間のため、ボタン間は隙間ちょうどで隣接します。
        assertEquals(buttonSize / 2.0 + NodeReorderLayout.BUTTON_GAP / 2.0, offset, 1.0e-9)
        assertEquals(NodeReorderLayout.BUTTON_GAP, (offset - buttonSize / 2.0) * 2.0, 1.0e-9)
        // 左右ボタンの外端は原点対称であり、対全体が中央揃えになります。
        assertEquals(0.0, (-offset - buttonSize / 2.0) + (offset + buttonSize / 2.0), 1.0e-9)
    }

    @Test
    fun `glyph size follows the button scale at half density`() {
        // 変更前（対ピッチ20%）の実寸比へ換算したうえで50%に縮小します。
        val legacyPitch = 0.30
        val legacySize = legacyPitch * NodeReorderLayout.LEGACY_BUTTON_PITCH_RATIO
        assertEquals(
            NodeReorderLayout.LEGACY_GLYPH_SIZE * NodeReorderLayout.GLYPH_SIZE_SCALE,
            NodeReorderLayout.glyphSize(legacySize, legacyPitch),
            1.0e-9,
        )
        // 50%ボタン化では2.25倍のうえ50%縮小となり、実質1.125倍です。
        val scaled = NodeReorderLayout.glyphSize(NodeReorderLayout.buttonSize(legacyPitch * 0.9), legacyPitch)
        assertEquals(NodeReorderLayout.LEGACY_GLYPH_SIZE * 1.125, scaled, 1.0e-9)
        assertEquals(
            NodeReorderLayout.LEGACY_GLYPH_DY * 1.125,
            NodeReorderLayout.glyphDy(scaled),
            1.0e-9,
        )
    }

    @Test
    fun `fold toggle is unavailable for ordinary nodes`() {
        val graph = CommandGraph.empty()
        val wait = GraphEditor.append(graph, CommandType.WAIT)
        assertFalse(NodeReorderLayout.isFoldToggleAvailable(wait, graph))
        // 合流・ペアなしFOR開始・FOR終了も対象外です（種別判定のみのため未挿入で検証）。
        assertFalse(NodeReorderLayout.isFoldToggleAvailable(CommandType.MERGE.newNode(), graph))
        assertFalse(NodeReorderLayout.isFoldToggleAvailable(CommandType.FOR_START.newNode(), graph))
        assertFalse(NodeReorderLayout.isFoldToggleAvailable(CommandType.FOR_END.newNode(), graph))
    }

    @Test
    fun `fold toggle follows the for pair`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        assertTrue(NodeReorderLayout.isFoldToggleAvailable(start, graph))
    }

    @Test
    fun `fold toggle requires a merged condition`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        // 未合流の条件分岐は折り畳めません。
        assertFalse(NodeReorderLayout.isFoldToggleAvailable(condition, graph))
        GraphEditor.appendMerge(graph, condition.id)
        assertTrue(NodeReorderLayout.isFoldToggleAvailable(condition, graph))
    }

    @Test
    fun `fold toggle stays available on folded nodes`() {
        val forGraph = CommandGraph.empty()
        val start = GraphEditor.append(forGraph, CommandType.FOR_START)
        assertTrue(GraphEditor.foldForStart(forGraph, start.id))
        assertTrue(NodeReorderLayout.isFoldToggleAvailable(forGraph.nodes.getValue(start.id), forGraph))

        val ifGraph = CommandGraph.empty()
        val condition = GraphEditor.append(ifGraph, CommandType.CONDITION)
        GraphEditor.appendMerge(ifGraph, condition.id)
        assertTrue(GraphEditor.foldCondition(ifGraph, condition.id))
        assertTrue(NodeReorderLayout.isFoldToggleAvailable(ifGraph.nodes.getValue(condition.id), ifGraph))
    }
}
