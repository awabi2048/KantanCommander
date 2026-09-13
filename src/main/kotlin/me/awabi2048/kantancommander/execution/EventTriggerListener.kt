package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.EventTriggerArguments
import me.awabi2048.kantancommander.model.EventTriggerKind
import me.awabi2048.kantancommander.model.SavedLocation
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.TypedValue
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.effectiveTrigger
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.EquipmentSlot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * issue #7 のイベント起動リスナーです。
 *
 * Bukkit／Paper のイベントだけを購読し、全配置の毎tick走査は増やしません。
 * イベント発生時に、そのワールドへ登録された同一種別の起動だけを実行します。
 * 1スクリプトの起動方法は単一選択であり、複数起動方法の同時保持はしません。
 * イベント引数は #2・#8 と共通の型付き値として、実行開始時に定義済みの
 * EXECUTION 変数になります。検証層は同じ引数表を定義として扱うため、
 * 静的検証と実行時の定義状態が一致します。
 */
class EventTriggerListener(private val plugin: KantanCommanderPlugin) : Listener {
    /** チャンク単位の登録です。 chunk unload・撤去・削除時に明示解除します。 */
    private data class Registration(
        val world: String,
        val chunkX: Int,
        val chunkZ: Int,
        val x: Int,
        val y: Int,
        val z: Int,
        val placementKey: String,
        val scriptId: UUID,
        val event: EventTriggerKind,
    )

    private val index = ConcurrentHashMap<String, Registration>()

    fun start() {
        rebuild()
    }

    /**
     * 登録全体を配置台帳から作り直します。
     *
     * 配置の追加・撤去・スクリプトの保存・削除の境界から呼び出し、
     * 増分更新の食い違いを残しません。配置数は tick 監視と同じ走査で扱える
     * 規模であり、スクリプト読込はキャッシュ正本のため安価です。
     */
    fun rebuild() {
        val fresh = mutableMapOf<String, Registration>()
        plugin.placements.all().forEach { placement ->
            registrationFor(placement.world, placement.x, placement.y, placement.z, placement.scriptId)
                ?.let { fresh[placement.key] = it }
        }
        index.clear()
        index.putAll(fresh)
    }

    /** スクリプト削除時に、そのスクリプトへの登録を解除します。 */
    fun forgetScript(scriptId: UUID) {
        index.entries.removeIf { it.value.scriptId == scriptId }
    }

