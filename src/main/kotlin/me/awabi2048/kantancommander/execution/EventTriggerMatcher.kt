package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.model.EventTriggerConditions
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TriggerClickSide
import me.awabi2048.kantancommander.model.TriggerLeverState
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

/**
 * 詳細設定を、発生した対象そのものへ適用します。
 * 汎用対象指定の探索結果を別途選ぶと「クリックされた個体」以外でも発火するため、
 * ここではイベントで確定した1体を候補とし、その分類・絞り込みだけを評価します。
 */
internal object EventTriggerMatcher {
    fun matches(
        condition: EventTriggerConditions,
        actor: Player?,
        block: Block?,
        target: Entity?,
        clickSide: TriggerClickSide?,
        leverState: TriggerLeverState?,
    ): Boolean {
        if (condition.clickSide != null && condition.clickSide != clickSide) return false
        if (condition.leverState != null && condition.leverState != leverState) return false
        if (condition.actorId != null && condition.actorId != actor?.uniqueId) return false
        if (condition.actorTarget != null && (actor == null || !matchesTarget(actor, condition.actorTarget))) return false
        if (condition.entityId != null && condition.entityId != target?.uniqueId) return false
        if (condition.entityTarget != null && (target == null || !matchesTarget(target, condition.entityTarget))) return false
        if (condition.blockType != null && condition.blockType != (block?.type?.name ?: "AIR")) return false
        // AIRのevent_positionは視線先ですが、クリックされた実ブロックの個体識別ではありません。
        condition.blockIdentity?.let { identity ->
            if (block == null || identity.world != block.world.name ||
                identity.x != block.x || identity.y != block.y || identity.z != block.z
            ) return false
        }
        return true
    }

    /** 単一のイベント候補に適用可能な、汎用TargetSpecの分類と属性絞り込みです。 */
    private fun matchesTarget(entity: Entity, spec: TargetSpec): Boolean {
        when (spec.kind) {
            TargetKind.NEAREST_PLAYER, TargetKind.NEARBY_PLAYERS,
            TargetKind.ALL_PLAYERS, TargetKind.RANDOM_PLAYER -> if (entity !is Player) return false
            TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES -> Unit
            // 起動前には一時変数が定義されないため、実行時参照は条件にできません。
            TargetKind.TEMPORARY -> return false
        }
        if (spec.entityType != null && spec.entityType != entity.type.key.toString()) return false
        if (spec.name != null && spec.name != entity.name) return false
        if (spec.tag != null && spec.tag !in entity.scoreboardTags) return false
        if (spec.gameMode != null && (entity !is Player || !entity.gameMode.name.equals(spec.gameMode, true))) return false
        return true
    }
}
