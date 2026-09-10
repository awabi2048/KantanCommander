package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.MyworldSpawnKind
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec

/**
 * issue #29 の対象・位置・向きプリセットの共有定義です。
 *
 * Inventory GUIとGesture GUIで同じ設定モデル・同じプリセット定義を利用します。
 * プリセット選択時は以前の詳細フィルター等を引き継がず、プリセットが表す設定へ
 * 正規化します。詳細設定によってcanonicalな内容から外れた場合は、そのプリセットを
 * 選択済み表示にしません。
 */
object SettingPresets {
    /** 「付近」系プリセットの初回最大距離です。再クリックで距離を変更できます。 */
    const val NEARBY_DISTANCE_DEFAULT = 5.0

    /** 頻用する対象設定のプリセットです。下部の汎用導線は含みません。 */
    enum class TargetPreset {
        NEARBY_PLAYERS,
        MYWORLD_PLAYERS,
        NEAREST_PLAYER,
        NEARBY_ENTITIES,
    }

    /** 頻用する位置設定のプリセットです。下部の汎用導線は含みません。 */
    enum class PositionPreset {
        CONTROL_BLOCK,
        NEAREST_PLAYER,
        NEARBY_PLAYERS,
        MYWORLD_SPAWN,
        COORDINATES,
    }

    /** 頻用する向き設定のプリセットです。「現在の向き」は新規に含みません。 */
    enum class FacingPreset {
        NEAREST_PLAYER,
        MYWORLD_SPAWN,
        COORDINATES,
        NUMERIC,
    }

    /** 対象プリセットの正規仕様を返します。付近系は初回距離5で設定します。 */
    fun targetSpecFor(preset: TargetPreset): TargetSpec = when (preset) {
        TargetPreset.NEARBY_PLAYERS ->
            TargetSpec(TargetKind.NEARBY_PLAYERS, maximumDistance = NEARBY_DISTANCE_DEFAULT)
        TargetPreset.MYWORLD_PLAYERS -> TargetSpec(TargetKind.ALL_PLAYERS)
        TargetPreset.NEAREST_PLAYER -> TargetSpec(TargetKind.NEAREST_PLAYER)
        TargetPreset.NEARBY_ENTITIES ->
            TargetSpec(TargetKind.NEARBY_ENTITIES, maximumDistance = NEARBY_DISTANCE_DEFAULT)
    }

    /**
     * 対象設定がプリセットの正規内容と一致する場合だけプリセットを返します。
     *
     * 詳細フィルター等で正規内容から外れた場合はnullを返し、選択済み表示にしません。
     */
    fun matchTargetPreset(spec: TargetSpec?): TargetPreset? {
        if (spec == null) return null
        return when (spec.kind) {
            TargetKind.NEARBY_PLAYERS -> TargetPreset.NEARBY_PLAYERS.takeIf { isNearbyPresetShape(spec) }
            TargetKind.ALL_PLAYERS -> TargetPreset.MYWORLD_PLAYERS.takeIf { isBareShape(spec) }
            TargetKind.NEAREST_PLAYER -> TargetPreset.NEAREST_PLAYER.takeIf { isBareShape(spec) }
            TargetKind.NEARBY_ENTITIES -> TargetPreset.NEARBY_ENTITIES.takeIf { isNearbyPresetShape(spec) }
            else -> null
        }
    }

    /** 付近系プリセットの形状です。距離以外の絞り込みがあれば正規内容から外れます。 */
    private fun isNearbyPresetShape(spec: TargetSpec): Boolean =
        spec.entityType == null && spec.minimumDistance == null &&
            spec.limit == null && spec.gameMode == null && spec.tag == null &&
            spec.name == null && spec.dx == null && spec.dy == null && spec.dz == null &&
            spec.fixedEntityId == null && spec.searchOrigin == null && spec.tempName == null

    /** 絞り込みなしの形状です。何らかの条件があれば正規内容から外れます。 */
    private fun isBareShape(spec: TargetSpec): Boolean =
        spec.entityType == null && spec.minimumDistance == null && spec.maximumDistance == null &&
            spec.limit == null && spec.gameMode == null && spec.tag == null &&
            spec.name == null && spec.dx == null && spec.dy == null && spec.dz == null &&
            spec.fixedEntityId == null && spec.searchOrigin == null && spec.tempName == null

    /** 位置プリセットの正規仕様を返します。初回選択時は実用的な既定値です。 */
    fun positionSpecFor(preset: PositionPreset): PositionSpec = when (preset) {
        PositionPreset.CONTROL_BLOCK -> PositionSpec(PositionKind.DISK)
        PositionPreset.NEAREST_PLAYER -> PositionSpec(
            PositionKind.TARGET,
            target = TargetSpec(TargetKind.NEAREST_PLAYER),
        )
        PositionPreset.NEARBY_PLAYERS -> PositionSpec(
            PositionKind.TARGET,
            target = TargetSpec(TargetKind.NEARBY_PLAYERS, maximumDistance = NEARBY_DISTANCE_DEFAULT),
        )
        // 初回選択時はメンバー用スポーンを設定します。
        PositionPreset.MYWORLD_SPAWN ->
            PositionSpec(PositionKind.MYWORLD_SPAWN, myworldSpawn = MyworldSpawnKind.MEMBER)
        PositionPreset.COORDINATES -> PositionSpec(PositionKind.COORDINATES)
    }

