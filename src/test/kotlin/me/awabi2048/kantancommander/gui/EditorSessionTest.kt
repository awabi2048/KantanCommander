package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.DiskPlacement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class EditorSessionTest {
    @Test
    fun `subscreen routes preserve viewport and placement context`() {
        val placement = DiskPlacement(
            scriptId = UUID.randomUUID(),
            world = "myworld",
            x = 12,
            y = 64,
            z = -8,
            facing = "north",
            displayId = null,
        )
        val session = EditorSession.forPlacement(placement).withOrigin(MapPoint(7, 3))
        val route = session.route(SequenceEditorMenu.OWNER, "command_settings", mapOf("nodeId" to UUID.randomUUID().toString()))

        assertEquals(session, EditorSession.from(route))
        assertEquals("myworld", route.payload[EditorSession.WORLD])
        assertEquals("7", route.payload[EditorSession.ORIGIN_X])
        assertEquals("3", route.payload[EditorSession.ORIGIN_Y])
    }
}
