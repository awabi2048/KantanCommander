package me.awabi2048.kantancommander.operationtool

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * issue #14の操作ツールwand用の、視線先Entityのタグ一覧表示です。
 *
 * wandをメインハンドに持ち、タグを持つ操作可能Entityを見ている間だけ、
 * EntityのBounding Box上端より上へText Displayを出し、タグを1行ずつ表示します。
 * 表示は操作者だけに見せ、他プレイヤーからは `hideEntity` で隠します。
 * 表示Entityは選択操作の候補から外れるよう [isTagDisplay] で識別します。
 */
class OperationToolTagDisplay(
    private val plugin: JavaPlugin,
    /** wand所持チェックです。リスナーの判定を複製せず共有します。 */
    private val holdsWand: (Player) -> Boolean,
    /** 表示対象として扱うEntityの判定です。ツール表示・システム実体・プレイヤーは呼び出し側で除外済みです。 */
    private val isViewable: (Player, Entity) -> Boolean,
    /** タグ数上限超過時の「他{count}件」行です。言語解決は呼び出し側へ委譲します。 */
    private val moreLine: (Player, Int) -> String,
) {
    private data class Shown(
        val targetId: UUID,
        val display: TextDisplay,
        val lines: List<String>,
    )

    private val shown = ConcurrentHashMap<UUID, Shown>()
    private val trackedIds = ConcurrentHashMap.newKeySet<UUID>()
    private var task: BukkitTask? = null

    /** 管理中のタグ表示かどうかを返します。選択候補・視線判定から除外するために使います。 */
    fun isTagDisplay(entityId: UUID): Boolean = trackedIds.contains(entityId)

    /** 追従タスクを開始します。リスナーのstartから呼び出します。 */
    fun start() {
        if (task != null) return
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, 2L, 1L)
    }

    /** 追従タスクを停止し、全表示を破棄します。プラグイン無効化時に呼び出します。 */
    fun stop() {
        task?.cancel()
        task = null
        shown.keys.toList().forEach(::clear)
    }

    /** プレイヤー分の表示を破棄します。 */
    fun clear(playerId: UUID) {
        val removed = shown.remove(playerId) ?: return
        trackedIds.remove(removed.display.uniqueId)
        runCatching { removed.display.remove() }
    }

    /** 参加したプレイヤーから他者のタグ表示を隠します。 */
    fun hideFrom(joinerId: UUID) {
        val joiner = plugin.server.getPlayer(joinerId) ?: return
        shown.values.forEach { runCatching { joiner.hideEntity(plugin, it.display) } }
    }

    private fun tick() {
        for (player in plugin.server.onlinePlayers) {
            if (!holdsWand(player)) {
                clear(player.uniqueId)
                continue
            }
            val target = lookTarget(player)
            if (target == null || target.scoreboardTags.isEmpty()) {
                clear(player.uniqueId)
                continue
            }
            upsert(player, target)
        }
    }

    /**
     * 視線先の操作可能Entityを返します。
     *
     * タグ表示そのものやツール範囲表示が視線に被っても対象へ置き換えないよう、
     * 表示Entityは除外して扱います。
     */
    private fun lookTarget(player: Player): Entity? {
        val eye = player.eyeLocation
        var distance = 0.0
        // 表示が対象の手前に割り込んだ場合を考慮し、有限回数でrayを延長します。
        repeat(4) {
            val result = player.world.rayTraceEntities(
                eye.clone().add(eye.direction.clone().multiply(distance)),
                eye.direction,
                LOOK_RANGE - distance,
            ) ?: return null
            val entity = result.hitEntity ?: return null
            if (isTagDisplay(entity.uniqueId)) {
                distance = result.hitPosition.distance(eye.toVector()) + 0.05
                return@repeat
            }
            return entity.takeIf { isViewable(player, it) }
        }
        return null
    }

    private fun upsert(player: Player, target: Entity) {
        val lines = tagLines(player, target)
        val anchor = anchorOf(target, lines.size)
        val current = shown[player.uniqueId]
        if (current != null && current.targetId == target.uniqueId && current.lines == lines &&
            current.display.isValid && current.display.world.uid == target.world.uid
        ) {
            runCatching { current.display.teleport(anchor) }
            return
        }
        clear(player.uniqueId)
        val display = runCatching {
            target.world.spawn(anchor, TextDisplay::class.java) { spawned ->
                spawned.text(Component.text(lines.joinToString("\n")))
                spawned.billboard = org.bukkit.entity.Display.Billboard.CENTER
                spawned.alignment = TextDisplay.TextAlignment.CENTER
                spawned.teleportDuration = 2
                spawned.displayWidth = TAG_CULL_SIZE
                spawned.displayHeight = TAG_CULL_SIZE
                spawned.isPersistent = false
            }
        }.getOrElse {
            plugin.logger.warning("操作ツールのタグ表示を生成できませんでした: player=${player.uniqueId}")
            return
        }
        trackedIds.add(display.uniqueId)
        plugin.server.onlinePlayers
            .filter { it.uniqueId != player.uniqueId }
            .forEach { other -> runCatching { other.hideEntity(plugin, display) } }
        shown[player.uniqueId] = Shown(target.uniqueId, display, lines)
    }

    private fun tagLines(player: Player, entity: Entity): List<String> {
        val tags = entity.scoreboardTags.sorted()
        if (tags.size <= MAX_TAG_LINES) return tags
        return tags.take(MAX_TAG_LINES) + moreLine(player, tags.size - MAX_TAG_LINES)
    }

    /**
     * タグ表示の原点を返します。
     *
     * Text Displayのテキストは原点を中心に上下へ広がるため、
     * 行数の半分+余白だけEntityの上端より上へ置き、Entityへ被らないようにします。
     */
    private fun anchorOf(entity: Entity, lineCount: Int): Location {
        val box = entity.boundingBox
        val x = (box.minX + box.maxX) / 2.0
        val z = (box.minZ + box.maxZ) / 2.0
        val y = box.maxY + LIFT_MARGIN + lineCount * LINE_HALF_HEIGHT
        return Location(entity.world, x, y, z)
    }

    companion object {
        /** タグ一覧を表示する視線の最大距離です。 */
        const val LOOK_RANGE = 16.0

        /** 表示するタグの最大行数です。超過分は「他{count}件」で畳みます。 */
        const val MAX_TAG_LINES = 8

        /** Entity上端からテキスト下端までの余白です。 */
        const val LIFT_MARGIN = 0.35

        /** 1行あたりのテキスト高さの半分です。 */
        const val LINE_HALF_HEIGHT = 0.16

        /** テキスト表示のカリング判定サイズです。小さすぎると視界端で消えるため余裕を持たせます。 */
        const val TAG_CULL_SIZE = 32.0f
    }
}
