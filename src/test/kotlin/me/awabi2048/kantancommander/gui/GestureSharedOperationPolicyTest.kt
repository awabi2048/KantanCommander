package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GestureSharedOperationPolicyTest {
    @Test
    fun `other players can operate only at the empty root`() {
        assertTrue(
            GestureSharedOperationPolicy.allowsOtherPlayer(
                lowerMode = GestureLowerMode.SETTINGS,
                settingRouteDepth = 0,
                hasSelection = false,
                hasChildScreen = false,
                hasPendingState = false,
            ),
        )
    }

    @Test
    fun `selection child screen route and pending state all block sharing`() {
        val cases = listOf(
            { GestureSharedOperationPolicy.allowsOtherPlayer(GestureLowerMode.SETTINGS, 0, true, false, false) },
            { GestureSharedOperationPolicy.allowsOtherPlayer(GestureLowerMode.SETTINGS, 0, false, true, false) },
            { GestureSharedOperationPolicy.allowsOtherPlayer(GestureLowerMode.SETTINGS, 1, false, false, false) },
            { GestureSharedOperationPolicy.allowsOtherPlayer(GestureLowerMode.SETTINGS, 0, false, false, true) },
            { GestureSharedOperationPolicy.allowsOtherPlayer(GestureLowerMode.PICKER, 0, false, false, false) },
        )

        cases.forEach { allows -> assertFalse(allows()) }
    }
}
