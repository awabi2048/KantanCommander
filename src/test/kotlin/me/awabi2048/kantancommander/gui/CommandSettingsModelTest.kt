package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TemporaryVariableType
import java.util.UUID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows

class CommandSettingsModelTest {
    @Test
    fun `target roles share inventory and gesture storage`() {
        val node = CommandNode(type = CommandType.TELEPORT)
        val destination = TargetSpec(TargetKind.NEAREST_PLAYER)

        CommandSettingsModel.setTargetSpec(node, CommandSettingRole.DESTINATION, destination)

        assertEquals(destination, node.destinationTargetSpec)
        assertNull(node.destinationSpec)
        assertEquals(destination, CommandSettingsModel.targetSpec(node, CommandSettingRole.DESTINATION))
        assertEquals(PositionKind.TARGET, CommandSettingsModel.positionKind(node, CommandSettingRole.DESTINATION))
    }

    @Test
    fun `context position role does not overwrite destination`() {
        val node = CommandNode(type = CommandType.CONTEXT)
        val position = PositionSpec(PositionKind.COORDINATES, x = 1.0, y = 2.0, z = 3.0)

        CommandSettingsModel.setPositionSpec(node, CommandSettingRole.CONTEXT_POSITION, position)

        assertEquals(position, node.contextOverride?.position)
        assertNull(node.destinationSpec)
        assertEquals(position, CommandSettingsModel.positionSpec(node, CommandSettingRole.CONTEXT_POSITION))
    }

