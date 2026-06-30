package me.awabi2048.kantancommander.util

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.action.DialogAction
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

object DialogInputUtil {

    fun showTextInput(
        player: Player,
        titleKey: String,
        paramKey: String,
        paramLabel: Component,
        currentValue: String,
        maxLength: Int = 100,
        errorMessage: String? = null,
        plugin: JavaPlugin,
        onConfirm: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        val body = mutableListOf<DialogBody>()
        if (errorMessage != null) {
            body += DialogBody.plainMessage(
                LegacyComponentSerializer.legacySection().deserialize(errorMessage)
            )
        }

        // Dialog の確定処理はキー待受ではなく、ボタンに直接結びつけて入力値の取りこぼしを防ぐ。
        val confirmAction = DialogAction.customClick(
            { response, audience ->
                val target = audience as? Player ?: return@customClick
                if (target.uniqueId != player.uniqueId) return@customClick
                plugin.server.scheduler.runTask(plugin, Runnable {
                    onConfirm(response.getText(paramKey) ?: currentValue)
                })
            },
            ClickCallback.Options.builder().uses(1).build()
        )
        val cancelAction = DialogAction.customClick(
            { _, audience ->
                val target = audience as? Player ?: return@customClick
                if (target.uniqueId != player.uniqueId) return@customClick
                plugin.server.scheduler.runTask(plugin, Runnable { onCancel() })
            },
            ClickCallback.Options.builder().uses(1).build()
        )

        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(LegacyComponentSerializer.legacySection().deserialize("§e${I18nHelper.string(player, titleKey)}"))
                        .body(body)
                        .inputs(listOf(
                            DialogInput.text(paramKey, paramLabel)
                                .initial(currentValue)
                                .maxLength(maxLength)
                                .build()
                        ))
                        .build()
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            Component.text(I18nHelper.string(player, "gui.common.confirm"), NamedTextColor.GREEN), null, 100,
                            confirmAction
                        ),
                        ActionButton.create(
                            Component.text(I18nHelper.string(player, "gui.common.cancel"), NamedTextColor.RED), null, 200,
                            cancelAction
                        )
                    )
                )
        }
        player.showDialog(dialog)
    }

    fun showSingleChoice(
        player: Player,
        titleKey: String,
        paramKey: String,
        paramLabel: Component,
        options: List<SingleOptionDialogInput.OptionEntry>,
        currentId: String,
        plugin: JavaPlugin,
        onConfirm: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        val confirmAction = DialogAction.customClick(
            { response, audience ->
                val target = audience as? Player ?: return@customClick
                if (target.uniqueId != player.uniqueId) return@customClick
                plugin.server.scheduler.runTask(plugin, Runnable {
                    onConfirm(response.getText(paramKey) ?: currentId)
                })
            },
            ClickCallback.Options.builder().uses(1).build()
        )
        val cancelAction = DialogAction.customClick(
            { _, audience ->
                val target = audience as? Player ?: return@customClick
                if (target.uniqueId != player.uniqueId) return@customClick
                plugin.server.scheduler.runTask(plugin, Runnable { onCancel() })
            },
            ClickCallback.Options.builder().uses(1).build()
        )

        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(LegacyComponentSerializer.legacySection().deserialize("§e${I18nHelper.string(player, titleKey)}"))
                        .inputs(listOf(
                            DialogInput.singleOption(paramKey, paramLabel, options)
                                .build()
                        ))
                        .build()
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            Component.text(I18nHelper.string(player, "gui.common.confirm"), NamedTextColor.GREEN), null, 100,
                            confirmAction
                        ),
                        ActionButton.create(
                            Component.text(I18nHelper.string(player, "gui.common.cancel"), NamedTextColor.RED), null, 200,
                            cancelAction
                        )
                    )
                )
        }
        player.showDialog(dialog)
    }

    fun showConfirmation(
        player: Player,
        titleKey: String,
        bodyMessage: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        val confirmAction = DialogAction.customClick(
            { _, audience ->
                val target = audience as? Player ?: return@customClick
                if (target.uniqueId != player.uniqueId) return@customClick
                onConfirm()
            },
            ClickCallback.Options.builder().uses(1).build()
        )
        val cancelAction = DialogAction.customClick(
            { _, audience ->
                val target = audience as? Player ?: return@customClick
                if (target.uniqueId != player.uniqueId) return@customClick
                onCancel()
            },
            ClickCallback.Options.builder().uses(1).build()
        )

        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(LegacyComponentSerializer.legacySection().deserialize("§e${I18nHelper.string(player, titleKey)}"))
                        .body(listOf(
                            DialogBody.plainMessage(
                                LegacyComponentSerializer.legacySection().deserialize(bodyMessage)
                            )
                        ))
                        .build()
                )
                .type(
                    DialogType.confirmation(
                        ActionButton.create(
                            Component.text(I18nHelper.string(player, "gui.common.confirm"), NamedTextColor.GREEN), null, 100,
                            confirmAction
                        ),
                        ActionButton.create(
                            Component.text(I18nHelper.string(player, "gui.common.cancel"), NamedTextColor.RED), null, 200,
                            cancelAction
                        )
                    )
                )
        }
        player.showDialog(dialog)
    }
}
