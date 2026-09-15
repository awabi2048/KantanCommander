package me.awabi2048.kantancommander.item
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import jp.awabi2048.cccontent.items.PoisonousPotatoComponentPack
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * Kantan Commanderのアイテム種別。
 * かんたんコマンダー制御ブロック（空またはプログラム同梱の設置用）と、
 * プログラムディスク（内容出力用・常に書き込み済み）の2種に分離する。
 */
enum class KantanItemKind { NONE, BLOCK, DISK }

object KantanItemService {
    const val BLOCK_ITEM_ID = "kantan.block"
    const val DISK_ITEM_ID = "kantan.disk"
    /** `/kankoma` と共通付与APIの両方で、1回に付与できる制御ブロック数を固定します。 */
    const val MAX_GRANT_AMOUNT = 1
    private val customItemIdKey = NamespacedKey("kantancommander", "custom_item_id")
    private val diskIdKey = NamespacedKey("kantancommander", "disk_id")
    private val blockProgramKey = NamespacedKey("kantancommander", "block_program")

    /**
     * かんたんコマンダー制御ブロックはバニラのコマンドブロックに相当する設置用アイテム。
     * 通常のブロック配置判定（BlockPlaceEvent）を使うため、許可時の実体素材をINFESTED_STONEとする。
     * 設置権限を持たない利用者への表示はlockedBlockMaterialへ切り替え、論理IDは維持する。
     */
    private val authorizedBlockMaterial = Material.INFESTED_STONE
    /** 設置権限を持たない利用者へ見せる制御ブロックの表示素材です。PDCの論理IDで識別します。 */
    private val lockedBlockMaterial = Material.POISONOUS_POTATO
    /** プログラムディスクはカスタムアイテムの慣習に合わせ、見た目用の毒じゃがいもを基底とする。 */
    private val diskMaterial = Material.POISONOUS_POTATO

    /**
     * 未設定のかんたんコマンダー制御ブロックを生成する。設置時に新しいスクリプトが作られる。
     * 通常のブロックと同じ体験にするため、Loreや毒じゃがいも用のカスタムコンポーネントは付与しない。
     */
    fun createBlock(player: Player): ItemStack = createBlock(null, player)

