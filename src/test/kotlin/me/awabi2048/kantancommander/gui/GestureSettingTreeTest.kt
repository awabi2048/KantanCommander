package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import me.awabi2048.kantancommander.model.setConditionSubject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GestureSettingTreeTest {
    @Test
    fun `tree node exposes nested detail without mixing sibling branches`() {
        val distance = GestureSettingTreeNode("filter:distance", "距離")
        val nearby = GestureSettingTreeNode(
            id = "target:PLAYER",
            label = "プレイヤー",
            selected = true,
            children = listOf(distance),
        )
        val nearest = GestureSettingTreeNode("target:NON_PLAYER_ENTITY", "プレイヤー以外のエンティティ")
        val root = listOf(nearby, nearest)

        assertTrue(nearby.hasChildren)
        assertEquals(distance, nearby.find("filter:distance"))
        assertEquals(null, nearest.find("filter:distance"))
        assertEquals(nearby, root.first { it.id == "target:PLAYER" })
    }

    @Test
    fun `path replaces a selection at the same depth and preserves parents`() {
        val root = GestureSettingTreePath("target", CommandSettingRole.NODE_TARGET)
            .selectAtDepth(0, "target:PLAYER")
        val nested = root.enterChild("target:PLAYER")
            .selectAtDepth(1, "filter:distance")
        val sibling = nested.selectAtDepth(1, "filter:limit")

        assertEquals(listOf("target:PLAYER"), root.nodeIds)
        assertEquals(listOf("target:PLAYER", "filter:distance"), nested.nodeIds)
        assertEquals(listOf("target:PLAYER", "filter:limit"), sibling.nodeIds)
        assertEquals(root, nested.leaveChild(remainingDepth = 1))
    }

    @Test
    fun `selection stays on the current frame except for a second click on a branch`() {
        assertSame(
            GestureSettingSelectionAction.STAY_ON_FRAME,
            settingSelectionAction(wasSelected = false, hasChildren = false),
        )
        assertSame(
            GestureSettingSelectionAction.STAY_ON_FRAME,
            settingSelectionAction(wasSelected = false, hasChildren = true),
        )
        assertSame(
            GestureSettingSelectionAction.STAY_ON_FRAME,
            settingSelectionAction(wasSelected = true, hasChildren = false),
        )
        assertSame(
            GestureSettingSelectionAction.ENTER_CHILD,
            settingSelectionAction(wasSelected = true, hasChildren = true),
        )
    }

    @Test
    fun `interaction mode separates selection from detail navigation`() {
        assertSame(
            GestureSettingSelectionAction.ENTER_CHILD,
            settingSelectionAction(
                wasSelected = false,
                interactionMode = GestureSettingInteractionMode.DETAILS_ONLY,
            ),
        )
        assertSame(
            GestureSettingSelectionAction.STAY_ON_FRAME,
            settingSelectionAction(
                wasSelected = true,
                interactionMode = GestureSettingInteractionMode.SELECT,
            ),
        )
        assertSame(
            GestureSettingSelectionAction.STAY_ON_FRAME,
            settingSelectionAction(
                wasSelected = false,
                interactionMode = GestureSettingInteractionMode.SELECT_AND_DETAILS,
            ),
        )
        assertSame(
            GestureSettingSelectionAction.ENTER_CHILD,
            settingSelectionAction(
                wasSelected = true,
                interactionMode = GestureSettingInteractionMode.SELECT_AND_DETAILS,
            ),
        )
        assertEquals(
            GestureSettingInteractionMode.SELECT_AND_DETAILS,
            GestureSettingTreeNode(
                "branch",
                "選択して詳細",
                children = listOf(GestureSettingTreeNode("leaf", "候補")),
            ).effectiveInteractionMode,
        )
    }

    @Test
    fun `toggle interaction flips the value in one click without a second stage`() {
        // 即時反転の選択肢は選択の二段階・詳細遷移を持たず、常に現在フレームへ留まります。
        assertSame(
            GestureSettingSelectionAction.STAY_ON_FRAME,
            settingSelectionAction(
                wasSelected = false,
                interactionMode = GestureSettingInteractionMode.TOGGLE,
            ),
        )
        assertSame(
            GestureSettingSelectionAction.STAY_ON_FRAME,
            settingSelectionAction(
                wasSelected = true,
                interactionMode = GestureSettingInteractionMode.TOGGLE,
            ),
        )
    }

    @Test
    fun `choice text color follows the interaction mode instead of children`() {
        // 子を持っても遷移を持たない行（領域の内包行など）は値入力と同じ金色です。
        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.GOLD,
            GestureSettingVisualPolicy.settingChoiceTextColor(
                GestureSettingTreeNode(
                    "filter:region",
                    "領域",
                    interactionMode = GestureSettingInteractionMode.SELECT,
                    children = listOf(GestureSettingTreeNode("filter:region-clear", "解除")),
                ),
            ),
        )
        // 子を持たなくても再クリックで子画面へ進む行は水色です。
        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.AQUA,
            GestureSettingVisualPolicy.settingChoiceTextColor(
                GestureSettingTreeNode(
                    "position-entity",
                    "実体参照",
                    interactionMode = GestureSettingInteractionMode.SELECT_AND_DETAILS,
                ),
            ),
        )
        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.AQUA,
            GestureSettingVisualPolicy.settingChoiceTextColor(
                GestureSettingTreeNode(
                    "condition-target",
                    "対象",
                    interactionMode = GestureSettingInteractionMode.DETAILS_ONLY,
                ),
            ),
        )
        // 即時反転は二段階を持たないため金色です。
        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.GOLD,
            GestureSettingVisualPolicy.settingChoiceTextColor(
                GestureSettingTreeNode(
                    "wireframe:toggle",
                    "枠線",
                    interactionMode = GestureSettingInteractionMode.TOGGLE,
                    toggleValue = true,
                ),
            ),
        )
        // 操作不能は遷移意味より無効色を優先します。
        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.GRAY,
            GestureSettingVisualPolicy.settingChoiceTextColor(
                GestureSettingTreeNode(
                    "filter:region",
                    "領域",
                    enabled = false,
                    interactionMode = GestureSettingInteractionMode.SELECT,
                    children = listOf(GestureSettingTreeNode("filter:region-clear", "解除")),
                ),
            ),
        )
    }

    @Test
    fun `random mode opens range input only after a second selection in the current route`() {
        assertEquals(
            VariableModeSelectionAction.SAVE_MODE,
            variableModeSelectionAction(
                me.awabi2048.kantancommander.model.VariableChangeMode.RANDOM,
                wasSelectedInCurrentRoute = false,
            ),
        )
        assertEquals(
            VariableModeSelectionAction.EDIT_RANDOM_RANGE,
            variableModeSelectionAction(
                me.awabi2048.kantancommander.model.VariableChangeMode.RANDOM,
                wasSelectedInCurrentRoute = true,
            ),
        )
        // ASSIGNなど他方式の再クリックで、数値Dialogを誤って開きません。
        assertEquals(
            VariableModeSelectionAction.SAVE_MODE,
            variableModeSelectionAction(
                me.awabi2048.kantancommander.model.VariableChangeMode.ASSIGN,
                wasSelectedInCurrentRoute = true,
            ),
        )
    }

    @Test
    fun `summon child resolves its property only from the current frame`() {
        // 召喚には項目パス欄がないため、有限候補子画面はフレームへ保持した項目IDだけを根拠にします。
        val context = CommandSettingContext(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), null)
        val parent = GestureSettingFrame(context, "summonData", GestureSettingScreen.SUMMON_DATA_ROOT)
        val state = GestureEditorState(java.util.UUID.randomUUID(), null)
            .apply { settingRoute = listOf(parent) }
        assertNull(state.currentSummonPropertyId())
        state.settingRoute = state.settingRoute + GestureSettingFrame(
            context,
            "summonData",
            GestureSettingScreen.SUMMON_PROPERTY_VALUE,
            summonPropertyId = "rabbit_type",
        )
        assertEquals("rabbit_type", state.currentSummonPropertyId())
        // 経路末尾が候補IDへ変わっても、現在フレームの対象項目を誤認しません。
        state.settingTreePath = GestureSettingTreePath(
            "summonData",
            null,
            listOf("summonData:rabbit_type", "summonDataValue:rabbit_type:white"),
        )
        assertEquals("rabbit_type", state.currentSummonPropertyId())
    }

    @Test
    fun `current frame selection reads the current depth instead of the route tail`() {
        val context = CommandSettingContext(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), null)
        val state = GestureEditorState(java.util.UUID.randomUUID(), null)
        // 位置画面からスポーン種別の子画面へ進んだ直後は、現在フレームの選択を
        // 持ちません。末尾に残る親の候補IDを現在フレームの選択へ誤用しません。
        state.settingRoute = listOf(
            GestureSettingFrame(context, "position", GestureSettingScreen.POSITION),
            GestureSettingFrame(context, "position", GestureSettingScreen.MYWORLD_SPAWN_KIND),
        )
        state.settingTreePath = GestureSettingTreePath(
            "position",
            null,
            listOf("position-preset:MYWORLD_SPAWN"),
        )
        assertNull(state.currentFrameSelectionId())
        // 現在画面は経路末尾の候補IDではなくフレームの画面種別です。
        assertSame(GestureSettingScreen.MYWORLD_SPAWN_KIND, state.settingRoute.last().screen)
        // 子画面内で候補を選択すると、現在フレームの深さに候補IDが記録されます。
        state.settingTreePath = state.settingTreePath!!.selectAtDepth(1, "myworld-spawn:GUEST")
        assertEquals("myworld-spawn:GUEST", state.currentFrameSelectionId())
    }

    @Test
    fun `leaving a child keeps the parent selection even without a child choice`() {
        val path = GestureSettingTreePath(
            "position",
            null,
            listOf("position-preset:MYWORLD_SPAWN"),
        )
        // 子画面で候補を選ばず（子スロットが存在しない）戻った場合でも、
        // 末尾の削除ではなく残るフレーム数への切り詰めで親の選択を維持します。
        assertEquals(
            listOf("position-preset:MYWORLD_SPAWN"),
            path.leaveChild(remainingDepth = 1).nodeIds,
        )
        // 子画面で候補を選んだ場合は、子フレームの選択だけを取り除きます。
        val selected = path.selectAtDepth(1, "myworld-spawn:GUEST")
        assertEquals(
            listOf("position-preset:MYWORLD_SPAWN"),
            selected.leaveChild(remainingDepth = 1).nodeIds,
        )
    }

    @Test
    fun `path selection applies exclusively within the current choices`() {
        val preset = GestureSettingTreeNode("target-preset:NEARBY_PLAYERS", "周囲", selected = true)
        val detail = GestureSettingTreeNode("target-detail", "詳細")
        // 詳細の初回クリック直後は詳細だけが選択表示になり、保存値由来の
        // プリセット選択は抑止されます。
        val applied = applyPathSelectionExclusive(listOf(preset, detail), "target-detail")
        assertEquals(listOf(false, true), applied.map { it.selected })
        // 経路が一覧外を指す場合は保存値表示を維持します。
        val untouched = applyPathSelectionExclusive(listOf(preset, detail), "filter:distance")
        assertEquals(listOf(true, false), untouched.map { it.selected })
        // 経路なしでは何も変えません。
        val empty = applyPathSelectionExclusive(listOf(preset, detail), null)
        assertEquals(listOf(true, false), empty.map { it.selected })
    }

    @Test
    fun `single item screens short circuit directly to the target screen`() {
        fun frame(fieldKey: String, screen: GestureSettingScreen) = GestureSettingFrame(
            CommandSettingContext(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), null),
            fieldKey,
            screen,
        )

        // 表示対象タブは経由画面を使わず、対象選択そのものを親画面へ配置します。
        val particle = CommandType.PARTICLE.newNode()
        assertSame(GestureSettingScreen.TARGET, gestureSettingScreenFor(CommandSettingEditor.PARTICLE_DISPLAY))
        assertEquals(
            CommandSettingRole.NODE_TARGET,
            CommandSettingsModel.descriptor(particle, "particleDisplay").role,
        )

        // 停止操作時は対象だけのため直行し、再生操作時は通常遷移します。
        val stopped = CommandType.PLAY_SOUND.newNode()
            .apply { params[SoundSettings.PARAM_OPERATION] = SoundOperation.STOP.name }
        val stoppedDirect = requireNotNull(
            shortCircuitTargetFrame(stopped, frame("soundTargetPosition", GestureSettingScreen.SOUND_TARGET_POSITION)),
        )
        assertSame(GestureSettingScreen.TARGET, stoppedDirect.screen)
        assertEquals(CommandSettingRole.NODE_TARGET, stoppedDirect.context.role)

        val playing = CommandType.PLAY_SOUND.newNode()
            .apply { params[SoundSettings.PARAM_OPERATION] = SoundOperation.PLAY.name }
        assertNull(
            shortCircuitTargetFrame(playing, frame("soundTargetPosition", GestureSettingScreen.SOUND_TARGET_POSITION)),
        )

        // 条件ノードの対象設定は親画面の「対象」タブへ分離したため、
        // CONDITION_DETAIL を経由する直行経路はありません。
        val otherEntity = CommandType.CONDITION.newNode()
            .apply { setConditionSubject(ConditionSubject.OTHER_ENTITY) }
        assertNull(
            shortCircuitTargetFrame(otherEntity, frame("condition", GestureSettingScreen.CONDITION_DETAIL)),
        )

        val playerSubject = CommandType.CONDITION.newNode()
            .apply { setConditionSubject(ConditionSubject.PLAYER) }
        assertNull(
            shortCircuitTargetFrame(playerSubject, frame("condition", GestureSettingScreen.CONDITION_DETAIL)),
        )

        // 対象画面自体や無関係な画面は直行しません。
        assertNull(
            shortCircuitTargetFrame(particle, frame("target", GestureSettingScreen.TARGET)),
        )
    }

    @Test
    fun `visual policy separates selection cardinality and value state`() {
        // 設定項目の通常背景は選択方式・値状態によらず薄灰色で統一します。
        assertEquals(
            org.bukkit.Material.LIGHT_GRAY_CONCRETE,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.EXCLUSIVE,
                GestureSettingValueState.CONFIGURED,
            ),
        )
        assertEquals(
            org.bukkit.Material.LIGHT_GRAY_CONCRETE,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.MULTIPLE,
                GestureSettingValueState.INITIAL,
            ),
        )
        // 旧シグネチャは後方互換のためテクスチャに影響しません。
        assertEquals(
            org.bukkit.Material.LIGHT_GRAY_CONCRETE,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.EXCLUSIVE,
                GestureSettingValueState.CONFIGURED,
                selected = true,
            ),
        )
        assertEquals(
            org.bukkit.Material.LIGHT_GRAY_CONCRETE,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.EXCLUSIVE,
                GestureSettingValueState.CONFIGURED,
                selected = false,
            ),
        )
        // タブの警告はGlowではなく、レガシー表記§c相当の赤文字で示します。
        assertEquals(null, GestureSettingVisualPolicy.tabTextColor(attention = false))
        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.RED,
            GestureSettingVisualPolicy.tabTextColor(attention = true),
        )

        // タブの選択状態は縁取りではなく右へ幅15%伸ばし、選択中タブの背景はシアンの
        // テラコッタで示します。テキスト位置は維持します。
        assertEquals(0.15, GestureSettingVisualPolicy.SELECTED_TAB_EXTENSION_RATIO, 1.0e-9)
        assertEquals(0.5405, GestureSettingVisualPolicy.selectedTabWidth(0.47, selected = true), 1.0e-9)
        assertEquals(0.47, GestureSettingVisualPolicy.selectedTabWidth(0.47, selected = false), 1.0e-9)
        // 左端固定のため、拡張分の半分だけ中心が右へ移動します。
        assertEquals(-0.76225, GestureSettingVisualPolicy.selectedTabCenterX(-0.7975, 0.47, selected = true), 1.0e-9)
        assertEquals(
            org.bukkit.Material.CYAN_TERRACOTTA,
            GestureSettingVisualPolicy.tabMaterial(selected = true),
        )
        assertEquals(
            org.bukkit.Material.LIGHT_GRAY_CONCRETE,
            GestureSettingVisualPolicy.tabMaterial(selected = false),
        )
        assertEquals(-0.7975, GestureSettingVisualPolicy.selectedTabCenterX(-0.7975, 0.47, selected = false), 1.0e-9)
        // タブ以外の設定ボタンは、完了状態にかかわらず選択中だけ白い外周枠で示します。
        assertEquals(org.bukkit.Material.WHITE_CONCRETE, GestureSettingVisualPolicy.nonTabOutlineMaterial(selected = true))
        assertEquals(null, GestureSettingVisualPolicy.nonTabOutlineMaterial(selected = false))

        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.GOLD,
            GestureSettingVisualPolicy.settingChoiceTextColor(GestureSettingTreeNode("value", "値")),
        )
        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.AQUA,
            GestureSettingVisualPolicy.settingChoiceTextColor(
                GestureSettingTreeNode("child", "詳細", children = listOf(GestureSettingTreeNode("leaf", "項目"))),
            ),
        )
        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.GRAY,
            GestureSettingVisualPolicy.settingChoiceTextColor(
                GestureSettingTreeNode("disabled", "無効", enabled = false),
            ),
        )
        assertEquals(
            org.bukkit.Material.LIGHT_BLUE_CONCRETE,
            GestureSettingVisualPolicy.choiceOutlineMaterial(
                GestureSettingTreeNode(
                    "filter:configured",
                    "設定済みの複数選択",
                    selectionMode = GestureSettingSelectionMode.MULTIPLE,
                    valueState = GestureSettingValueState.CONFIGURED,
                ),
            ),
        )
        assertEquals(
            org.bukkit.Material.LIGHT_BLUE_CONCRETE,
            GestureSettingVisualPolicy.choiceOutlineMaterial(
                GestureSettingTreeNode(
                    "choice:configured",
                    "設定済みの択一",
                    selectionMode = GestureSettingSelectionMode.EXCLUSIVE,
                    valueState = GestureSettingValueState.CONFIGURED,
                ),
            ),
        )
        assertEquals(
            org.bukkit.Material.WHITE_CONCRETE,
            GestureSettingVisualPolicy.choiceOutlineMaterial(
                GestureSettingTreeNode(
                    "filter:selected",
                    "現在選択中",
                    selected = true,
                    selectionMode = GestureSettingSelectionMode.MULTIPLE,
                    valueState = GestureSettingValueState.CONFIGURED,
                ),
            ),
        )
    }

    @Test
    fun `boolean choice color comes from the value channel not the selection`() {
        // Boolean値を表示する選択肢は、値を背景色（ON=緑・OFF=灰）で示します。
        // 選択枠・設定済み枠とは別チャネルです。
        assertEquals(
            org.bukkit.Material.LIME_CONCRETE,
            GestureSettingVisualPolicy.choiceMaterial(
                GestureSettingTreeNode("summonData:ai", "AI", toggleValue = true),
            ),
        )
        assertEquals(
            org.bukkit.Material.GRAY_CONCRETE,
            GestureSettingVisualPolicy.choiceMaterial(
                GestureSettingTreeNode("summonData:ai", "AI", toggleValue = false),
            ),
        )
        // 値が未設定の選択肢は中立の薄灰色のままです。
        assertEquals(
            org.bukkit.Material.LIGHT_GRAY_CONCRETE,
            GestureSettingVisualPolicy.choiceMaterial(
                GestureSettingTreeNode("summonData:ai", "AI", toggleValue = null),
            ),
        )
        // 選択中の白枠は背景色のON/OFFと独立しています。
        assertEquals(
            org.bukkit.Material.GRAY_CONCRETE,
            GestureSettingVisualPolicy.choiceMaterial(
                GestureSettingTreeNode("entityData:property:ai", "AI", selected = true, toggleValue = false),
            ),
        )
        assertEquals(
            org.bukkit.Material.WHITE_CONCRETE,
            GestureSettingVisualPolicy.choiceOutlineMaterial(
                GestureSettingTreeNode("entityData:property:ai", "AI", selected = true, toggleValue = false),
            ),
        )
        // 明示設定済みの空色枠も背景色と独立しています。
        assertEquals(
            org.bukkit.Material.LIGHT_BLUE_CONCRETE,
            GestureSettingVisualPolicy.choiceOutlineMaterial(
                GestureSettingTreeNode(
                    "wireframe:toggle",
                    "枠線",
                    toggleValue = true,
                    valueState = GestureSettingValueState.CONFIGURED,
                ),
            ),
        )
        // 操作不能な選択肢はBoolean値よりも無効色を優先します。
        assertEquals(
            org.bukkit.Material.LIGHT_GRAY_TERRACOTTA,
            GestureSettingVisualPolicy.choiceMaterial(
                GestureSettingTreeNode("summonData:ai", "AI", enabled = false, toggleValue = true),
            ),
        )
    }
}
