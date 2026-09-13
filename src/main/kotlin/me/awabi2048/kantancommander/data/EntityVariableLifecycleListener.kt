package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.KantanCommanderPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityBreedEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTransformEvent

/**
 * issue #22 のEntity変数ライフサイクルです。
 *
 * - 死亡時: Entityに付随する変数をリセットします。プレイヤーもMobと同様に
 *   死亡時にリセットします（PDCは死亡後も残るため、明示的に破棄します）。
 *   ワールド削除ではない除去（remove等）は実体ごと消えるため処理しません。
 * - 繁殖時: 母親の変数を子供へそのまま複製します。
 * - 変化時: 変化元の変数を変化先の各実体へそのまま複製します。
 *
 * chunk unload／load・再起動・ワールド間移動ではPDCが実体に付随するため、
 * 維持されます。追加処理はしません。
 */
class EntityVariableLifecycleListener(
    private val plugin: KantanCommanderPlugin,
) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        runCatching {
            plugin.entityVariables.clear(event.entity)
        }.onFailure { failure ->
            plugin.logger.warning(
                "エンティティ変数を死亡時に破棄できませんでした: ${event.entity.uniqueId} $failure",
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityBreed(event: EntityBreedEvent) {
        runCatching {
            plugin.entityVariables.copyTo(event.mother, event.entity)
        }.onFailure { failure ->
            plugin.logger.warning(
                "エンティティ変数を繁殖時に複製できませんでした: ${event.entity.uniqueId} $failure",
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityTransform(event: EntityTransformEvent) {
        runCatching {
            applyEntityTransformCopy(plugin.entityVariables, event)
        }.onFailure { failure ->
            plugin.logger.warning(
                "エンティティ変数を変化時に複製できませんでした: ${event.entity.uniqueId} $failure",
            )
        }
    }
}

/**
 * issue #22 の変化時複製を実行します。
 *
 * Paper API の `EntityTransformEvent` は、`entity` が変化元（変換される実体）、
 * `transformedEntities` が変化先（変換後の実体一覧）です。`transformedEntity` は
 * `transformedEntities` の先頭を返すだけであり、変化元ではありません。
 * 複製元には必ず変化元 [`EntityTransformEvent.getEntity`] を使います。
 * 変化先へ値が自動継承されない経路でも、元の変数一式を各変化先へ引き継ぎます。
 * plugin に依存しないため単体試験できます。
 */
internal fun applyEntityTransformCopy(
    store: me.awabi2048.kantancommander.model.EntityVariableStore,
    event: EntityTransformEvent,
) {
    event.transformedEntities.forEach { transformed ->
        store.copyTo(event.entity, transformed)
    }
}