    @Test
    fun `coordinate position is incomplete until all values are entered`() {
        val node = CommandType.TELEPORT.newNode()

        CommandSettingsModel.setPositionSpec(
            node,
            CommandSettingRole.DESTINATION,
            PositionSpec(PositionKind.COORDINATES),
        )

        assertEquals(PositionKind.COORDINATES, CommandSettingsModel.positionKind(node, CommandSettingRole.DESTINATION))
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "position", CommandSettingRole.DESTINATION))
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "destination"))
        assertTrue(node.configuredFields == null || "destination" !in node.configuredFields.orEmpty())

        CommandSettingsModel.setPositionSpec(
            node,
            CommandSettingRole.DESTINATION,
            PositionSpec(PositionKind.COORDINATES, x = 1.0, y = 2.0, z = 3.0),
        )

        assertTrue(CommandSettingsModel.isFieldConfigured(node, "position", CommandSettingRole.DESTINATION))
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "destination"))
    }

    @Test
    fun `incomplete structured values never become configured from an old marker`() {
        val node = CommandType.CONTEXT.newNode().apply {
            configuredFields = linkedSetOf("context.position")
            contextOverride = ExecutionContextSpec(position = PositionSpec(PositionKind.COORDINATES))
        }

        assertFalse(CommandSettingsModel.isFieldConfigured(node, "position", CommandSettingRole.CONTEXT_POSITION))
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "context"))
    }

    @Test
    fun `fixed target is incomplete until an entity is captured`() {
        val node = CommandType.ENTITY_DELETE.newNode()
        val incomplete = TargetSpec(TargetKind.FIXED_ENTITY)

        CommandSettingsModel.setTargetSpec(node, CommandSettingRole.NODE_TARGET, incomplete)

        assertFalse(CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.NODE_TARGET))
        assertFalse(node.isExplicitlyConfigured("target"))

        CommandSettingsModel.setTargetSpec(
            node,
            CommandSettingRole.NODE_TARGET,
            incomplete.copy(fixedEntityId = UUID.randomUUID()),
        )

        assertTrue(CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.NODE_TARGET))
    }

    @Test
    fun `temporary target position and facing are incomplete until a name is supplied`() {
        val node = CommandType.TELEPORT.newNode()

        CommandSettingsModel.setTargetSpec(
            node,
            CommandSettingRole.NODE_TARGET,
            TargetSpec(TargetKind.TEMPORARY),
        )
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.NODE_TARGET))

        CommandSettingsModel.setTargetSpec(
            node,
            CommandSettingRole.NODE_TARGET,
            TargetSpec(TargetKind.TEMPORARY, tempName = "selected"),
        )
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.NODE_TARGET))

        CommandSettingsModel.setPositionSpec(
            node,
            CommandSettingRole.DESTINATION,
            PositionSpec(PositionKind.TEMPORARY),
        )
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "destination"))

        CommandSettingsModel.setPositionSpec(
            node,
            CommandSettingRole.DESTINATION,
            PositionSpec(PositionKind.TEMPORARY, tempName = "point"),
        )
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "destination"))

        CommandSettingsModel.setFacingSpec(
            node,
            FacingSpec(FacingKind.TEMPORARY),
            CommandSettingRole.DESTINATION_FACING,
        )
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "destinationFacing"))

        CommandSettingsModel.setFacingSpec(
            node,
            FacingSpec(FacingKind.TEMPORARY, tempName = "point"),
            CommandSettingRole.DESTINATION_FACING,
        )
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "destinationFacing"))
    }

    @Test
    fun `temporary value fields follow the selected type`() {
        val node = CommandType.TEMP_SET.newNode()

        assertEquals(listOf("name", "tempType", "value"), CommandSettingsModel.visibleFields(node).map { it.key })
        node.params["tempType"] = TemporaryVariableType.POSITION.name
        assertEquals(listOf("name", "tempType", "x", "y", "z"), CommandSettingsModel.visibleFields(node).map { it.key })
        node.params["tempType"] = TemporaryVariableType.SOUND.name
        assertEquals(listOf("name", "tempType", "sound", "volume", "pitch"), CommandSettingsModel.visibleFields(node).map { it.key })
    }

    @Test
    fun `coordinate facing is incomplete until all values are entered`() {
        val node = CommandType.CONTEXT.newNode()

        CommandSettingsModel.setFacingSpec(
            node,
            FacingSpec(FacingKind.COORDINATES, x = 1.0, y = null, z = 3.0),
            CommandSettingRole.CONTEXT_FACING,
        )

        assertFalse(CommandSettingsModel.isFieldConfigured(node, "facing", CommandSettingRole.CONTEXT_FACING))
        assertFalse(node.isExplicitlyConfigured("context.facing"))

        CommandSettingsModel.setFacingSpec(
            node,
            FacingSpec(FacingKind.COORDINATES, x = 1.0, y = 2.0, z = 3.0),
            CommandSettingRole.CONTEXT_FACING,
        )

        assertTrue(CommandSettingsModel.isFieldConfigured(node, "facing", CommandSettingRole.CONTEXT_FACING))
    }

    @Test
    fun `empty explicitly entered text is not a configured value`() {
        val node = CommandType.WAIT.newNode()

        CommandSettingsModel.setParameter(node, "seconds", "")

        assertFalse(CommandSettingsModel.isFieldConfigured(node, "seconds"))
        assertFalse(node.isExplicitlyConfigured("seconds"))
    }

    @Test
    fun `position lookup never silently falls back to context for another role`() {
        val node = CommandType.APPLY_EFFECT.newNode().apply {
            contextOverride = ExecutionContextSpec(
                position = PositionSpec(PositionKind.COORDINATES, x = 1.0, y = 2.0, z = 3.0),
            )
        }

        assertNull(CommandSettingsModel.positionSpec(node, CommandSettingRole.NODE_TARGET))
        assertNull(CommandSettingsModel.positionKind(node, CommandSettingRole.NODE_TARGET))
    }

    @Test
    fun `clearing context resets both override values and source selection`() {
        val node = CommandType.APPLY_EFFECT.newNode().apply {
            contextOverride = ExecutionContextSpec()
            contextSource = me.awabi2048.kantancommander.model.ContextSource.PREVIOUS
        }

        CommandSettingsModel.clearContextOverride(node)

        assertNull(node.contextOverride)
        assertEquals(me.awabi2048.kantancommander.model.ContextSource.BASE, node.contextSource)
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "context"))
    }

    @Test
    fun `context fields use a separate configured namespace from node target`() {
        val node = CommandType.APPLY_EFFECT.newNode()
        val target = TargetSpec(TargetKind.NEAREST_PLAYER)
        val contextTarget = TargetSpec(TargetKind.ALL_PLAYERS)

        CommandSettingsModel.setTargetSpec(node, null, target)
        CommandSettingsModel.setTargetSpec(node, CommandSettingRole.CONTEXT_TARGET, contextTarget)

        assertTrue(CommandSettingsModel.isFieldConfigured(node, "target"))
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.CONTEXT_TARGET))

        CommandSettingsModel.clearContextOverride(node)

        assertEquals(target, node.targetSpec)
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "target"))
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.CONTEXT_TARGET))
    }

    @Test
    fun `clearing context removes nested configured detail state`() {
        val node = CommandType.APPLY_EFFECT.newNode().apply {
            markConfigured("target", "context.target", "context.target.entityType", "context.position")
            contextOverride = ExecutionContextSpec(target = TargetSpec(TargetKind.NEAREST_ENTITY))
        }

        CommandSettingsModel.clearContextOverride(node)

        assertEquals(setOf("target"), node.configuredFields)
    }

    @Test
    fun `context source returning to base clears its configured marker`() {
        val node = CommandType.APPLY_EFFECT.newNode()

        CommandSettingsModel.toggleContextSource(node)
        assertEquals(ContextSource.PREVIOUS, node.contextSource)
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "context"))

        CommandSettingsModel.toggleContextSource(node)

        assertEquals(ContextSource.BASE, node.contextSource)
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "context"))
        assertFalse(node.isExplicitlyConfigured("context"))
    }

    @Test
    fun `visible fields apply the same conditional rules`() {
        val display = CommandType.DISPLAY_TEXT.newNode()
        assertEquals(false, CommandSettingsModel.visibleFields(display).any { it.key == "staySeconds" })
        assertEquals(false, CommandSettingsModel.visibleFields(display).any { it.key == "subtitle" })

        display.params["mode"] = "title"
        assertEquals(true, CommandSettingsModel.visibleFields(display).any { it.key == "staySeconds" })
        assertEquals(true, CommandSettingsModel.visibleFields(display).any { it.key == "subtitle" })

        display.params["mode"] = "actionbar"
        val actionbarDuration = CommandSettingsModel.visibleFields(display).single { it.key == "staySeconds" }
        assertEquals(true, CommandSettingsModel.visibleFields(display).any { it.key == "staySeconds" })
        assertEquals(
            com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys
                .KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DISPLAY_ACTIONBAR_DURATION,
            actionbarDuration.descriptionKey,
        )
        assertEquals(
            com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys
                .KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DISPLAY_ACTIONBAR_DURATION,
            actionbarDuration.actionKey,
        )

        val riding = CommandType.ENTITY_ACTION.newNode()
        assertEquals(true, CommandSettingsModel.visibleFields(riding).any { it.key == "other" })
        riding.params["action"] = "dismount"
        assertEquals(false, CommandSettingsModel.visibleFields(riding).any { it.key == "other" })
    }

    @Test
    fun `gesture display timing stays one tab while showing each current value`() {
        val title = CommandType.DISPLAY_TEXT.newNode().apply { params["mode"] = "title" }

        assertEquals(
            listOf("target", "mode", "text", "subtitle", "staySeconds", "context"),
            CommandSettingsModel.visibleFields(title).map { it.key },
        )
        assertEquals(
            listOf("target", "mode", "text", "subtitle", "staySeconds", "context"),
            CommandSettingsModel.gestureVisibleFields(title).map { it.key },
        )
        assertEquals(
            com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys
                .KANTAN_COMMANDER_CLEAN_GUI_FIELD_DURATION,
            CommandSettingsModel.gestureVisibleFields(title).single { it.key == "staySeconds" }.label,
        )
        assertEquals(
            DisplayValue.Timing("1", "3", "1"),
            CommandSettingsModel.gestureVisibleFields(title).single { it.key == "staySeconds" }.value(title),
        )

        val tellraw = CommandType.DISPLAY_TEXT.newNode()
        assertEquals(
            listOf("target", "mode", "text", "context"),
            CommandSettingsModel.gestureVisibleFields(tellraw).map { it.key },
        )
    }

    @Test
    fun `incomplete warnings use fixed keys for each setting tab`() {
        val display = CommandType.DISPLAY_TEXT.newNode().apply { params["mode"] = "title" }
        val giveItem = CommandType.GIVE_ITEM.newNode()
        val sound = CommandType.PLAY_SOUND.newNode()
        val teleport = CommandType.TELEPORT.newNode()
        val entityAction = CommandType.ENTITY_ACTION.newNode()
        val summon = CommandType.SUMMON_ENTITY.newNode()

        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.duration",
            CommandSettingsModel.incompleteWarningKey(display, "staySeconds").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.give_target",
            CommandSettingsModel.incompleteWarningKey(giveItem, "target").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.sound_parameters",
            CommandSettingsModel.incompleteWarningKey(sound, "soundParameters").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.destination_facing",
            CommandSettingsModel.incompleteWarningKey(teleport, "destinationFacing").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.tag_operation",
            CommandSettingsModel.incompleteWarningKey(entityAction, "tagOperation").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.summon_position",
            CommandSettingsModel.incompleteWarningKey(summon, "summonPosition").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.sound_scope",
            CommandSettingsModel.incompleteWarningKey(sound, "soundScope").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.sound_position",
            CommandSettingsModel.incompleteWarningKey(sound, "soundPosition").id,
        )
    }

    @Test
    fun `validation fields are normalized to visible setting tabs`() {
        val display = CommandType.DISPLAY_TEXT.newNode().apply { params["mode"] = "title" }
        val tellraw = CommandType.DISPLAY_TEXT.newNode()
        val sound = CommandType.PLAY_SOUND.newNode()

        assertEquals("staySeconds", CommandSettingsModel.visibleAttentionFieldKey(display, "fadeInSeconds"))
        assertEquals("staySeconds", CommandSettingsModel.visibleAttentionFieldKey(display, "staySeconds"))
        assertEquals("staySeconds", CommandSettingsModel.visibleAttentionFieldKey(display, "fadeOutSeconds"))
        assertEquals("text", CommandSettingsModel.visibleAttentionFieldKey(display, "subtitle"))
        assertEquals(null, CommandSettingsModel.visibleAttentionFieldKey(tellraw, "fadeInSeconds"))
        assertEquals("soundParameters", CommandSettingsModel.visibleAttentionFieldKey(sound, "volume"))
        assertEquals("soundParameters", CommandSettingsModel.visibleAttentionFieldKey(sound, "pitch"))
        assertEquals("soundScope", CommandSettingsModel.visibleAttentionFieldKey(sound, "soundPosition"))
    }

    @Test
    fun `entity action fields follow the selected operation allowlist`() {
        val node = CommandType.ENTITY_ACTION.newNode()
        fun keys(action: String): List<String> {
            node.params["action"] = action
            return CommandSettingsModel.visibleFields(node).map { it.key }
        }

        assertEquals(listOf("target", "action", "other", "context"), keys("ride"))
        assertEquals(listOf("target", "action", "context"), keys("dismount"))
        assertEquals(listOf("target", "action", "slot", "item", "overwrite", "context"), keys("equip"))
        assertEquals(listOf("target", "action", "tagOperation", "tag", "context"), keys("tag"))
    }

    @Test
    fun `descriptor maps structured fields to shared editors`() {
        val context = CommandSettingsModel.descriptor(CommandNode(type = CommandType.CONTEXT), "position")
        assertEquals(CommandSettingEditor.POSITION, context.editor)
        assertEquals(CommandSettingRole.CONTEXT_POSITION, context.role)

        val nodeContext = CommandType.APPLY_EFFECT.newNode()
        assertTrue(CommandSettingsModel.visibleFields(nodeContext).any { it.key == "context" })
        assertEquals(
            CommandSettingEditor.CONTEXT,
            CommandSettingsModel.descriptor(nodeContext, "context").editor,
        )

        val variableContext = CommandType.VARIABLE.newNode()
        assertFalse(CommandSettingsModel.visibleFields(variableContext).any { it.key == "context" })

        val condition = CommandSettingsModel.descriptor(CommandNode(type = CommandType.CONDITION), "condition")
        assertEquals(CommandSettingEditor.CONDITION_DETAIL, condition.editor)

        val variable = CommandSettingsModel.descriptor(CommandNode(type = CommandType.VARIABLE), "operation")
        assertEquals(CommandSettingEditor.VARIABLE_OPERATION, variable.editor)
    }

    @Test
    fun `target kind and detailed filter domains are explicitly separated`() {
        val giveItem = CommandType.GIVE_ITEM.newNode()
        assertEquals("target", CommandSettingsModel.visibleFields(giveItem).first().key)
        assertEquals(
            CommandSettingEditor.TARGET,
            CommandSettingsModel.descriptor(giveItem, "target").editor,
        )

        assertTrue(CommandSettingsModel.targetSupportsDetailedFilters(TargetKind.NEARBY_ENTITIES))
        assertTrue(CommandSettingsModel.targetSupportsDetailedFilters(TargetKind.NEAREST_PLAYER))
        assertTrue(CommandSettingsModel.targetSupportsDetailedFilters(TargetKind.FIXED_ENTITY))
        assertFalse(CommandSettingsModel.targetSupportsDetailedFilters(null))
        assertEquals(
            listOf(
                TargetKind.NEAREST_PLAYER,
                TargetKind.NEARBY_PLAYERS,
                TargetKind.ALL_PLAYERS,
                TargetKind.RANDOM_PLAYER,
            ),
            CommandSettingsModel.targetKinds(TargetCategory.PLAYER),
        )
        assertTrue(CommandSettingsModel.targetFilterApplies(TargetKind.NEAREST_PLAYER, "kind"))
        assertFalse(CommandSettingsModel.targetFilterApplies(TargetKind.INHERITED_TARGET, "kind"))
    }

    @Test
    fun `configured state preserves explicit selection of a default value`() {
        val node = CommandType.GIVE_ITEM.newNode()

        assertFalse(CommandSettingsModel.isFieldConfigured(node, "count"))
        node.markConfigured("count")

        assertTrue(CommandSettingsModel.isFieldConfigured(node, "count"))
    }

    @Test
    fun `repeat command exposes only its count field and rejects reserved variable definitions`() {
        val repeat = CommandType.FOR_START.newNode()
        assertEquals(listOf("count"), CommandSettingsModel.visibleFields(repeat).map { it.key })

        val variable = CommandType.VARIABLE.newNode()
        assertThrows(IllegalArgumentException::class.java) {
            CommandSettingsModel.setParameter(variable, "name", "CURRENT_LOOP_COUNT")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommandSettingsModel.setParameter(variable, "name", "current_loop_count")
        }
    }

    @Test
    fun `target filter state is independent for multi-select details`() {
        val node = CommandType.GIVE_ITEM.newNode().apply {
            targetSpec = TargetSpec(TargetKind.NEARBY_PLAYERS, maximumDistance = 12.0, dx = 4.0)
        }

        assertTrue(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "distance"))
        assertTrue(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "range"))
        assertFalse(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "sort"))
    }

    @Test
    fun `sound parameters are configured as one visible field`() {
        val node = CommandType.PLAY_SOUND.newNode()
        val fields = CommandSettingsModel.visibleFields(node)

        assertEquals(listOf("sound", "soundParameters", "soundScope", "soundPosition", "context"), fields.map { it.key })
        assertFalse(fields.any { it.key == "volume" || it.key == "pitch" })

        node.params["volume"] = "0.5"
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "soundParameters"))
    }

    @Test
    fun `block operation exposes only the position domain for the selected mode`() {
        val node = CommandType.BLOCK_OPERATION.newNode()

        assertTrue(CommandSettingsModel.visibleFields(node).any { it.key == "position" })
        assertFalse(CommandSettingsModel.visibleFields(node).any { it.key == "from" })
        assertFalse(CommandSettingsModel.visibleFields(node).any { it.key == "to" })

        node.params["operation"] = "fill"
        assertFalse(CommandSettingsModel.visibleFields(node).any { it.key == "position" })
        assertTrue(CommandSettingsModel.visibleFields(node).any { it.key == "from" })
        assertTrue(CommandSettingsModel.visibleFields(node).any { it.key == "to" })
        assertEquals(CommandSettingRole.BLOCK_FROM, CommandSettingsModel.descriptor(node, "from").role)
    }

    @Test
    fun `inheritance needs a context command with a concrete target`() {
        val graph = me.awabi2048.kantancommander.model.CommandGraph.empty()
        val first = me.awabi2048.kantancommander.data.GraphEditor.append(graph, CommandType.TELEPORT)
        first.targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
        val second = me.awabi2048.kantancommander.data.GraphEditor.append(graph, CommandType.TELEPORT)

        // 通常コマンドのtargetSpecは実行文脈へ入らないため、継承対象は確立されません。
        assertFalse(CommandSettingsModel.hasPriorTargetContext(graph, second.id))
    }

    @Test
    fun `context command with a concrete target enables inheritance`() {
        val graph = me.awabi2048.kantancommander.model.CommandGraph.empty()
        val context = me.awabi2048.kantancommander.data.GraphEditor.append(graph, CommandType.CONTEXT)
        context.contextOverride = ExecutionContextSpec(target = TargetSpec(TargetKind.NEAREST_PLAYER))
        val teleport = me.awabi2048.kantancommander.data.GraphEditor.append(graph, CommandType.TELEPORT)

        assertTrue(CommandSettingsModel.hasPriorTargetContext(graph, teleport.id))
    }

    @Test
    fun `explicit inherited context target clears the established target`() {
        val graph = me.awabi2048.kantancommander.model.CommandGraph.empty()
        val establish = me.awabi2048.kantancommander.data.GraphEditor.append(graph, CommandType.CONTEXT)
        establish.contextOverride = ExecutionContextSpec(target = TargetSpec(TargetKind.NEAREST_PLAYER))
        val reset = me.awabi2048.kantancommander.data.GraphEditor.append(graph, CommandType.CONTEXT)
        reset.contextOverride = ExecutionContextSpec(target = TargetSpec(TargetKind.INHERITED_TARGET))
        val teleport = me.awabi2048.kantancommander.data.GraphEditor.append(graph, CommandType.TELEPORT)

        // 明示的にINHERITEDを設定したCONTEXTは参照先を消すため、確立状態を解除します。
        assertFalse(CommandSettingsModel.hasPriorTargetContext(graph, teleport.id))
    }
}