    /**
     * 位置設定がプリセットの正規内容と一致する場合だけプリセットを返します。
     *
     * 詳細設定で正規内容から外れた場合はnullを返します。旧来の単一MYWORLD_SPAWN
     * 相当（種別未設定）はメンバー用スポーンとして互換移行します。
     */
    fun matchPositionPreset(spec: PositionSpec?): PositionPreset? {
        if (spec == null) return null
        return when (spec.kind) {
            PositionKind.DISK -> PositionPreset.CONTROL_BLOCK.takeIf { spec.target == null }
            PositionKind.TARGET -> when (matchTargetPreset(spec.target)) {
                TargetPreset.NEAREST_PLAYER -> PositionPreset.NEAREST_PLAYER
                TargetPreset.NEARBY_PLAYERS -> PositionPreset.NEARBY_PLAYERS
                else -> null
            }
            PositionKind.MYWORLD_SPAWN -> {
                val spawn = spec.myworldSpawn ?: MyworldSpawnKind.MEMBER
                PositionPreset.MYWORLD_SPAWN.takeIf { spawn == MyworldSpawnKind.MEMBER }
            }
            PositionKind.COORDINATES -> PositionPreset.COORDINATES.takeIf {
                spec.target == null && spec.tempName == null
            }
            else -> null
        }
    }

    /** 向きプリセットの正規仕様を返します。初回選択時は実用的な既定値です。 */
    fun facingSpecFor(preset: FacingPreset): FacingSpec = when (preset) {
        FacingPreset.NEAREST_PLAYER -> FacingSpec(
            FacingKind.TARGET,
            target = TargetSpec(TargetKind.NEAREST_PLAYER),
        )
        FacingPreset.MYWORLD_SPAWN ->
            FacingSpec(FacingKind.MYWORLD_SPAWN, myworldSpawn = MyworldSpawnKind.MEMBER)
        FacingPreset.COORDINATES -> FacingSpec(FacingKind.COORDINATES)
        FacingPreset.NUMERIC -> FacingSpec(FacingKind.ROTATION)
    }

    /**
     * 向き設定がプリセットの正規内容と一致する場合だけプリセットを返します。
     *
     * 既存の「現在の向き」（CAPTURED）は内部表現として残しますが、新規プリセットの
     * 選択済み表示にはしません。
     */
    fun matchFacingPreset(spec: FacingSpec?): FacingPreset? {
        if (spec == null) return null
        return when (spec.kind) {
            FacingKind.TARGET -> FacingPreset.NEAREST_PLAYER.takeIf {
                matchTargetPreset(spec.target) == TargetPreset.NEAREST_PLAYER
            }
            FacingKind.MYWORLD_SPAWN -> {
                val spawn = spec.myworldSpawn ?: MyworldSpawnKind.MEMBER
                FacingPreset.MYWORLD_SPAWN.takeIf { spawn == MyworldSpawnKind.MEMBER }
            }
            FacingKind.COORDINATES -> FacingPreset.COORDINATES
            FacingKind.ROTATION -> FacingPreset.NUMERIC
            else -> null
        }
    }

    /** 旧来の単一MYWORLD_SPAWN相当をメンバー用として解決します。 */
    fun myworldSpawnOrMember(kind: MyworldSpawnKind?): MyworldSpawnKind = kind ?: MyworldSpawnKind.MEMBER
}

/**
 * issue #29 の位置の複数対応可否です。
 *
 * 選択可否はGUIごとに個別実装せず、この共通ポリシーで判定します。
 * 複数位置を扱えない項目で対象由来位置が選ばれた場合、実行時・出力時ともに
 * 同じ制約を適用し、出力時だけ異なる意味になりません。
 */
enum class PositionMultiplicity {
    /** 各位置へ実行・ANY評価します。 */
    MULTI,

    /** 単一位置に限定します。対象由来位置は無効化し、理由を表示します。 */
    SINGLE,
}

object MultiPositionPolicy {
    /**
     * 設定役割ごとの複数位置対応可否を返します。
     *
     * - 複数許可：単一配置・サウンド・POINTパーティクル・召喚・花火・条件判定・実行位置
     * - 単一限定：テレポート先・fill始点終点・図形の対応位置・LOCATION一時変数そのもの
     */
    fun multiplicityFor(role: CommandSettingRole?): PositionMultiplicity = when (role) {
        CommandSettingRole.DESTINATION,
        CommandSettingRole.BLOCK_FROM,
        CommandSettingRole.BLOCK_TO,
        CommandSettingRole.PARTICLE_END,
        CommandSettingRole.TEMPORARY_LOCATION_POSITION,
        -> PositionMultiplicity.SINGLE
        else -> PositionMultiplicity.MULTI
    }

    /**
     * 単一限定の役割で対象由来位置が選ばれているかを返します。
     *
     * 単一限定ではGUIで無効化し、検証・出力で明示エラーとします。
     * 取得元ではなく件数で判定し、静的0..1の指定は許可します。
     */
    fun isUnsupportedMulti(spec: PositionSpec?, role: CommandSettingRole?): Boolean {
        if (spec == null || multiplicityFor(role) != PositionMultiplicity.SINGLE) return false
        if (spec.kind != PositionKind.TARGET) return false
        val target = spec.target ?: return true
        return target.isMultiCapable()
    }
}
