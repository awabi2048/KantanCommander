package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.*
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.model.CommandType
import me.awabi2048.kantancommander.data.model.DiskScript
import me.awabi2048.kantancommander.data.model.ScriptCommand
import me.awabi2048.kantancommander.util.I18nHelper
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import java.util.UUID

class CommandParamMenu(private val plugin: KantanCommanderPlugin) : Listener {

    private companion object {
        const val OWNER = "kantan_commander"
        const val MENU_ID = "edit_param"

        // SETTINGS_54 の本文領域だけを、この画面のパラメータ一覧として使う。
        val PARAMETER_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        )
    }

    private val navigation get() = CCSystem.getAPI().getMenuNavigationService()
    private val elementService get() = CCSystem.getAPI().getGuiElementService()
    private val layout get() = CCSystem.getAPI().getGuiLayoutService().settings54()
    private val soundService get() = CCSystem.getAPI().getMenuSoundService()

    /** 編集中のデータ (playerUUID -> EditSession) */
    private val sessions = mutableMapOf<UUID, EditSession>()

    private data class EditSession(
        val script: DiskScript,
        val commandIndex: Int,
        val command: ScriptCommand
    )

    /** Dialog入力待ちのデータ */
    private data class PendingInput(
        val paramIndex: Int,
        val paramDef: CommandType.ParamDef,
        val onComplete: (String) -> Unit
    )
    private val pendingInputs = mutableMapOf<UUID, PendingInput>()

    fun initialize() {
        navigation.registerOpener(OWNER, MENU_ID) { player, route ->
            val uuidStr = route.payload["scriptUuid"] ?: return@registerOpener false
            val idx = route.payload["index"]?.toIntOrNull() ?: return@registerOpener false
            val scriptUuid = try { java.util.UUID.fromString(uuidStr) } catch (_: Exception) { return@registerOpener false }
            val script = me.awabi2048.kantancommander.data.DataManager.load(scriptUuid) ?: return@registerOpener false
            if (idx < 0 || idx >= script.commands.size) return@registerOpener false
            open(player, script, idx)
            true
        }
    }

    fun open(player: Player, script: DiskScript, commandIndex: Int) {
        val cmd = script.commands.getOrNull(commandIndex) ?: return
        val session = EditSession(script, commandIndex, cmd)
        sessions[player.uniqueId] = session

        val typeName = I18nHelper.string(player, cmd.type.displayNameKey)
        val holder = KantanMenuHolder(player.uniqueId, OWNER, MENU_ID)
        val inventory = Bukkit.createInventory(holder, layout.size, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(I18nHelper.string(player, "gui.edit_param.title", mapOf("type" to typeName))))
        holder.backingInventory = inventory

        render(player, session, inventory)
        player.openInventory(inventory)
        soundService.onMenuOpen(player, MENU_ID)
    }

    private fun render(player: Player, session: EditSession, inventory: Inventory) {
        inventory.clear()

        KantanGuiElements.applyStandardFrame(inventory)

        val params = session.command.type.paramDefinitions

        params.forEachIndexed { index, paramDef ->
            if (index < PARAMETER_SLOTS.size) {
                val currentValue = session.command.getString(paramDef.key, "?")
                val displayName = I18nHelper.string(player, paramDef.displayNameKey)
                inventory.setItem(PARAMETER_SLOTS[index], elementService.item(GuiItemSpec(
                    material = paramIcon(paramDef),
                    name = GuiNameSpec.Text("$displayName: $currentValue", GuiNameStyle.PRIMARY),
                    lore = GuiLoreSpec.Rich(
                        listOf(GuiLoreLine.SingleAction(I18nHelper.string(player, "gui.edit_param.click_to_edit"))),
                        GuiLoreFrame.BOTH
                    ),
                    role = GuiElementRole.CONTENT,
                    amount = 1
                )))
            }
        }

        // 戻る
        inventory.setItem(layout.backSlot, elementService.backItem(I18nHelper.string(player, "gui.common.back")))
    }

    // ─── クリック処理 ──────────────────────────────

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? KantanMenuHolder ?: return
        if (holder.menuOwner != OWNER || holder.menuId != MENU_ID) return
        val player = event.whoClicked as? Player ?: return
        if (holder.ownerId != player.uniqueId) { event.isCancelled = true; return }
        event.isCancelled = true

        val session = sessions[player.uniqueId] ?: return
        val inv = event.view.topInventory
        val slot = event.rawSlot

        if (slot == layout.backSlot) {
            soundService.onMenuClick(player, MENU_ID, MenuClickType.CANCEL)
            plugin.sequenceEditorMenu.open(player, session.script)
            return
        }

        val params = session.command.type.paramDefinitions
        val paramIndex = PARAMETER_SLOTS.indexOf(slot)
        if (paramIndex !in params.indices) return

        val paramDef = params[paramIndex]
        val currentValue = session.command.getString(paramDef.key, "")

        // パラメータスロットクリック → 編集ダイアログ
        player.closeInventory()
        if (isNumericParam(paramDef)) {
            openNumberEditDialog(player, session, paramIndex, paramDef, currentValue)
        } else {
            openTextEditDialog(player, session, paramIndex, paramDef, currentValue)
        }
    }

    private fun openNumberEditDialog(player: Player, session: EditSession, paramIndex: Int, paramDef: CommandType.ParamDef, currentValue: String) {
        pendingInputs[player.uniqueId] = PendingInput(paramIndex, paramDef) { newVal ->
            session.command.params[paramDef.key] = newVal
            open(player, session.script, session.commandIndex)
        }

        me.awabi2048.kantancommander.util.DialogInputUtil.showTextInput(
            player = player,
            titleKey = paramDef.displayNameKey,
            paramKey = "value",
            paramLabel = Component.text(""),
            currentValue = currentValue,
            plugin = plugin,
            onConfirm = { value ->
                val pending = pendingInputs.remove(player.uniqueId) ?: return@showTextInput
                if (pending.paramIndex != paramIndex) return@showTextInput
                val numeric = value.toDoubleOrNull()
                if (numeric == null || numeric < 0) {
                    player.sendMessage(I18nHelper.string(player, "message.invalid_number"))
                    openNumberEditDialog(player, session, paramIndex, paramDef, currentValue)
                    return@showTextInput
                }
                pending.onComplete(value)
            },
            onCancel = {
                pendingInputs.remove(player.uniqueId)
                open(player, session.script, session.commandIndex)
            }
        )
    }

    private fun openTextEditDialog(player: Player, session: EditSession, paramIndex: Int, paramDef: CommandType.ParamDef, currentValue: String) {
        // enum系パラメータは singleOption を使用
        val enumOptions = getEnumOptions(player, paramDef, currentValue)
        if (enumOptions != null) {
            pendingInputs[player.uniqueId] = PendingInput(paramIndex, paramDef) { newVal ->
                session.command.params[paramDef.key] = newVal
                open(player, session.script, session.commandIndex)
            }

            me.awabi2048.kantancommander.util.DialogInputUtil.showSingleChoice(
                player = player,
                titleKey = paramDef.displayNameKey,
                paramKey = "value",
                paramLabel = Component.text(""),
                options = enumOptions,
                currentId = currentValue,
                plugin = plugin,
                onConfirm = { value ->
                    val pending = pendingInputs.remove(player.uniqueId) ?: return@showSingleChoice
                    if (pending.paramIndex != paramIndex) return@showSingleChoice
                    pending.onComplete(value)
                },
                onCancel = {
                    pendingInputs.remove(player.uniqueId)
                    open(player, session.script, session.commandIndex)
                }
            )
        } else {
            pendingInputs[player.uniqueId] = PendingInput(paramIndex, paramDef) { newVal ->
                session.command.params[paramDef.key] = newVal
                open(player, session.script, session.commandIndex)
            }

            me.awabi2048.kantancommander.util.DialogInputUtil.showTextInput(
                player = player,
                titleKey = paramDef.displayNameKey,
                paramKey = "value",
                paramLabel = Component.text(""),
                currentValue = currentValue,
                maxLength = 200,
                plugin = plugin,
                onConfirm = { value ->
                    val pending = pendingInputs.remove(player.uniqueId) ?: return@showTextInput
                    if (pending.paramIndex != paramIndex) return@showTextInput
                    pending.onComplete(value)
                },
                onCancel = {
                    pendingInputs.remove(player.uniqueId)
                    open(player, session.script, session.commandIndex)
                }
            )
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? KantanMenuHolder ?: return
        if (holder.menuOwner != OWNER) return
        event.isCancelled = true
    }

    // ─── ヘルパー ──────────────────────────────

    private fun paramIcon(paramDef: CommandType.ParamDef): Material = when (paramDef) {
        is CommandType.ParamDef.SOUND -> Material.MUSIC_DISC_13
        is CommandType.ParamDef.VOLUME, is CommandType.ParamDef.PITCH -> Material.LEVER
        is CommandType.ParamDef.CATEGORY -> Material.JUKEBOX
        is CommandType.ParamDef.TEXT, is CommandType.ParamDef.TITLE_TEXT, is CommandType.ParamDef.SUBTITLE_TEXT -> Material.PAPER
        is CommandType.ParamDef.MESSAGE_TARGET -> Material.PLAYER_HEAD
        is CommandType.ParamDef.PARTICLE -> Material.FIREWORK_STAR
        is CommandType.ParamDef.COUNT, is CommandType.ParamDef.SPEED -> Material.REPEATER
        is CommandType.ParamDef.OFFSET_X, is CommandType.ParamDef.OFFSET_Y, is CommandType.ParamDef.OFFSET_Z -> Material.COMPASS
        is CommandType.ParamDef.TICKS -> Material.CLOCK
        is CommandType.ParamDef.FADE_IN, is CommandType.ParamDef.STAY, is CommandType.ParamDef.FADE_OUT -> Material.SAND
        is CommandType.ParamDef.EFFECT_TYPE -> Material.POTION
        is CommandType.ParamDef.DURATION -> Material.GLASS_BOTTLE
        is CommandType.ParamDef.AMPLIFIER -> Material.NETHERITE_SCRAP
    }

    private fun isNumericParam(paramDef: CommandType.ParamDef): Boolean = when (paramDef) {
        is CommandType.ParamDef.VOLUME, is CommandType.ParamDef.PITCH,
        is CommandType.ParamDef.COUNT, is CommandType.ParamDef.SPEED,
        is CommandType.ParamDef.OFFSET_X, is CommandType.ParamDef.OFFSET_Y, is CommandType.ParamDef.OFFSET_Z,
        is CommandType.ParamDef.TICKS,
        is CommandType.ParamDef.FADE_IN, is CommandType.ParamDef.STAY, is CommandType.ParamDef.FADE_OUT,
        is CommandType.ParamDef.DURATION, is CommandType.ParamDef.AMPLIFIER -> true
        else -> false
    }

    private fun getEnumOptions(player: Player, paramDef: CommandType.ParamDef, currentValue: String): List<SingleOptionDialogInput.OptionEntry>? = when (paramDef) {
        is CommandType.ParamDef.CATEGORY -> SoundCategory.entries.map { sc ->
            SingleOptionDialogInput.OptionEntry.create(sc.name, Component.text(sc.name, NamedTextColor.WHITE), currentValue.equals(sc.name, ignoreCase = true))
        }
        is CommandType.ParamDef.MESSAGE_TARGET -> listOf(
            option("nearby", I18nHelper.string(player, "option.target.nearby"), currentValue),
            option("self", I18nHelper.string(player, "option.target.self"), currentValue),
        )
        is CommandType.ParamDef.EFFECT_TYPE -> listOf(
            "SPEED", "SLOWNESS", "HASTE", "MINING_FATIGUE",
            "STRENGTH", "JUMP_BOOST", "REGENERATION", "RESISTANCE",
            "FIRE_RESISTANCE", "WATER_BREATHING", "INVISIBILITY",
            "NIGHT_VISION", "WEAKNESS", "POISON", "WITHER",
            "GLOWING", "LEVITATION", "LUCK", "UNLUCK",
            "SLOW_FALLING", "CONDUIT_POWER", "DOLPHINS_GRACE",
            "BAD_OMEN", "HERO_OF_THE_VILLAGE", "DARKNESS",
            "TRIAL_OMEN", "RAID_OMEN", "WIND_CHARGED", "WEAVING", "OOZING", "INFESTED"
        ).map { SingleOptionDialogInput.OptionEntry.create(it, Component.text(it, NamedTextColor.WHITE), currentValue.equals(it, ignoreCase = true)) }
        else -> null
    }

    private fun option(id: String, label: String, currentValue: String): SingleOptionDialogInput.OptionEntry =
        SingleOptionDialogInput.OptionEntry.create(id, Component.text(label, NamedTextColor.WHITE), currentValue.equals(id, ignoreCase = true))


}