    /**
     * プログラムを同梱した制御ブロックアイテムを生成します。
     * 表示名は通常の設定項目名に固定し、同梱内容の識別情報だけをLoreへ載せます。
     */
    fun createBlock(script: DiskScript?, player: Player): ItemStack {
        val item = ItemStack(authorizedBlockMaterial, 1)
        item.editMeta { meta ->
            meta.displayName(Component.text(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_NAME_BLOCK),
                NamedTextColor.AQUA,
            ).decoration(TextDecoration.ITALIC, false))
            meta.setItemModel(NamespacedKey("minecraft", "test_block"))
            meta.persistentDataContainer.set(customItemIdKey, PersistentDataType.STRING, BLOCK_ITEM_ID)
            // 制御ブロックは未設定・同梱を問わず単体アイテムへ固定し、内容混同と
            // 複数所持のまま権限が変わる経路をなくします。
            meta.setMaxStackSize(1)
            script?.let {
                meta.persistentDataContainer.set(
                    blockProgramKey,
                    PersistentDataType.STRING,
                    ControlBlockProgramCodec.encode(it),
                )
            }
        }
        // 無機能アイテムの成分はCC-Contentの共有パックへ寄せ、自前の分岐を持ちません。
        PoisonousPotatoComponentPack.applyNonConsumable(item)
        script?.let { updateLore(item, it, player) }
        return item
    }

    /** 書き込み済みのプログラムディスクを生成する。内容はスクリプトの独立コピーを参照する。 */
    fun createDisk(script: DiskScript, player: Player): ItemStack {
        val item = ItemStack(diskMaterial, 1)
        item.editMeta { meta ->
            meta.displayName(Component.text(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_NAME_DISK),
                NamedTextColor.AQUA,
            ).decoration(TextDecoration.ITALIC, false))
            meta.setItemModel(NamespacedKey("minecraft", "music_disc_otherside"))
            meta.setMaxStackSize(1)
            meta.persistentDataContainer.set(customItemIdKey, PersistentDataType.STRING, DISK_ITEM_ID)
            meta.persistentDataContainer.set(diskIdKey, PersistentDataType.STRING, script.id.toString())
        }
        PoisonousPotatoComponentPack.applyNonConsumable(item)
        updateLore(item, script, player)
        return item
    }

    /**
     * 論理IDと物理素材から種別を判定します。Bukkit非依存のため単体テストで検証します。
     *
     * 制御ブロックは許可表示と権限制限表示で素材が変わるため、論理IDを正本とし
     * 両素材を受け付けます。プログラムディスクと同じ毒じゃがいもでも、論理IDが
     * 異なれば誤認しません。想定外の素材は無関係アイテムとして扱います。
     */
    fun kindOfIdAndMaterial(itemId: String?, material: Material): KantanItemKind = when (itemId) {
        BLOCK_ITEM_ID ->
            if (material == authorizedBlockMaterial || material == lockedBlockMaterial) KantanItemKind.BLOCK
            else KantanItemKind.NONE
        DISK_ITEM_ID ->
            if (material == diskMaterial) KantanItemKind.DISK else KantanItemKind.NONE
        else -> KantanItemKind.NONE
    }

    fun kind(item: ItemStack?): KantanItemKind {
        if (item == null) return KantanItemKind.NONE
        val meta = item.itemMeta ?: return KantanItemKind.NONE
        val itemId = meta.persistentDataContainer.get(customItemIdKey, PersistentDataType.STRING)
        return kindOfIdAndMaterial(itemId, item.type)
    }

    /** 制御ブロックが権限制限表示かどうかを返します。 */
    fun isLockedView(itemId: String?, material: Material): Boolean =
        itemId == BLOCK_ITEM_ID && material == lockedBlockMaterial

    /** アイテムが権限制限表示の制御ブロックかどうかを返します。 */
    fun isLockedView(item: ItemStack?): Boolean {
        if (item == null) return false
        val itemId = item.itemMeta?.persistentDataContainer?.get(customItemIdKey, PersistentDataType.STRING)
        return isLockedView(itemId, item.type)
    }

    /** 権限状態に応じた制御ブロックの表示素材を返します。 */
    fun blockViewMaterial(locked: Boolean): Material =
        if (locked) lockedBlockMaterial else authorizedBlockMaterial

    /**
     * 制御ブロックの表示素材を権限状態へ寄せたアイテムを返します。PDC・名称・
     * lore・同梱内容・個数は維持し、最大stack数も1へ正規化します。素材切替で
     * 成分が既定へ戻るため、無機能アイテムの成分も共有パックで掛け直します。
     * 素材の書換は非推奨の破壊的setterを避け、コピーを返すwithTypeを使います。
     * 制御ブロック以外ではnullを返します。
     */
    fun normalizeControlBlockView(item: ItemStack, locked: Boolean): ItemStack? {
        if (kind(item) != KantanItemKind.BLOCK) return null
        val amount = item.amount.coerceAtLeast(1)
        val converted = if (item.type == blockViewMaterial(locked)) item
        else item.withType(blockViewMaterial(locked))
        converted.amount = amount
        converted.editMeta { meta -> meta.setMaxStackSize(1) }
        PoisonousPotatoComponentPack.applyNonConsumable(converted)
        return converted
    }

    fun isKantanItem(item: ItemStack?): Boolean = kind(item) != KantanItemKind.NONE

    /** プログラムディスクが参照するスクリプトUUID。制御ブロックや無関係アイテムではnull。 */
    fun diskId(item: ItemStack?): UUID? {
        if (kind(item) != KantanItemKind.DISK) return null
        val meta = item?.itemMeta ?: return null
        val raw = meta.persistentDataContainer.get(diskIdKey, PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    /** 制御ブロックアイテムにプログラムの埋め込みデータが存在するかを返します。 */
    fun hasEmbeddedProgram(item: ItemStack?): Boolean {
        if (kind(item) != KantanItemKind.BLOCK) return false
        return item?.itemMeta?.persistentDataContainer?.has(blockProgramKey, PersistentDataType.STRING) == true
    }

    /** アイテム自身へ保存されたプログラムの独立データを復元します。 */
    fun embeddedProgram(item: ItemStack?): DiskScript? {
        if (!hasEmbeddedProgram(item)) return null
        val raw = item?.itemMeta?.persistentDataContainer?.get(blockProgramKey, PersistentDataType.STRING)
            ?: return null
        return ControlBlockProgramCodec.decode(raw)
    }

    private fun updateLore(item: ItemStack, script: DiskScript, player: Player) {
        item.editMeta { meta ->
            val lore = CCSystem.getAPI().getLoreService().render(
                GuiLoreSpec.Rich(
                    programLoreLines(script, player),
                    com.awabi2048.ccsystem.api.gui.GuiLoreFrame.NONE,
                ),
            )
            meta.lore(lore)
        }
    }

    /**
     * issue #13: プログラム情報の lore 行です。所有者・作成日時を含め、
     * 一覧表示と同じ [me.awabi2048.kantancommander.gui.ProgramMetadata] の
     * 書式を使います。Bukkit 依存の名前解決だけを呼び出し側へ残し、
     * 行組み立ては単体試験できます。
     */
    fun programLoreLines(script: DiskScript, player: Player): List<GuiLoreLine> = programLoreLines(
        scriptName = script.name,
        nodeCount = script.graph.nodes.size,
        ownerName = me.awabi2048.kantancommander.gui.ProgramMetadata.ownerName(script.owner) {
            org.bukkit.Bukkit.getOfflinePlayer(it).name
        },
        createdAt = me.awabi2048.kantancommander.gui.ProgramMetadata.formatCreatedAt(script.createdAt),
        programNameLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_PROGRAM_NAME),
        commandsLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_COMMANDS),
        ownerLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_OWNER),
        createdAtLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_CREATED_AT),
    )

    /** issue #13: lore 行組み立ての純粋部分です。表示文言の対応を固定します。 */
    fun programLoreLines(
        scriptName: String,
        nodeCount: Int,
        ownerName: String,
        createdAt: String,
        programNameLabel: String,
        commandsLabel: String,
        ownerLabel: String,
        createdAtLabel: String,
    ): List<GuiLoreLine> = listOf(
        GuiLoreLine.Data(programNameLabel, scriptName, "§f"),
        GuiLoreLine.Data(commandsLabel, nodeCount, "§f"),
        GuiLoreLine.Data(ownerLabel, ownerName, "§f"),
        GuiLoreLine.Data(createdAtLabel, createdAt, "§f"),
    )
}
