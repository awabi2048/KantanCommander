package me.awabi2048.kantancommander.gui.setting

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.gui.CommandSettingContext
import me.awabi2048.kantancommander.gui.CommandSettingsModel
import me.awabi2048.kantancommander.gui.GestureSettingTreeNode
import me.awabi2048.kantancommander.gui.TargetCategory
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.entity.Player

/**
 * 対象・単一実体参照の汎用プロバイダです。
 *
 * コマンド固有の対象選択（テレポート対象・移動先実体・向き参照先など）を個別実装せず、
 * すべてこのプロバイダへ委譲します。位置の「エンティティ位置を参照」も対象設定へ帰着し、
 * 向きの注視・複写も単一実体参照としてここから生成します。
 *
 * 単体テスト容易性のため、文言解決は呼び出し側から注入できます。本番はKcI18nを使います。
 */
object ReferenceSettingProviders {
    /**
     * 汎用対象分類（プレイヤー／プレイヤー以外／一時変数）を返します。
     *
     * 詳細条件を持つ分類には子として絞り込み候補をぶら下げ、二段階フローの
     * `hasChildren` 判定を共通化します。
     *
     * @param singleOnly 単一体参照として扱い、上限数・一時変数を除外します。
     * @param text 文言解決です。既定はKcI18nによるプレイヤーロケール解決です。
     */
    fun targetCategories(
        node: CommandNode,
        context: CommandSettingContext,
        player: Player,
        includeTemporary: Boolean = true,
        singleOnly: Boolean = false,
        text: (LocalizationKey<String>) -> String = { KcI18n.text(player, it) },
    ): List<GestureSettingTreeNode> {
        val current = CommandSettingsModel.targetSpec(node, context.role)?.kind
        val categories = buildList {
            add(TargetCategory.PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER)
            add(TargetCategory.NON_PLAYER_ENTITY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_ENTITY)
            if (includeTemporary && !singleOnly) {
                add(TargetCategory.TEMPORARY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE)
            }
        }
        return categories.map { (category, label) ->
            val selected = CommandSettingsModel.targetCategoryMatches(current, category) &&
                CommandSettingsModel.isFieldConfigured(node, "target", context.role)
            val effectiveKind = if (selected) current else CommandSettingsModel.defaultTargetKind(category)
            GestureSettingTreeNode(
                id = "target:${category.name}",
                label = text(label),
                selected = selected,
                children = if (CommandSettingsModel.targetSupportsDetailedFilters(effectiveKind)) {
                    targetFilters(node, context, player, kindOverride = effectiveKind, singleOnly = singleOnly, text = text)
                } else emptyList(),
            )
        }
    }

    /**
     * 単一実体参照の分類を返します。向き注視・向き複写など単一体のみ許す設定が使います。
     *
     * 一時変数・複数解決を除外し、プレイヤー／プレイヤー以外だけを提示します。
     * 各分類の詳細条件は汎用対象と同じ規則で付与し、二段階目の詳細画面へ進めます。
     */
    fun singleEntityCategories(
        node: CommandNode,
        context: CommandSettingContext,
        player: Player,
        text: (LocalizationKey<String>) -> String = { KcI18n.text(player, it) },
    ): List<GestureSettingTreeNode> =
        targetCategories(node, context, player, includeTemporary = false, singleOnly = true, text = text)

    /**
     * 汎用対象詳細条件を返します。種類・距離・範囲・上限・並べ替え等は対象種別に応じて絞ります。
     *
     * @param singleOnly 単一体参照として上限数選択を除外します。
     */
    fun targetFilters(
        node: CommandNode,
        context: CommandSettingContext,
        player: Player,
        kindOverride: TargetKind? = null,
        singleOnly: Boolean = false,
        text: (LocalizationKey<String>) -> String = { KcI18n.text(player, it) },
    ): List<GestureSettingTreeNode> {
        val currentSpec = CommandSettingsModel.targetSpec(node, context.role)
            ?: TargetSpec(kindOverride ?: TargetKind.NEAREST_ENTITY)
        val spec = kindOverride?.let { currentSpec.copy(kind = it) } ?: currentSpec
        fun value(parameter: String): String? = when (parameter) {
            "sort" -> text(
                when (spec.sort) {
                    TargetSort.NEAREST -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SORT_NEAREST
                    TargetSort.FURTHEST -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SORT_FURTHEST
                    TargetSort.RANDOM -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SORT_RANDOM
                },
            )
            else -> when (parameter) {
                "gameMode" -> spec.gameMode
                "entityType" -> spec.entityType
                "distance" -> formatDistance(spec.minimumDistance, spec.maximumDistance)
                "range" -> formatRange(spec.dx, spec.dy, spec.dz, text)
                "limit" -> spec.limit?.toString()
                "tag" -> spec.tag
                else -> spec.name
            }
        }
        return listOf(
            "entityType" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_TYPE,
            "distance" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE,
            "range" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_RANGE,
            "limit" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LIMIT,
            "sort" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SORT,
            "gameMode" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GAME_MODE,
            "tag" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG,
            "name" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_NAME,
        )
            .filter { (id, _) -> !singleOnly || id != "limit" }
            .filter { (id, _) -> CommandSettingsModel.targetFilterApplies(spec.kind, id) }
            .map { (id, label) ->
                GestureSettingTreeNode(
                    id = "filter:$id",
                    label = "${text(label)} ${value(id) ?: text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)}",
                )
            }
    }

    /**
     * 単一体参照の完全性を返します。向き注視・複写の完了判定が使います。
     *
     * 固定実体はID必須、一時変数は変数名必須、それ以外は存在のみで完了とします。
     */
    fun isSingleEntityConfigured(spec: TargetSpec?): Boolean {
        if (spec == null) return false
        if ((spec.limit ?: 1) != 1) return false
        return when (spec.kind) {
            TargetKind.FIXED_ENTITY -> spec.fixedEntityId != null
            TargetKind.TEMPORARY -> false
            else -> true
        }
    }

    /**
     * 単一体参照へ正規化した複製を返します。複数解決の混入を汎用層で防ぎます。
     */
    fun asSingleEntity(spec: TargetSpec): TargetSpec =
        spec.copy(limit = 1)

    private fun formatDistance(minimum: Double?, maximum: Double?): String? {
        if (minimum == null && maximum == null) return null
        fun format(value: Double?): String = value?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        }.orEmpty()
        return "${format(minimum)}..${format(maximum)}"
    }

    private fun formatRange(dx: Double?, dy: Double?, dz: Double?, text: (LocalizationKey<String>) -> String): String? {
        if (dx == null && dy == null && dz == null) return null
        fun format(value: Double?): String = value?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        }.orEmpty()
        return listOf(
            "${text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX)}=${format(dx).ifBlank { text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }}",
            "${text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DY)}=${format(dy).ifBlank { text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }}",
            "${text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DZ)}=${format(dz).ifBlank { text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }}",
        ).joinToString(" / ")
    }
}