    private fun registrationFor(
        worldName: String,
        x: Int,
        y: Int,
        z: Int,
        scriptId: UUID,
    ): Registration? {
        val world = Bukkit.getWorld(worldName) ?: return null
        val chunkX = x shr 4
        val chunkZ = z shr 4
        // アンロード中のチャンクは登録しません。ロード時に再登録します。
        if (!world.isChunkLoaded(chunkX, chunkZ)) return null
        val script = plugin.scripts.load(scriptId) ?: return null
        val effective = script.effectiveTrigger()
        if (effective.kind != TriggerKind.EVENT || effective.event == null) return null
        return Registration(
            world = worldName,
            chunkX = chunkX,
            chunkZ = chunkZ,
            x = x,
            y = y,
            z = z,
            placementKey = "$worldName,$x,$y,$z",
            scriptId = scriptId,
            event = effective.event,
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkLoad(event: ChunkLoadEvent) {
        val world = event.world
        plugin.placements.all()
            .filter { it.world == world.name && (it.x shr 4) == event.chunk.x && (it.z shr 4) == event.chunk.z }
            .forEach { placement ->
                registrationFor(placement.world, placement.x, placement.y, placement.z, placement.scriptId)
                    ?.let { index[placement.key] = it }
            }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val world = event.world.name
        val chunkX = event.chunk.x
        val chunkZ = event.chunk.z
        index.entries.removeIf { it.value.world == world && it.value.chunkX == chunkX && it.value.chunkZ == chunkZ }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        fire(
            EventTriggerKind.BLOCK_BREAK,
            event.block.world,
            actor = event.player,
            arguments = blockArguments(event.block, event.player),
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        fire(
            EventTriggerKind.BLOCK_PLACE,
            event.blockPlaced.world,
            actor = event.player,
            arguments = blockArguments(event.blockPlaced, event.player),
        )
    }

    /**
     * ボタン・レバー・感圧板のオン／オフを信号変化で検出します。
     *
     * 起動設定で選ばれた種別の変化辺だけを実行します。感圧板は踏まれた時だけを
     * 提供し、離した時は実行しません。信号変化の観測であり操作者を特定できないため、
     * 操作者（actor）は持ちません。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRedstoneChange(event: BlockRedstoneEvent) {
        val category = EventTriggerClassification.redstoneCategory(
            event.block.blockData,
            event.block.type.name,
        ) ?: return
        val was = event.oldCurrent > 0
        val now = event.newCurrent > 0
        if (was == now) return
        val kind = EventTriggerClassification.eventFor(category, now) ?: return
        fire(
            kind,
            event.block.world,
            actor = null,
            arguments = blockArguments(event.block),
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityInteract(event: PlayerInteractEntityEvent) {
        fire(
            EventTriggerKind.ENTITY_RIGHT_CLICK,
            event.player.world,
            actor = event.player,
            arguments = mapOf(
                EventTriggerArguments.ACTOR to TypedValue(ValueType.ENTITY, entityId = event.player.uniqueId),
                EventTriggerArguments.TARGET to TypedValue(ValueType.ENTITY, entityId = event.rightClicked.uniqueId),
            ),
        )
    }

    /**
     * エンティティへの左クリック（攻撃）を検出します。
     *
     * ダメージ自体はキャンセルせず観測だけ行います。実行可否の確定後に
     * キャンセルされた攻撃では起動しないよう、キャンセル済みは無視します。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val damager = event.damager as? Player ?: return
        fire(
            EventTriggerKind.ENTITY_LEFT_CLICK,
            damager.world,
            actor = damager,
            arguments = mapOf(
                EventTriggerArguments.ACTOR to TypedValue(ValueType.ENTITY, entityId = damager.uniqueId),
                EventTriggerArguments.TARGET to TypedValue(ValueType.ENTITY, entityId = event.entity.uniqueId),
            ),
        )
    }

    /**
     * ブロックへの左右クリックを検出します。
     *
     * 両手で二重発火しないようメインハンドだけを採用します。制御ブロックへの
     * 操作は編集画面の入口であり、利用者スクリプトの起動対象にしません。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val kind = when (event.action) {
            Action.LEFT_CLICK_BLOCK -> EventTriggerKind.BLOCK_LEFT_CLICK
            Action.RIGHT_CLICK_BLOCK -> EventTriggerKind.BLOCK_RIGHT_CLICK
            else -> return
        }
        val block = event.clickedBlock ?: return
        if (plugin.placements.isRegistered(block.world, block.x, block.y, block.z)) return
        fire(
            kind,
            block.world,
            actor = event.player,
            arguments = blockArguments(block, event.player),
        )
    }

    /** ブロック系イベントの共通引数（対象・位置、および操作者がいる場合の操作者）です。 */
    private fun blockArguments(block: Block, actor: Player? = null): Map<String, TypedValue> {
        val center = block.location.add(0.5, 0.5, 0.5)
        return buildMap {
            actor?.let { put(EventTriggerArguments.ACTOR, TypedValue(ValueType.ENTITY, entityId = it.uniqueId)) }
            put(EventTriggerArguments.BLOCK, TypedValue(ValueType.BLOCK, block = block.type.name))
            put(
                EventTriggerArguments.POSITION,
                TypedValue(ValueType.LOCATION, location = SavedLocation(center.x, center.y, center.z, 0f, 0f)),
            )
        }
    }

    /**
     * 同一ワールドへ登録された同一種別の起動を、配置単位で実行します。
     *
     * 登録はチャンク単位で維持し、アンロード・撤去・削除・起動変更の stale は
     * 発火時に検出して登録から外します。テスト実行中は通常実行へ入れず、
     * 実行開始前の例外でも排他トークンを必ず解放します。
     */
    private fun fire(
        event: EventTriggerKind,
        world: World,
        actor: Player?,
        arguments: Map<String, TypedValue>,
    ) {
        // 引数契約との不一致は未定義のまま実行せず、起動しません。
        // 検証層と同じ表で確認するため、静的検証を通過した引数だけが実行されます。
        val required = EventTriggerArguments.argumentsFor(event).map { it.name }.toSet()
        if (!arguments.keys.containsAll(required)) {
            plugin.logger.warning("[KantanCommander] event arguments are incomplete: event=${event.name}")
            return
        }
        index.values
            .filter { it.event == event && it.world == world.name }
            .forEach { registration ->
                if (!world.isChunkLoaded(registration.chunkX, registration.chunkZ)) {
                    index.remove(registration.placementKey)
                    return@forEach
                }
                if (!plugin.placements.isRegistered(world, registration.x, registration.y, registration.z)) {
                    index.remove(registration.placementKey)
                    return@forEach
                }
                val script = plugin.scripts.load(registration.scriptId) ?: run {
                    index.remove(registration.placementKey)
                    return@forEach
                }
                val effective = script.effectiveTrigger()
                if (effective.kind != TriggerKind.EVENT || effective.event != event) {
                    index.remove(registration.placementKey)
                    return@forEach
                }
                val origin = Location(world, registration.x + 0.5, registration.y + 0.5, registration.z + 0.5)
                val normalExecution = plugin.testExecution.beginNormal(registration.placementKey) ?: return@forEach
                runCatching {
                    plugin.executor.execute(script.id, origin, actor, arguments) {
                        normalExecution.close()
                    }
                }.onFailure { failure ->
                    normalExecution.close()
                    plugin.logger.log(
                        java.util.logging.Level.WARNING,
                        "イベント起動の開始に失敗しました: placement=${registration.placementKey} event=${event.name}",
                        failure,
                    )
                }
            }
    }
}
