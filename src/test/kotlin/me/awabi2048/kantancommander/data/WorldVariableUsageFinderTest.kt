package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.DiskScript
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorldVariableUsageFinderTest {
    private val owner = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `finds generic references structured variable targets and condition targets`() {
        val display = node(CommandType.DISPLAY_TEXT, "text" to "値=${'$'}{counter}")
        val variable = node(
            CommandType.VARIABLE,
            "name" to "counter",
            "operation" to "CHANGE",
        )
        val condition = node(
            CommandType.CONDITION,
            "kind" to ConditionKind.VARIABLE_STATE.name,
            "variable" to "counter",
        )
        val script = script("program", graph(display, variable, condition))
        val placements = listOf(placement("world", script.id))

        val usages = WorldVariableUsageFinder.find("world", "counter", placements, listOf(script))

        assertEquals(listOf("program"), usages.map(WorldVariableUsage::programName))
    }

    @Test
    fun `scans nested disk call snapshots but ignores literal names and other worlds`() {
        val nested = node(CommandType.DISPLAY_TEXT, "text" to "nested=${'$'}{counter}")
        val nestedCall = node(CommandType.DISK_CALL, snapshot = graph(nested))
        val nestedScript = script("nested", graph(nestedCall))
        val literalScript = script("literal", graph(node(CommandType.DISPLAY_TEXT, "text" to "counter")))
        val otherWorldScript = script("other-world", graph(node(CommandType.VARIABLE, "name" to "counter")))
        val placements = listOf(
            placement("world", nestedScript.id),
            // 同じプログラムを複数配置しても、一覧には一度だけ表示します。
            placement("world", nestedScript.id),
            placement("world", literalScript.id),
            placement("other", otherWorldScript.id),
        )

        val usages = WorldVariableUsageFinder.find(
            "world",
            "COUNTER",
            placements,
            listOf(nestedScript, literalScript, otherWorldScript),
        )

        assertEquals(listOf("nested"), usages.map(WorldVariableUsage::programName))
    }

    private fun script(name: String, graph: CommandGraph): DiskScript =
        DiskScript(name = name, owner = owner, graph = graph)

    private fun graph(vararg nodes: CommandNode): CommandGraph =
        CommandGraph(nodes = linkedMapOf<UUID, CommandNode>().apply {
            nodes.forEach { this[it.id] = it }
        })

    private fun node(
        type: CommandType,
        vararg params: Pair<String, String>,
        snapshot: CommandGraph? = null,
    ): CommandNode = CommandNode(
        type = type,
        params = linkedMapOf(*params),
        snapshot = snapshot,
    )

    private fun placement(world: String, scriptId: UUID): DiskPlacement = DiskPlacement(
        world = world,
        x = 0,
        y = 64,
        z = 0,
        scriptId = scriptId,
        facing = "north",
        displayId = null,
    )
}
