package me.awabi2048.kantancommander.item

import org.bukkit.event.block.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KantanItemPolicyTest {
    @Test
    fun `block item never handles hand interactions and relies on vanilla placement`() {
        listOf(Action.RIGHT_CLICK_BLOCK, Action.RIGHT_CLICK_AIR, Action.LEFT_CLICK_BLOCK, Action.LEFT_CLICK_AIR)
            .forEach { action ->
                assertEquals(
                    KantanItemAction.NONE,
                    KantanItemPolicy.itemAction(KantanItemKind.BLOCK, action, sneaking = false),
                )
                assertEquals(
                    KantanItemAction.NONE,
                    KantanItemPolicy.itemAction(KantanItemKind.BLOCK, action, sneaking = true),
                )
            }
    }

    @Test
    fun `disk never opens an editor from direct hand interaction`() {
        listOf(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK).forEach { action ->
            assertEquals(
                KantanItemAction.NONE,
                KantanItemPolicy.itemAction(KantanItemKind.DISK, action, sneaking = false),
            )
            // ディスクによる設置（Shift+右クリック）は仕様として廃止している。
            assertEquals(
                KantanItemAction.NONE,
                KantanItemPolicy.itemAction(KantanItemKind.DISK, action, sneaking = true),
            )
        }
        listOf(Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK).forEach { action ->
            assertEquals(
                KantanItemAction.NONE,
                KantanItemPolicy.itemAction(KantanItemKind.DISK, action, sneaking = false),
            )
        }
    }

    @Test
    fun `unrelated item never enters kantan interaction`() {
        listOf(Action.RIGHT_CLICK_BLOCK, Action.RIGHT_CLICK_AIR).forEach { action ->
            assertEquals(
                KantanItemAction.NONE,
                KantanItemPolicy.itemAction(KantanItemKind.NONE, action, sneaking = true),
            )
            assertEquals(
                KantanItemAction.NONE,
                KantanItemPolicy.itemAction(KantanItemKind.NONE, action, sneaking = false),
            )
        }
    }
}
