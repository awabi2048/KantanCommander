package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.MyworldSpawnKind
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
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

    /** 頻用する向き設定のプリセットです。スポーン向き・現在の向きは新規に含みません。 */
    enum class FacingPreset {
        NEAREST_PLAYER,
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

    /**
     * 付近系プリセットの形状です。距離以外の絞り込みがあれば正規内容から外れます。
     *
     * 並び順の変更も詳細設定のため、既定の最寄り順以外は一致させません。
     * 最大距離だけはプリセットの既定値として持ち、変更後も再クリックで
     * 距離編集へ戻すため形状に含めます。
     * 詳細設定入室時の変換で指定した距離下限0は、来歴付きの場合だけ許容し、
     * 手入力の等価値はプリセット扱いにしません。
     */
    private fun isNearbyPresetShape(spec: TargetSpec): Boolean =
        spec.entityType == null && (spec.minimumDistance == null ||
            (spec.minimumDistance == 0.0 && spec.convertedFromPreset)) &&
            spec.limit == null && spec.sort == TargetSort.NEAREST &&
            spec.gameMode == null && spec.tag == null &&
            spec.name == null && spec.dx == null && spec.dy == null && spec.dz == null &&
            spec.searchOrigin == null && spec.tempName == null &&
            spec.region?.hasAnySetting() != true

    /**
     * 絞り込みなしの形状です。何らかの条件があれば正規内容から外れます。
     *
     * 並び順の変更も詳細設定のため、既定の最寄り順以外は一致させません。
     * 詳細設定入室時の変換で指定した上限数1・距離下限0は、来歴付きの
     * 場合だけ許容し、手入力の等価値はプリセット扱いにしません。
     */
    private fun isBareShape(spec: TargetSpec): Boolean =
        spec.entityType == null && (spec.minimumDistance == null ||
            (spec.minimumDistance == 0.0 && spec.convertedFromPreset)) &&
            spec.maximumDistance == null &&
            (spec.limit == null || (spec.limit == 1 && spec.convertedFromPreset)) &&
            spec.sort == TargetSort.NEAREST &&
            spec.gameMode == null && spec.tag == null &&
            spec.name == null && spec.dx == null && spec.dy == null && spec.dz == null &&
            spec.searchOrigin == null && spec.tempName == null &&
            spec.region?.hasAnySetting() != true

    /**
     * プリセット相当の明示値を詳細初期値として指定します。
     *
     * 新規の詳細設定入室では引き継ぎを行いません。既存の変換済みデータの
     * 表示互換のために残し、無変更の間はプリセット選択表示を維持します。
     * 等価性（セレクタ既定補完・件数判定・実行照合）は保たれるため、
     * 実行・出力の意味は変わりません。対象外の指定はそのまま返します。
     * 冪等であり、変換済みへの再適用で値は変化しません。
     */
    fun expandPresetToDetail(spec: TargetSpec): TargetSpec = when (matchTargetPreset(spec)) {
        TargetPreset.NEAREST_PLAYER ->
            spec.copy(limit = 1, minimumDistance = 0.0, convertedFromPreset = true)
        TargetPreset.NEARBY_PLAYERS, TargetPreset.MYWORLD_PLAYERS, TargetPreset.NEARBY_ENTITIES ->
            spec.copy(minimumDistance = 0.0, convertedFromPreset = true)
        null -> spec
    }

    /** 詳細画面の保存で来歴を消し、手入力の等価値をプリセット扱いにしません。 */
    fun withoutPresetConversion(spec: TargetSpec): TargetSpec =
        spec.copy(convertedFromPreset = false)

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
     * 詳細設定で正規内容から外れた場合はnullを返します。MyWorldスポーンは
     * メンバー用・ゲスト用のどちらの種別でもプリセットに一致させ、子画面で
     * 種別を切り替えます。旧来の単一MYWORLD_SPAWN相当（種別未設定）は
     * メンバー用スポーンとして互換移行します。
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
            PositionKind.MYWORLD_SPAWN -> PositionPreset.MYWORLD_SPAWN
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
        FacingPreset.COORDINATES -> FacingSpec(FacingKind.COORDINATES)
        FacingPreset.NUMERIC -> FacingSpec(FacingKind.ROTATION)
    }

    /**
     * 向き設定がプリセットの正規内容と一致する場合だけプリセットを返します。
     *
     * 複写・一時変数・詳細条件付きの注視はプリセットに一致させず、下段の汎用導線で扱います。
     */
    fun matchFacingPreset(spec: FacingSpec?): FacingPreset? {
        if (spec == null) return null
        return when (spec.kind) {
            FacingKind.TARGET -> FacingPreset.NEAREST_PLAYER.takeIf {
                matchTargetPreset(spec.target) == TargetPreset.NEAREST_PLAYER
            }
            FacingKind.COORDINATES -> FacingPreset.COORDINATES
            FacingKind.ROTATION -> FacingPreset.NUMERIC
            else -> null
        }
    }

    /** 旧来の単一MYWORLD_SPAWN相当をメンバー用として解決します。 */
    fun myworldSpawnOrMember(kind: MyworldSpawnKind?): MyworldSpawnKind = kind ?: MyworldSpawnKind.MEMBER

    /**
     * issue #29: テレポート先のプリセット照合です。
     *
     * 移動先は対象指定と位置指定の二重保持のため、対象側の最寄り・付近だけを
     * プリセットとして照合し、それ以外は位置側の照合へ委ねます。
     * 詳細な対象指定はエンティティ導線の選択表示に任せます。
     */
    fun matchDestinationPreset(target: TargetSpec?, position: PositionSpec?): PositionPreset? {
        target?.let { matchTargetPreset(it)?.let { matched ->
            return when (matched) {
                TargetPreset.NEAREST_PLAYER -> PositionPreset.NEAREST_PLAYER
                TargetPreset.NEARBY_PLAYERS -> PositionPreset.NEARBY_PLAYERS
                else -> null
            }
        } }
        return matchPositionPreset(position)
    }
}

/**
 * プリセット選択表示の規則です。
 *
 * 形状一致だけでは詳細設定入室後もプリセットが選択表示のまま残るため、
 * 詳細子画面への入室記録を組み合わせます。保存値は変更しません。
 */
object PresetSelectionDisplay {
    /**
     * 詳細訪問記録の識別子を返します。設定タブと役割ごとに独立させます。
     */
    fun detailVisitKey(fieldKey: String, role: CommandSettingRole?): String =
        "$fieldKey:${role?.name ?: "NONE"}"

    /**
     * プリセットカードを選択表示するかを返します。
     *
     * 形状一致していても詳細入室後は選択表示にしません。
     */
    fun isPresetSelected(matched: Boolean, configured: Boolean, detailVisited: Boolean): Boolean =
        matched && configured && !detailVisited

    /**
     * 詳細・汎用カードを選択表示するかを返します。
     *
     * 詳細入室後は形状一致の有無にかかわらず詳細側を表示します。
     */
    fun isDetailSelected(configured: Boolean, unmatched: Boolean, detailVisited: Boolean): Boolean =
        configured && (unmatched || detailVisited)
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
