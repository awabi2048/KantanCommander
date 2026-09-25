package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.selectedControlBlockStates
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.DiskArgument
import me.awabi2048.kantancommander.model.DisplayTextTimingPolicy
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataPath
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.EntityGroupOperation
import me.awabi2048.kantancommander.model.EntityPropertyValueKind
import me.awabi2048.kantancommander.model.EntityTagEntries
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.MAX_TIMER_SECONDS
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.PlaceItemDestination
import me.awabi2048.kantancommander.model.ParticleGeometry
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import me.awabi2048.kantancommander.model.StructureMirror
import me.awabi2048.kantancommander.model.StructureRotation
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.RegionAnchorKind
import me.awabi2048.kantancommander.model.RegionSpec
import me.awabi2048.kantancommander.model.TemporaryTemplate
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.variableScope
import java.util.UUID

/**
 * 個別設定画面が対象とする構造化データの役割です。
 *
 * インベントリGUIは従来payloadへ任意文字列を詰めていましたが、ジェスチャーGUIまで
 * 同じ分岐を複製すると、対象／位置／向きの保存先がずれます。このenumを両GUIの
 * 中間表現として使い、画面固有のルート文字列をドメインの役割へ変換します。
 */
enum class CommandSettingRole(val routeValue: String, val tabFieldKey: String) {
    NODE_TARGET("node_target", "target"),
    DESTINATION("destination", "destination"),
    DESTINATION_FACING("destination_facing", "destinationFacing"),
    SECONDARY_TARGET("secondary_target", "other"),
    CONDITION_POSITION("condition_position", "condition"),
    BLOCK_POSITION("block_position", "position"),
    BLOCK_FROM("block_from", "from"),
    BLOCK_TO("block_to", "to"),
    /** issue #11: 建造物設置位置です。BLOCK_POSITION と同じ位置設定木を使います。 */
    STRUCTURE_PLACE_POSITION("structure_place_position", "structurePlace"),
    /** issue #24: 群 pivot です。位置設定木を再利用します。 */
    GROUP_PIVOT("group_pivot", "pivot"),
    SOUND_POSITION("sound_position", "soundPosition"),
    PARTICLE_POSITION("particle_position", "position"),
    PARTICLE_END("particle_end", "particleEnd"),
    SUMMON_POSITION("summon_position", "summonPosition"),
    FIREWORK_POSITION("firework_position", "fireworkPosition"),
    /** issue #26 のコマンド単位の実行位置です。後続へ継承しません。 */
    EXECUTION_POSITION("execution_position", "executionPosition"),
    /** TEMP_SET ENTITYは通常コマンドと同じ対象選択木から1体を解決します。 */
    TEMPORARY_ENTITY("temporary_entity", "entity"),
    /** TEMP_SET LOCATIONは位置と向きをまとめた親画面から共通設定へ進みます。 */
    TEMPORARY_LOCATION("temporary_location", "location"),
    TEMPORARY_LOCATION_POSITION("temporary_location_position", "location"),
    TEMPORARY_LOCATION_FACING("temporary_location_facing", "location"),
    ;

    companion object {
        fun fromRoute(value: String?): CommandSettingRole? =
            entries.firstOrNull { it.routeValue == value }
    }
}

/** UIで統一表示する対象の大分類です。実行モデルのTargetKindとは分離します。 */
enum class TargetCategory {
    /** 対象設定がまだ存在しない状態です。暗黙の対象へ解決する意味は持ちません。 */
    UNSET,
    PLAYER,
    NON_PLAYER_ENTITY,
    TEMPORARY,
}

/** インベントリ／ジェスチャー双方で共有する設定対象の識別子です。 */
data class CommandSettingContext(
    val scriptId: UUID,
    val nodeId: UUID,
    val role: CommandSettingRole? = null,
) {
    companion object {
        /** 既存InventoryMenuのMenuRouteを共通コンテキストへ変換します。 */
        fun from(route: MenuRoute): CommandSettingContext? {
            val session = EditorSession.from(route) ?: return null
            val nodeId = route.payload["nodeId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return null
            val role = CommandSettingRole.fromRoute(route.payload["role"])
            return CommandSettingContext(session.scriptId, nodeId, role)
        }
    }
}

/** フィールドを押した後に遷移すべき意味上の編集経路です。 */
enum class CommandSettingEditor {
    TEXT,
    TARGET,
    POSITION,
    FACING,
    LOCATION,
    CONDITION_KIND,
    CONDITION_DETAIL,
    DISPLAY_MODE,
    ENTITY_ACTION,
    /** 統合選択画面（設定する項目の一覧）です。旧3択を置換します。 */
    ENTITY_DATA_ROOT,
    /** issue #10: 召喚時個別設定の一覧画面です。適用可能な項目だけを列挙します。 */
    SUMMON_DATA_ROOT,
    /** attribute子画面（設定可能なattributeの一覧）です。 */
    ENTITY_ATTRIBUTE_SELECT,
    /** タグ一覧画面（カード列挙・追加・モード切替・削除）です。 */
    ENTITY_TAG_LIST,
    ENTITY_PROPERTY_VALUE,
    PLACE_DESTINATION,
    ENTITY_EQUIPMENT_SLOT,
    ENTITY_OVERWRITE,
    VARIABLE_SCOPE,
    VARIABLE_NAME,
    VARIABLE_PROPERTY,
    VARIABLE_TYPE,
    VARIABLE_OPERATION,
    VARIABLE_CHANGE_MODE,
    VARIABLE_VALUE,
    CONDITION_INVERSION,
    CAMERA_SHAKE_TYPE,
    SOUND_SCOPE,
    SOUND_OPERATION,
    SOUND_TARGET_POSITION,
    PARTICLE_DISPLAY,
    PARTICLE_GEOMETRY,
    BLOCK_OPERATION,
    /** issue #11: 建造物名の確認・選択画面です。保存済み建造物を候補として列挙します。 */
    STRUCTURE_NAME,
    /** issue #11: 建造物の回転選択です。 */
    STRUCTURE_ROTATION,
    /** issue #11: 建造物の反転選択です。 */
    STRUCTURE_MIRROR,
    /** issue #24: 群操作の選択画面です。 */
    ENTITY_GROUP_OPERATION,
    /** issue #24: 群名の確認・選択画面です。保存済み群を候補として列挙します。 */
    ENTITY_GROUP_NAME,
}

/**
 * 実行時に値を解決する元です。
 *
 * ITEM／BLOCK／SOUND／EFFECTは、画面上で「直接値を設定する」か「一時変数を
 * 参照する」かを選べます。TEMP_SET自身の値入力は別の責務なので、この型は
 * 通常コマンドの値フィールドだけに適用します。
 */
enum class CommandValueSource {
    LITERAL,
    TEMPORARY,
}

data class CommandSettingDescriptor(
    val editor: CommandSettingEditor,
    val role: CommandSettingRole? = null,
)

/**
 * コマンド型とフィールド名から、両GUIが共有する編集経路を解決します。
 * ここへ新しい設定項目を追加すれば、表示側が「専用選択で編集」を描画でき、
 * インベントリGUIのルートとジェスチャーGUIの選択画面が同じ役割を受け取ります。
 */
object CommandSettingsModel {
    /** 通常コマンドの複合値を、保存フィールドと一時参照フィールドへ対応付けます。 */
    private data class TemporaryValueBinding(
        val referenceKey: String,
        val literalKeys: Set<String>,
    )

    /**
     * GUIで設定元を選べる通常コマンドの値だけを列挙します。
     *
     * CONDITIONのitem/blockは同名の保存欄を使いますが、ブロック判定は実行側が
     * blockTempRefを解決せず、条件詳細も独立した値入力画面です。そのため、ここへ
     * 含めるのは実行処理・バニラ出力・既存UIが同じ意味を共有するitem・blockの
     * 通常値です。音・効果の一時変数参照は廃止し、直接値だけを扱います。
     */
    private fun temporaryValueBinding(node: CommandNode, fieldKey: String): TemporaryValueBinding? {
        if (node.type == CommandType.TEMP_SET) return null
        return when (fieldKey) {
            "item" -> when (node.type) {
                CommandType.GIVE_ITEM -> TemporaryValueBinding("itemTempRef", setOf("item", "itemData"))
                CommandType.PLACE_ITEM -> TemporaryValueBinding("itemTempRef", setOf("item", "itemData"))
                else -> null
            }
            "block" -> if (node.type == CommandType.BLOCK_OPERATION) {
                TemporaryValueBinding("blockTempRef", setOf("block"))
            } else null
            else -> null
        }
    }

    /** 指定フィールドが通常値／一時変数の設定元を持つかを返します。 */
    fun supportsTemporaryValueReference(node: CommandNode, fieldKey: String): Boolean =
        temporaryValueBinding(node, fieldKey) != null

    /** 設定元を、保存済み参照の有無と入力途中の選択マーカーから復元します。 */
    fun temporaryValueSource(node: CommandNode, fieldKey: String): CommandValueSource? {
        val binding = temporaryValueBinding(node, fieldKey) ?: return null
        val reference = temporaryReference(node, binding.referenceKey)
        return if (reference != null || node.isExplicitlyConfigured(binding.referenceKey)) {
            CommandValueSource.TEMPORARY
        } else {
            CommandValueSource.LITERAL
        }
    }

    /** 現在の一時変数参照名を返します。入力途中の空参照は未設定として扱います。 */
    fun temporaryValueReference(node: CommandNode, fieldKey: String): String? {
        val binding = temporaryValueBinding(node, fieldKey) ?: return null
        return temporaryReference(node, binding.referenceKey)
    }

    /**
     * 一時変数モードを選択します。
     *
     * 直接値を残したまま参照モードへ切り替えると、実行時には参照値だけが採用
     * されるにもかかわらず、画面には古い直接値が残ります。設定元を一つにする
     * ため、選択時点で直接値を消し、参照名の入力途中マーカーだけを保存します。
     */
    fun selectTemporaryValueSource(node: CommandNode, fieldKey: String) {
        val binding = requireNotNull(temporaryValueBinding(node, fieldKey)) {
            "一時変数参照に対応しないフィールドです: ${node.type}.$fieldKey"
        }
        binding.literalKeys.forEach { key ->
            node.params.remove(key)
            node.clearConfigured(key)
        }
        setTemporaryReference(node, binding.referenceKey, null)
        node.markConfigured(binding.referenceKey)
    }

    /** 直接値モードを選択し、既存の一時変数参照と入力途中マーカーを解除します。 */
    fun selectLiteralValueSource(node: CommandNode, fieldKey: String) {
        val binding = requireNotNull(temporaryValueBinding(node, fieldKey)) {
            "一時変数参照に対応しないフィールドです: ${node.type}.$fieldKey"
        }
        setTemporaryReference(node, binding.referenceKey, null)
        node.clearConfigured(binding.referenceKey)
    }

    /** 一時変数名を確定し、対応する直接値を再び実行へ流さない状態にします。 */
    fun setTemporaryValueReference(node: CommandNode, fieldKey: String, name: String) {
        val binding = requireNotNull(temporaryValueBinding(node, fieldKey)) {
            "一時変数参照に対応しないフィールドです: ${node.type}.$fieldKey"
        }
        binding.literalKeys.forEach { key ->
            node.params.remove(key)
            node.clearConfigured(key)
        }
        setTemporaryReference(node, binding.referenceKey, name.trim())
        node.markConfigured(binding.referenceKey)
    }

    private fun temporaryReference(node: CommandNode, referenceKey: String): String? = when (referenceKey) {
        "itemTempRef" -> node.itemTempRef
        "blockTempRef" -> node.blockTempRef
        else -> error("未定義の一時変数参照フィールドです: $referenceKey")
    }?.takeIf(String::isNotBlank)

    private fun setTemporaryReference(node: CommandNode, referenceKey: String, value: String?) {
        when (referenceKey) {
            "itemTempRef" -> node.itemTempRef = value
            "blockTempRef" -> node.blockTempRef = value
            else -> error("未定義の一時変数参照フィールドです: $referenceKey")
        }
    }

    private val PLAYER_KINDS = setOf(
        TargetKind.NEAREST_PLAYER,
        TargetKind.NEARBY_PLAYERS,
        TargetKind.ALL_PLAYERS,
        TargetKind.RANDOM_PLAYER,
    )
    private val ENTITY_KINDS = setOf(
        TargetKind.NEAREST_ENTITY,
        TargetKind.NEARBY_ENTITIES,
    )

    /** 実行時の細分類を、GUIで表示する対象大分類へ写像します。 */
    fun targetCategory(kind: TargetKind?): TargetCategory = when (kind) {
        null -> TargetCategory.UNSET
        TargetKind.TEMPORARY -> TargetCategory.TEMPORARY
        in PLAYER_KINDS -> TargetCategory.PLAYER
        else -> TargetCategory.NON_PLAYER_ENTITY
    }

    /** 大分類を初めて選んだときに採用する実行モデル上の既定種別です。 */
    fun defaultTargetKind(category: TargetCategory): TargetKind = when (category) {
        TargetCategory.UNSET -> TargetKind.NEAREST_PLAYER
        TargetCategory.PLAYER -> TargetKind.NEAREST_PLAYER
        TargetCategory.NON_PLAYER_ENTITY -> TargetKind.NEAREST_ENTITY
        TargetCategory.TEMPORARY -> TargetKind.TEMPORARY
    }

    /** 大分類の詳細画面で選択できる実行モデル上の細分類を返します。 */
    fun targetKinds(category: TargetCategory): List<TargetKind> = when (category) {
        TargetCategory.UNSET -> emptyList()
        TargetCategory.PLAYER -> listOf(
            TargetKind.NEAREST_PLAYER,
            TargetKind.NEARBY_PLAYERS,
            TargetKind.ALL_PLAYERS,
            TargetKind.RANDOM_PLAYER,
        )
        TargetCategory.NON_PLAYER_ENTITY -> listOf(
            TargetKind.NEAREST_ENTITY,
            TargetKind.NEARBY_ENTITIES,
        )
        TargetCategory.TEMPORARY -> listOf(TargetKind.TEMPORARY)
    }

    /** 既存の細分類を維持したまま大分類だけを表示するための所属判定です。 */
    fun targetCategoryMatches(kind: TargetKind?, category: TargetCategory): Boolean =
        targetCategory(kind) == category

    /**
     * 両GUIで同じ条件付きフィールド集合を表示します。
     * 例えば時間設定に対応しないDISPLAY_TEXTからstaySecondsを隠す処理を各画面へ複製しないことで、
     * 片方だけに存在する設定項目や、選択後に参照不能になる値を防ぎます。
     */
    fun visibleFields(node: CommandNode): List<EditorField> {
        // 表示方式ごとの説明は同じ「時間設定」項目でも意味が異なります。
        // フィールド集合を返す段階で文言も文脈化し、インベントリGUIとジェスチャーGUIの
        // どちらでもタイトル用の説明がアクションバーへ誤表示されないようにします。
        val fields = EditorMenuLayout.fields(node.type, node).map { field ->
            if (node.type == CommandType.DISPLAY_TEXT &&
                field.key == "staySeconds" &&
                node.string("mode", "tellraw") == "actionbar"
            ) {
                field.copy(
                    descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DISPLAY_ACTIONBAR_DURATION,
                    actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DISPLAY_ACTIONBAR_DURATION,
                )
            } else field
        }
        val conditionalFields = when {
            node.type == CommandType.ENTITY_ACTION -> when (node.string("action", "ride")) {
                // 操作ごとの許可リストを明示します。除外リスト方式では、別操作へ
                // 追加した詳細項目が無関係な操作へ漏れ続けるためです。
                "ride" -> fields.filter { it.key in setOf("target", "action", "other") }
                "dismount" -> fields.filter { it.key in setOf("target", "action") }
                "motion" -> fields.filter { it.key in setOf("target", "action", "motionX", "motionY", "motionZ") }
                // 旧equipは#21のPLACE_ITEMへ移行済みのため許可リストから外します。
                // 旧tagは#32のENTITY_OPERATIONへ移行済みのため許可リストから外します。
                else -> fields.filter { it.key in setOf("target", "action") }
            }
            // 統合選択形式では設定対象IDで許可リストを切り替えます。
            // 真偽値は統合選択画面のクリックで直接切り替えるため、値タブを持ちません。
            // 有限候補は子画面、文字列・整数・attribute値はDialog入力へ進みます。
            node.type == CommandType.ENTITY_OPERATION -> when (val parsed = EntityDataPath.parse(node.string(EntityDataPath.PARAM_PATH))) {
                is EntityDataPath.Parsed.Tag -> fields.filter {
                    it.key in setOf(
                        "target", EntityDataPath.PARAM_PATH,
                        EntityTagEntries.PARAM_ENTRIES,
                    )
                }
                is EntityDataPath.Parsed.Attribute -> fields.filter {
                    it.key in setOf(
                        "target", EntityDataPath.PARAM_PATH,
                        EntityAttributeSupport.PARAM_VALUE,
                    )
                }
                is EntityDataPath.Parsed.Property -> {
                    val kind = EntityDataSchema.definition(parsed.propertyId)?.valueKind
                    if (kind == EntityPropertyValueKind.TEXT || kind == EntityPropertyValueKind.INTEGER) {
                        fields.filter {
                            it.key in setOf(
                                "target", EntityDataPath.PARAM_PATH,
                                EntityDataSchema.PARAM_VALUE,
                            )
                        }
                    } else {
                        fields.filter { it.key in setOf("target", EntityDataPath.PARAM_PATH) }
                    }
                }
                null -> fields.filter { it.key in setOf("target", EntityDataPath.PARAM_PATH) }
            }
            // 配置先で要求する対象が変わります。エンティティ系は対象欄、
            // ブロックコンテナ系は配置位置欄を使い、もう一方は出しません。
            // 額縁は内容固定のためスロット欄を出さず、既定値で確定します。
            // 配置先不明時は位置欄を出さず、配置先の確定を先に促します。
            // 不明時に両方出すと通常タブ列の上限（6件）を超えるためです。
            // 上書き設定は独立タブを持たず、配置先タブの中央下トグルで編集します。
            // 保存・検証・実行側の params["overwrite"] は変更しません。
            node.type == CommandType.PLACE_ITEM -> {
                val destination = PlaceItemDestination.from(node.string("placeDestination"))
                val allowed = buildSet {
                    if (destination == null || destination.requiresEntityTarget) add("target")
                    add("placeDestination")
                    if (destination != PlaceItemDestination.ITEM_FRAME) add("slot")
                    add("item")
                    add("count")
                    if (destination != null && destination.requiresBlockPosition) add("position")
                }
                fields.filter { it.key in allowed }
            }
            node.type == CommandType.DISPLAY_TEXT -> {
                val withoutUnusedSubtitle = if (node.string("mode", "tellraw") == "title") {
                    fields
                } else {
                    fields.filterNot { it.key == "subtitle" }
                }
                if (DisplayTextTimingPolicy.supports(node)) withoutUnusedSubtitle
                else withoutUnusedSubtitle.filterNot { it.key == "staySeconds" }
            }
            node.type == CommandType.BLOCK_OPERATION ->
                if (node.string("operation", "setblock") == "fill") {
                    fields.filterNot { it.key == "position" }
                } else {
                    fields.filterNot { it.key == "from" || it.key == "to" }
                }
            // issue #24: 群操作ごと に必要な項目だけを表示します。対象・pivot・
            // 移動軸・回転軸は操作で切り替えます。群名と操作自体は常に表示します。
            node.type == CommandType.ENTITY_GROUP -> {
                val allowed = when (EntityGroupOperation.from(node.string("operation"))) {
                    EntityGroupOperation.CREATE -> setOf("operation", "group", "target", "pivot")
                    EntityGroupOperation.ADD,
                    EntityGroupOperation.REMOVE,
                    -> setOf("operation", "group", "target")
                    EntityGroupOperation.SET_PIVOT -> setOf("operation", "group", "pivot")
                    EntityGroupOperation.MOVE -> setOf("operation", "group", "moveX", "moveY", "moveZ")
                    EntityGroupOperation.ROTATE -> setOf("operation", "group", "rotateX", "rotateY", "rotateZ")
                    EntityGroupOperation.DELETE,
                    EntityGroupOperation.CLEANUP,
                    null,
                    -> setOf("operation", "group")
                }
                fields.filter { it.key in allowed }
            }
            // 効果音は操作（再生／停止）を先頭タブで切り替え、以降のタブを組み替えます。
            // 除外リスト方式では、別操作へ追加した項目が無関係な操作へ漏れるため、
            // 操作ごとの許可リストを明示します。
            node.type == CommandType.PLAY_SOUND -> {
                val operation = SoundSettings.operation(node)
                val allowed = if (operation == SoundOperation.STOP) {
                    setOf("soundOperation", "soundTargetPosition", "soundSetting")
                } else {
                    setOf("soundOperation", "soundTargetPosition", "soundSetting", "soundScope")
                }
                fields.filter { it.key in allowed }
            }
            // #30: 通常表示と図形表示は別CommandTypeです。両者ともタブ順を
            // 「種類 → 表示設定 → 表示位置 → 表示対象」に統一します。
            // 種類（particle）と表示対象（particleDisplay）は共有し、
            // 表示設定の中身だけが型固有です（通常=散布、図形=形状＋密度）。
            node.type == CommandType.PARTICLE -> {
                val allowed = setOf("particle", "particleParameters", "position", "particleDisplay")
                fields.filter { it.key in allowed }
            }
            node.type == CommandType.PARTICLE_GEOMETRY -> {
                val allowed = setOf("particle", "geometrySettings", "particleDensity", "position", "particleDisplay")
                fields.filter { it.key in allowed }
            }
            // #2: 操作内容ごとに必要な項目だけを表示します。scope・name・operation は
            // 常に表示し、type・changeMode・value・source は操作で切り替えます。
            // #3: 値生成方法（changeMode）は値を書き込む3操作で表示し、生成方法別の
            // 項目（propertySource・property・range）は対応する
            // 生成方法のときだけ表示します。
            // - DEFINE: 型＋初期値。COPY／DELETE 用の項目は不要です。
            // - SET: 型＋値。COPY／DELETE 用の項目は不要です。
            // - CHANGE: 値＋changeMode。型は定義側を正とします。
            // - COPY: 複写元のみ。型・値・changeMode は不要です。
            // - DELETE: 項目なし（name だけで確定します）。
            node.type == CommandType.VARIABLE -> {
                val operation = runCatching { VariableOperation.valueOf(node.string("operation")) }
                    .getOrDefault(VariableOperation.DEFINE)
                fields.filterNot { field ->
                    when (operation) {
                        VariableOperation.DEFINE -> field.key == "source"
                        VariableOperation.SET -> field.key == "source"
                        VariableOperation.CHANGE -> field.key == "type" || field.key == "source"
                        VariableOperation.COPY -> field.key == "type" || field.key == "changeMode" || field.key == "value" ||
                            field.key == "propertySource" || field.key == "property" ||
                            field.key == "range"
                        VariableOperation.DELETE -> field.key == "type" || field.key == "changeMode" || field.key == "value" ||
                            field.key == "source" || field.key == "propertySource" || field.key == "property" ||
                            field.key == "range"
                    }
                }
            }
            else -> fields
        }
        // issue #26: 実行位置は対応コマンドの共通設定として末尾へ追加します。
        // 制御系（待機・分岐端子・変数操作等）には表示しません。
        val withExecutionPosition = if (supportsExecutionPosition(node.type)) {
            conditionalFields + executionPositionField()
        } else conditionalFields
        // 実行時に参照されない設定は、保存値を変更せずに両GUIから隠します。
        // ここで一元判定することで、表示だけ残って実行結果へ反映されない
        // 「テレポート先の向き」「サウンド位置」の設定入口を作りません。
        return withExecutionPosition.filter { isFieldVisible(node, it.key) }
    }

    /**
     * issue #26 の実行位置を表示するコマンドかを返します。
     *
     * 実行位置が意味を持たない制御系（待機・分岐端子・変数操作・繰返し制御等）には
     * 表示しません。旧コンテキストの実行者・対象・向き・継承は復活させません。
     */
    fun supportsExecutionPosition(type: CommandType): Boolean = type in setOf(
        CommandType.TELEPORT,
        CommandType.GIVE_ITEM,
        CommandType.PLACE_ITEM,
        CommandType.ENTITY_ACTION,
        CommandType.ENTITY_OPERATION,
        CommandType.DISPLAY_TEXT,
        CommandType.SUMMON_ENTITY,
        CommandType.PLAY_SOUND,
        CommandType.PARTICLE,
        CommandType.PARTICLE_GEOMETRY,
        CommandType.FIREWORK_LAUNCH,
        CommandType.APPLY_EFFECT,
        CommandType.CAMERA_SHAKE,
        CommandType.BLOCK_OPERATION,
        CommandType.ENTITY_DELETE,
        CommandType.CONDITION,
        CommandType.DISK_CALL,
    )

    /** issue #26 の実行位置フィールドです。作用位置と区別できる専用文言を使います。 */
    private fun executionPositionField(): EditorField = EditorField(
        key = "executionPosition",
        label = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EXECUTION_POSITION,
        material = org.bukkit.Material.COMPASS,
        descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_EXECUTION_POSITION,
        actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_EXECUTION_POSITION,
        value = { node -> node.executionPositionSpec?.kind?.let { kind ->
            // 表示キーは CommandEditMenu.displayPosition と同じ既存キーを使い、
            // 新規言語キーを増やしません。
            DisplayValue.Localized(
                when (kind) {
                    me.awabi2048.kantancommander.model.PositionKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAPTURED_POSITION
                    me.awabi2048.kantancommander.model.PositionKind.DISK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTROL_BLOCK_POSITION
                    me.awabi2048.kantancommander.model.PositionKind.MYWORLD_SPAWN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN
                    me.awabi2048.kantancommander.model.PositionKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES
                    me.awabi2048.kantancommander.model.PositionKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
                    me.awabi2048.kantancommander.model.PositionKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TARGET_POSITION
                },
            )
        } ?: DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) },
    )

    /**
     * コマンドの現在値に応じた実行依存フィールドの可視性を返します。
     *
     * 古い保存値を移行・消去する責務は持たせず、現在の実行意味論に不要な
     * 入力だけをGUIから隠します。これにより、保存済みデータを無視するという
     * 方針を保ったまま、表示と実行の入口だけを一致させられます。
     */
    fun isFieldVisible(node: CommandNode, fieldKey: String): Boolean = when (node.type) {
        CommandType.TELEPORT ->
            fieldKey != "destinationFacing" || node.destinationSpec?.kind != PositionKind.TEMPORARY

        CommandType.PLAY_SOUND, CommandType.APPLY_EFFECT -> true

        CommandType.PARTICLE -> true

        // #30: 図形の終点・対角点は線・直方体でのみ意味を持つため、それ以外の形状では隠します。
        CommandType.PARTICLE_GEOMETRY ->
            fieldKey != "geometryEnd" || ParticleGeometry.shape(node) == ParticleShape.LINE ||
                ParticleGeometry.shape(node) == ParticleShape.BOX

        // #2: 操作内容ごとに必要な項目だけを表示します。値欄・型別項目は
        // 値を必要とする操作（DEFINE／SET／CHANGE）でのみ表示し、複写元は
        // COPY でのみ、型別項目は現在の型に一致するものだけ表示します。
        CommandType.VARIABLE -> isVariableFieldVisible(node, fieldKey)
        CommandType.TEMP_SET -> isTemporaryFieldVisible(node, fieldKey)

        else -> true
    }

    /** 数値Range乱数を選択可能な操作かを、GUIとDialog保存時の双方で共有します。 */
    fun supportsRandomNumberMode(node: CommandNode): Boolean = when (node.type) {
        CommandType.VARIABLE -> ValueType.parse(node.string("type")) == ValueType.NUMBER &&
            runCatching { VariableOperation.valueOf(node.string("operation")) }
                .getOrDefault(VariableOperation.DEFINE) in setOf(
                VariableOperation.DEFINE,
                VariableOperation.SET,
                VariableOperation.CHANGE,
            )
        CommandType.TEMP_SET -> ValueType.parse(node.string("tempType")) == ValueType.NUMBER
        else -> false
    }

    /** 現在Range入力を編集できる状態かを確認し、Dialog中の方式変更も拒否します。 */
    fun isRandomRangeInputAvailable(node: CommandNode): Boolean =
        supportsRandomNumberMode(node) &&
            runCatching { VariableChangeMode.valueOf(node.string("changeMode")) }
                .getOrDefault(VariableChangeMode.ASSIGN) == VariableChangeMode.RANDOM

    /**
     * issue #2 の変数操作コマンドの項目可視性です。
     *
     * scope・operation・name は常に表示します。type・changeMode・value・source と
     * #3 の生成方法別項目は操作内容・値生成方法・現在の型で切り替えます。保存値を
     * 変更せず表示だけを絞るため、両GUIで同じ判定を使います。
     */
    fun isVariableFieldVisible(node: CommandNode, fieldKey: String): Boolean {
        if (fieldKey in setOf("scope", "operation", "name")) return true
        // #22: 対象は ENTITY scope でのみ表示します。
        if (fieldKey == "target") return node.variableScope() == VariableScope.ENTITY
        val operation = runCatching { VariableOperation.valueOf(node.string("operation")) }
            .getOrDefault(VariableOperation.DEFINE)
        if (fieldKey == "source") return operation == VariableOperation.COPY
        val needsValue = operation == VariableOperation.DEFINE ||
            operation == VariableOperation.SET ||
            operation == VariableOperation.CHANGE
        if (!needsValue) return false
        val mode = runCatching { VariableChangeMode.valueOf(node.string("changeMode", VariableChangeMode.ASSIGN.name)) }
            .getOrDefault(VariableChangeMode.ASSIGN)
        if (fieldKey == "type") {
            // #22: ENTITY scope は定義表を持たないため、CHANGE＋ASSIGN でも
            // 型欄で指定します。EXECUTION／WORLD の CHANGE は定義済みの型を使います。
            if (operation == VariableOperation.CHANGE) {
                return node.variableScope() == VariableScope.ENTITY && mode == VariableChangeMode.ASSIGN
            }
            // PROPERTY／RANDOM モードでは生成結果の型を採用するため型欄を隠します。
            return mode == VariableChangeMode.ASSIGN || mode == VariableChangeMode.CALCULATE
        }
        if (fieldKey == "changeMode") return true
        // #3: 生成方法別の項目は対応する生成方法のときだけ表示します。
        if (fieldKey == "propertySource" || fieldKey == "property") return mode == VariableChangeMode.PROPERTY
        if (fieldKey == "range") return mode == VariableChangeMode.RANDOM
        val type = ValueType.parse(node.string("type")) ?: return false
        return when (fieldKey) {
            "value" -> (mode == VariableChangeMode.ASSIGN &&
                (type == ValueType.NUMBER || type == ValueType.STRING)) ||
                (mode == VariableChangeMode.CALCULATE && type == ValueType.NUMBER)
            "location" -> mode == VariableChangeMode.ASSIGN && type == ValueType.LOCATION
            "item" -> mode == VariableChangeMode.ASSIGN && type == ValueType.ITEM
            "block" -> mode == VariableChangeMode.ASSIGN && type == ValueType.BLOCK
            "entity" -> mode == VariableChangeMode.ASSIGN && type == ValueType.ENTITY
            else -> true
        }
    }

    /** 一時変数もワールド変数と同じ値指定／Range乱数の表示規則を使います。 */
    private fun isTemporaryFieldVisible(node: CommandNode, fieldKey: String): Boolean {
        if (fieldKey in setOf("name", "tempType")) return true
        val type = ValueType.parse(node.string("tempType")) ?: return false
        val mode = runCatching { VariableChangeMode.valueOf(node.string("changeMode")) }
            .getOrDefault(VariableChangeMode.ASSIGN)
        if (fieldKey == "changeMode") return type == ValueType.NUMBER
        if (fieldKey == "range") return type == ValueType.NUMBER && mode == VariableChangeMode.RANDOM
        if (fieldKey == "value") return mode == VariableChangeMode.ASSIGN &&
            (type == ValueType.NUMBER || type == ValueType.STRING)
        if (mode != VariableChangeMode.ASSIGN) return false
        return when (fieldKey) {
            "location", "x", "y", "z", "yaw" -> type == ValueType.LOCATION
            "entity", "entityId" -> type == ValueType.ENTITY
            "item", "itemData" -> type == ValueType.ITEM
            "block" -> type == ValueType.BLOCK
            else -> false
        }
    }

    /**
     * Gesture GUIで表示するフィールドを返します。
     *
     * Inventory GUIとGesture GUIは同じ「表示時間」設定を編集しますが、Gesture GUIでは
     * タブを増やさず、選択中のタブの現在値欄へ3項目をまとめて表示します。値の結合は
     * 表示専用の意味型へ閉じ込め、各画面が独自の文字列フォーマットを持たないようにします。
     */
    fun gestureVisibleFields(node: CommandNode): List<EditorField> =
        visibleFields(node).filter { field ->
            // エンティティ操作の詳細（タグ一覧・attribute値・設定値）は
            // 子画面で編集するため、タブ列には出しません。タブ列が選択で
            // 増減すると表示と経路がずれるため、対象・項目・実行位置に固定します。
            // Inventory GUIの直接編集行はvisibleFields側に残します。
            val entityDetail = node.type == CommandType.ENTITY_OPERATION && field.key in setOf(
                    EntityTagEntries.PARAM_ENTRIES,
                    EntityAttributeSupport.PARAM_VALUE,
                    EntityDataSchema.PARAM_VALUE,
                )
            val randomRange = node.type in setOf(CommandType.VARIABLE, CommandType.TEMP_SET) &&
                field.key == "range"
            !entityDetail && !randomRange
        }.map { field ->
            val randomNumberMode = field.key == "changeMode" &&
                node.type in setOf(CommandType.VARIABLE, CommandType.TEMP_SET) &&
                ValueType.parse(node.string(if (node.type == CommandType.TEMP_SET) "tempType" else "type")) == ValueType.NUMBER &&
                runCatching { VariableChangeMode.valueOf(node.string("changeMode")) }
                    .getOrDefault(VariableChangeMode.ASSIGN) == VariableChangeMode.RANDOM
            if (randomNumberMode) {
                // 範囲入力は独立タブにせず、乱数方式の選択を再クリックしたDialogで編集します。
                // 保存値は方式タブの複合表示にも出し、入力後に範囲が見失われないようにします。
                field.copy(
                    value = { currentNode ->
                        DisplayValue.VariableRandomRange(
                            mode = field.value(currentNode),
                            range = currentNode.string("range").takeIf(String::isNotBlank)
                                ?.let(DisplayValue::Literal)
                                ?: DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET),
                        )
                    },
                )
            } else if (node.type == CommandType.DISPLAY_TEXT &&
                field.key == "staySeconds" &&
                DisplayTextTimingPolicy.supports(node)
            ) {
                // issue #35-2: アクションバーは単独の表示時間だけを示します。
                if (node.string("mode", "tellraw") == "actionbar") {
                    field.copy(
                        value = { currentNode ->
                            DisplayValue.Duration(
                                seconds = currentNode.string(
                                    "staySeconds",
                                    currentNode.type.defaults["staySeconds"].orEmpty(),
                                ),
                            )
                        },
                    )
                } else {
                    field.copy(
                        value = { currentNode ->
                            DisplayValue.Timing(
                                fadeInSeconds = currentNode.string(
                                    "fadeInSeconds",
                                    currentNode.type.defaults["fadeInSeconds"].orEmpty(),
                                ),
                                staySeconds = currentNode.string(
                                    "staySeconds",
                                    currentNode.type.defaults["staySeconds"].orEmpty(),
                                ),
                                fadeOutSeconds = currentNode.string(
                                    "fadeOutSeconds",
                                    currentNode.type.defaults["fadeOutSeconds"].orEmpty(),
                                ),
                            )
                        },
                    )
                }
            } else if (node.type == CommandType.PARTICLE && field.key == "particleParameters") {
                field.copy(
                    value = { currentNode ->
                        DisplayValue.ParticleParameters(
                            currentNode.string(ParticleSettings.PARAM_DELTA_X, "0.0"),
                            currentNode.string(ParticleSettings.PARAM_DELTA_Y, "0.0"),
                            currentNode.string(ParticleSettings.PARAM_DELTA_Z, "0.0"),
                            currentNode.string(ParticleSettings.PARAM_SPEED, "0.0"),
                            currentNode.string(ParticleSettings.PARAM_COUNT, "1"),
                        )
                    },
                )
            } else {
                field
            }
        }

    /**
     * 下部画面の設定タブへ表示する、未完了警告の固定キーを返します。
     *
     * 検証エラーの文言や保存値から表示文字列を組み立てると、同じタブでも
     * エラーの種類によって表示が揺れ、ローカライズ契約も画面側へ漏れます。
     * そのため、画面で編集できる設定項目ごとに、コマンド型とfieldKeyの
     * 組み合わせをこの表で固定します。実際の文言はCC-Systemの型付きキーから
     * 解決し、タブ単位で常に同じ警告を表示します。
     */
    fun incompleteWarningKey(node: CommandNode, fieldKey: String): LocalizationKey<String> {
        // issue #26: 実行位置タブの注意表示です。専用警告キーを使います。
        if (fieldKey == "executionPosition") {
            return KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_EXECUTION_POSITION
        }
        return when (node.type) {
            CommandType.TELEPORT -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                "destination" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_DESTINATION
                "destinationFacing" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_DESTINATION_FACING
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.GIVE_ITEM -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_GIVE_TARGET
                "item" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_GIVE_ITEM
                "count" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_COUNT
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.ENTITY_ACTION -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                "action" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_ACTION
                "other" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_OTHER
                "tagOperation" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TAG_OPERATION
                "tag" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TAG
                "motionX", "motionY", "motionZ" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_MOTION
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.ENTITY_OPERATION -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                EntityDataPath.PARAM_PATH -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_ENTITY_DATA
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.PLACE_ITEM -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                "placeDestination" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_PLACE_DESTINATION
                "slot" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_PLACE_SLOT
                "item" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_PLACE_ITEM
                "count" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_COUNT
                "overwrite" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_OVERWRITE
                "position" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK_POSITION
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.DISPLAY_TEXT -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_DISPLAY_TARGET
                "mode" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_MODE
                "text", "subtitle" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TEXT
                "fadeInSeconds", "staySeconds", "fadeOutSeconds" ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_DURATION
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.WAIT -> when (fieldKey) {
                "seconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_WAIT
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.SUMMON_ENTITY -> when (fieldKey) {
                "summonEntity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_ENTITY
                "summonData" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_ENTITY_DATA
                "tags" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TAGS
                "summonPosition" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SUMMON_POSITION
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.PLAY_SOUND -> when (fieldKey) {
                "soundOperation" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_OPERATION
                "soundTargetPosition" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SOUND_TARGET_POSITION
                "soundSetting" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SOUND_SETTING
                "soundScope" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SOUND_SCOPE
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.PARTICLE -> when (fieldKey) {
                "particleDisplay" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_PARTICLE_DISPLAY
                "particle" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_PARTICLE
                "particleParameters" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_PARTICLE_PARAMETERS
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.PARTICLE_GEOMETRY -> when (fieldKey) {
                "particleDisplay" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_PARTICLE_DISPLAY
                "particle" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_PARTICLE
                "geometrySettings" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_GEOMETRY_SETTINGS
                "particleDensity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_PARTICLE_PARAMETERS
                "geometryEnd" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_GEOMETRY_SETTINGS
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.FIREWORK_LAUNCH -> when (fieldKey) {
                "fireworkPosition" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SUMMON_POSITION
                "fireworkContents" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_FIREWORK_CONTENTS
                "burstHeight" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BURST_HEIGHT
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.APPLY_EFFECT -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                "effect" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_EFFECT
                "level" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_LEVEL
                "seconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SECONDS
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.CAMERA_SHAKE -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                "intensity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_INTENSITY
                "seconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SECONDS
                "shakeType" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SHAKE_TYPE
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.BLOCK_OPERATION -> when (fieldKey) {
                "operation" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK_OPERATION
                "block" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK
                "position" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK_POSITION
                "from" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK_FROM
                "to" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK_TO
                else -> undefinedWarningKey(node, fieldKey)
            }
            // 建造物読み込みは名前・回転・反転・位置の警告を示します。
            // 位置系はブロック操作と同じ警告キーを再利用します。
            CommandType.STRUCTURE_PLACE -> when (fieldKey) {
                "structure" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK
                "rotation", "mirror" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK_OPERATION
                "structurePlace" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK_POSITION
                else -> undefinedWarningKey(node, fieldKey)
            }
            // issue #24: 群操作の警告を示します。群名・操作・軸入力は汎用警告を
            // 再利用し、対象・pivot は既存の位置系警告を使います。
            CommandType.ENTITY_GROUP -> when (fieldKey) {
                "operation" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_OPERATION
                "group" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_NAME
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                "pivot" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_POSITION
                "moveX", "moveY", "moveZ", "rotateX", "rotateY", "rotateZ" ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VALUE
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.ENTITY_DELETE -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.CONDITION -> when (fieldKey) {
                "inverted" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_INVERTED
                "kind" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_CONDITION_KIND
                "condition" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_CONDITION
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.DISK_CALL -> when (fieldKey) {
                "diskId" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_DISK
                // 呼び出し設定の未完了も、ディスク指定と同じ警告面で示します。
                "diskCallSettings" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_DISK
                else -> undefinedWarningKey(node, fieldKey)
            }
            // issue #8: 「値を返す」は変数由来またはリテラル値のどちらかを返します。
            CommandType.RETURN_VALUE -> when (fieldKey) {
                "sourceScope", "source" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VARIABLE
                "value" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VALUE
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.VARIABLE -> when (fieldKey) {
                "operation", "changeMode" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_OPERATION
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                "name" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VARIABLE
                "type" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TYPE
                "scope" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VARIABLE
                "source" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VARIABLE
                "propertySource" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VARIABLE
                "property" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VALUE
                "range" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VALUE
                "value", "location", "x", "y", "z", "yaw", "pitch", "item", "block", "entity",
                "entityId", "sound", "soundParameters", "volume", "effect", "level", "seconds" ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VALUE
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.TEMP_SET -> when (fieldKey) {
                "name" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VARIABLE
                "tempType" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TYPE
                "changeMode" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_OPERATION
                "range", "value", "location", "x", "y", "z", "yaw", "item", "block", "entity",
                "entityId" ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VALUE
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.FOR_START -> when (fieldKey) {
                "count" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_REPEAT_COUNT
                else -> undefinedWarningKey(node, fieldKey)
            }
            // 折り畳みノードの警告文言は折り畳み前の種別と共用します。
            CommandType.FOLDED_FOR -> incompleteWarningKey(node.copy(type = CommandType.FOR_START), fieldKey)
            CommandType.FOLDED_IF -> incompleteWarningKey(node.copy(type = CommandType.CONDITION), fieldKey)
            CommandType.MERGE,
            CommandType.FOR_END,
            CommandType.BREAK,
            CommandType.CONTINUE,
            -> undefinedWarningKey(node, fieldKey)
        }
    }

    /**
     * 検証側の内部フィールド名を、実際に表示するタブへ正規化します。
     *
     * 表示時間はInventory GUIとGesture GUIのどちらでも1項目です。Gesture GUIでは
     * そのタブの現在値欄だけを3項目表示へ拡張します。
     * また非表示のsubtitleはtextタブで編集するため、検証結果だけが存在しない
     * タブを指さないようにします。
     */
    fun visibleAttentionFieldKey(node: CommandNode, validationFieldKey: String): String? {
        val visibleKey = when (node.type) {
            CommandType.DISPLAY_TEXT -> when (validationFieldKey) {
                "fadeInSeconds", "staySeconds", "fadeOutSeconds" -> "staySeconds"
                "subtitle" -> "text"
                else -> validationFieldKey
            }
            CommandType.PLAY_SOUND -> when (validationFieldKey) {
                "volume", "pitch", "sound", "soundCategory" -> "soundSetting"
                "target" -> "soundTargetPosition"
                // 現行バリデーターはscopeの不正をsoundPositionとして報告します。
                "soundPosition" -> "soundTargetPosition"
                else -> validationFieldKey
            }
            CommandType.PARTICLE -> when (validationFieldKey) {
                ParticleSettings.PARAM_DELTA_X,
                ParticleSettings.PARAM_DELTA_Y,
                ParticleSettings.PARAM_DELTA_Z,
                ParticleSettings.PARAM_SPEED,
                ParticleSettings.PARAM_COUNT,
                -> "particleParameters"
                ParticleSettings.PARAM_DATA -> "particle"
                "target" -> "particleDisplay"
                else -> validationFieldKey
            }
            CommandType.PARTICLE_GEOMETRY -> when (validationFieldKey) {
                ParticleSettings.PARAM_DATA -> "particle"
                "target" -> "particleDisplay"
                else -> validationFieldKey
            }
            CommandType.CONDITION -> when (validationFieldKey) {
                "sneaking", "variable", "operator", "value", "block", "item", "itemData" -> "condition"
                else -> validationFieldKey
            }
            // 上書き設定は配置先タブの中央下トグルで編集するため、
            // 検証結果だけが存在しないタブを指さないようにします。
            CommandType.PLACE_ITEM -> when (validationFieldKey) {
                "overwrite" -> "placeDestination"
                else -> validationFieldKey
            }
            // 折り畳みノードの注意タブ対応付けは折り畳み前の種別と共用します。
            CommandType.FOLDED_IF -> visibleAttentionFieldKey(
                node.copy(type = CommandType.CONDITION),
                validationFieldKey,
            )
            CommandType.FOLDED_FOR -> validationFieldKey
            CommandType.TEMP_SET -> when (validationFieldKey) {
                // 旧形式のx/y/z/yaw/pitchをLOCATIONの親タブへ投影し、
                // 新形式のENTITYも対象選択欄へ投影します。
                "x", "y", "z", "yaw", "pitch", "location" -> "location"
                "entityId", "entity" -> "entity"
                else -> validationFieldKey
            }
            // 詳細タブを持たないため、検証結果は設定対象タブへ投影します。
            CommandType.ENTITY_OPERATION -> when (validationFieldKey) {
                EntityDataPath.PARAM_PATH,
                EntityTagEntries.PARAM_ENTRIES,
                EntityAttributeSupport.PARAM_VALUE,
                EntityDataSchema.PARAM_VALUE,
                -> EntityDataPath.PARAM_PATH
                else -> validationFieldKey
            }
            else -> validationFieldKey
        }
        return gestureVisibleFields(node).firstOrNull { it.key == visibleKey }?.key
    }

    private fun undefinedWarningKey(node: CommandNode, fieldKey: String): Nothing =
        error("未定義の設定警告キーです: type=${node.type}, field=$fieldKey")

    fun descriptor(node: CommandNode, fieldKey: String): CommandSettingDescriptor {
        // issue #26: 実行位置はコマンド横断の共通設定項目です。表示可否は
        // visibleFields側で制御し、ここでは保存先の役割だけを解決します。
        if (fieldKey == "executionPosition") {
            return CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.EXECUTION_POSITION)
        }
        return when (node.type) {
        CommandType.TELEPORT -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            "destination" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.DESTINATION)
            "destinationFacing" -> CommandSettingDescriptor(CommandSettingEditor.FACING, CommandSettingRole.DESTINATION_FACING)
            else -> text()
        }
        CommandType.GIVE_ITEM -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            else -> text()
        }
        CommandType.ENTITY_ACTION -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            "action" -> CommandSettingDescriptor(CommandSettingEditor.ENTITY_ACTION)
            "other" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.SECONDARY_TARGET)
            // 旧tag欄はENTITY_OPERATIONへ移行済みのため出しません。
            else -> text()
        }
        CommandType.ENTITY_OPERATION -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            EntityDataPath.PARAM_PATH -> CommandSettingDescriptor(CommandSettingEditor.ENTITY_DATA_ROOT)
            EntityTagEntries.PARAM_ENTRIES -> CommandSettingDescriptor(CommandSettingEditor.ENTITY_TAG_LIST)
            EntityAttributeSupport.PARAM_VALUE -> text()
            // 設定値の編集経路は項目の型で振り分けます。
            // 真偽値は統合選択画面のクリックで直接切り替えるため子画面を持たず、
            // 有限候補は専用画面、文字列・整数はDialog直結で編集します。
            EntityDataSchema.PARAM_VALUE -> {
                val propertyId = EntityDataPath.propertyIdOf(node.string(EntityDataPath.PARAM_PATH))
                val kind = propertyId?.let { EntityDataSchema.definition(it)?.valueKind }
                if (kind == EntityPropertyValueKind.ENUM) {
                    CommandSettingDescriptor(CommandSettingEditor.ENTITY_PROPERTY_VALUE)
                } else text()
            }
            else -> text()
        }
        // 配置スロットは配置先で編集経路が変わります。装備は既存の装備選択、
        // 数値スロットは汎用テキスト入力（表示は1始まり・保存は0始まり）、
        // 額縁は固定のため欄自体を出しません。
        CommandType.PLACE_ITEM -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            "placeDestination" -> CommandSettingDescriptor(CommandSettingEditor.PLACE_DESTINATION)
            "slot" -> if (PlaceItemDestination.from(node.string("placeDestination")) == PlaceItemDestination.EQUIPMENT) {
                CommandSettingDescriptor(CommandSettingEditor.ENTITY_EQUIPMENT_SLOT)
            } else text()
            "overwrite" -> CommandSettingDescriptor(CommandSettingEditor.ENTITY_OVERWRITE)
            "position" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.BLOCK_POSITION)
            else -> text()
        }
        CommandType.DISPLAY_TEXT -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            "mode" -> CommandSettingDescriptor(CommandSettingEditor.DISPLAY_MODE)
            else -> text()
        }
        CommandType.WAIT -> text()
        CommandType.SUMMON_ENTITY -> when (fieldKey) {
            "summonPosition" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.SUMMON_POSITION)
            // issue #10: 個別設定は一覧画面で編集します。真偽値・有限候補・
            // 文字列・整数の入力経路は画面側で項目の型に合わせます。
            "summonData" -> CommandSettingDescriptor(CommandSettingEditor.SUMMON_DATA_ROOT)
            else -> text()
        }
        CommandType.PLAY_SOUND -> when (fieldKey) {
            "soundOperation" -> CommandSettingDescriptor(CommandSettingEditor.SOUND_OPERATION)
            "soundTargetPosition" -> CommandSettingDescriptor(CommandSettingEditor.SOUND_TARGET_POSITION)
            "soundScope" -> CommandSettingDescriptor(CommandSettingEditor.SOUND_SCOPE)
            else -> text()
        }
        CommandType.PARTICLE -> when (fieldKey) {
            "particleDisplay" -> CommandSettingDescriptor(
                CommandSettingEditor.PARTICLE_DISPLAY,
                CommandSettingRole.NODE_TARGET,
            )
            "position" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.PARTICLE_POSITION)
            else -> text()
        }
        CommandType.PARTICLE_GEOMETRY -> when (fieldKey) {
            "particleDisplay" -> CommandSettingDescriptor(
                CommandSettingEditor.PARTICLE_DISPLAY,
                CommandSettingRole.NODE_TARGET,
            )
            "position" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.PARTICLE_POSITION)
            "geometryEnd" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.PARTICLE_END)
            "geometrySettings" -> CommandSettingDescriptor(CommandSettingEditor.PARTICLE_GEOMETRY)
            else -> text()
        }
        CommandType.FIREWORK_LAUNCH -> when (fieldKey) {
            "fireworkPosition" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.FIREWORK_POSITION)
            else -> text()
        }
        CommandType.APPLY_EFFECT -> if (fieldKey == "target") {
            CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
        } else text()
        CommandType.CAMERA_SHAKE -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            "shakeType" -> CommandSettingDescriptor(CommandSettingEditor.CAMERA_SHAKE_TYPE)
            else -> text()
        }
        CommandType.CONDITION -> when (fieldKey) {
            "inverted" -> CommandSettingDescriptor(CommandSettingEditor.CONDITION_INVERSION)
            "kind" -> CommandSettingDescriptor(CommandSettingEditor.CONDITION_KIND)
            "condition" -> CommandSettingDescriptor(CommandSettingEditor.CONDITION_DETAIL)
            else -> text()
        }
        CommandType.DISK_CALL -> text()
        // issue #8: 呼び出し設定は専用子画面で編集します。ここでは対話入力へ進めません。
        CommandType.RETURN_VALUE -> text()
        CommandType.BLOCK_OPERATION -> when (fieldKey) {
            "operation" -> CommandSettingDescriptor(CommandSettingEditor.BLOCK_OPERATION)
            "position" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.BLOCK_POSITION)
            "from" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.BLOCK_FROM)
            "to" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.BLOCK_TO)
            else -> text()
        }
        // issue #11: 建造物読み込みは名前・回転・反転を専用画面で編集し、設置位置だけは
        // 既存の位置設定木を再利用します（BLOCK_OPERATION と同じ扱い）。
        CommandType.STRUCTURE_PLACE -> when (fieldKey) {
            "structure" -> CommandSettingDescriptor(CommandSettingEditor.STRUCTURE_NAME)
            "rotation" -> CommandSettingDescriptor(CommandSettingEditor.STRUCTURE_ROTATION)
            "mirror" -> CommandSettingDescriptor(CommandSettingEditor.STRUCTURE_MIRROR)
            "structurePlace" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.STRUCTURE_PLACE_POSITION)
            else -> text()
        }
        // issue #24: 群操作は操作・群名を専用画面で編集し、対象・pivot・軸入力は
        // 既存の編集経路を再利用します。軸入力は数値 Dialog です。
        CommandType.ENTITY_GROUP -> when (fieldKey) {
            "operation" -> CommandSettingDescriptor(CommandSettingEditor.ENTITY_GROUP_OPERATION)
            "group" -> CommandSettingDescriptor(CommandSettingEditor.ENTITY_GROUP_NAME)
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            "pivot" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.GROUP_PIVOT)
            else -> text()
        }
        CommandType.ENTITY_DELETE -> if (fieldKey == "target") {
            CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
        } else text()
        CommandType.VARIABLE -> when (fieldKey) {
            "scope" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_SCOPE)
            // #22: ENTITY scope の対象は通常の対象形式で選択します。
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            // #2: WORLD scope の変数名は定義済み候補から選択し、EXECUTION scope の
            // 変数名は実行経路で有効範囲が変わるため文字列入力で指定します。
            // scope 欄のない旧ノードは WORLD として読み替えます。
            "name" -> if (node.variableScope() == VariableScope.WORLD) {
                CommandSettingDescriptor(CommandSettingEditor.VARIABLE_NAME)
            } else {
                text()
            }
            "type" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_TYPE)
            "operation" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_OPERATION)
            "changeMode" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_CHANGE_MODE)
            "value" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_VALUE)
            // #3: 参照元は変数名の自由入力、詳細値は候補選択、乱数範囲は数値入力です。
            "propertySource" -> text()
            "property" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_PROPERTY)
            "range" -> text()
            "location" -> CommandSettingDescriptor(CommandSettingEditor.LOCATION, CommandSettingRole.TEMPORARY_LOCATION)
            "entity" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.TEMPORARY_ENTITY)
            else -> text()
        }
        CommandType.TEMP_SET -> when (fieldKey) {
            "tempType" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_TYPE)
            "changeMode" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_CHANGE_MODE)
            "value" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_VALUE)
            "range" -> text()
            "entity" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.TEMPORARY_ENTITY)
            "location" -> CommandSettingDescriptor(CommandSettingEditor.LOCATION, CommandSettingRole.TEMPORARY_LOCATION)
            else -> text()
        }
        CommandType.FOR_START -> text()
        // 折り畳みノードの設定編集子は折り畳み前の種別と共用します。
        CommandType.FOLDED_FOR -> descriptor(node.copy(type = CommandType.FOR_START), fieldKey)
        CommandType.FOLDED_IF -> descriptor(node.copy(type = CommandType.CONDITION), fieldKey)
        CommandType.MERGE,
        CommandType.FOR_END,
        CommandType.BREAK,
        CommandType.CONTINUE,
        -> text()
        }
    }

    /**
     * 文字列パラメータの書き込みも、両GUIが同じドメイン入口を通ります。
     *
     * 構造化値だけを共通setterへ寄せても、条件・表示・変数などのparamsだけが
     * 画面ごとの直接代入に残ると、明示設定の記録や将来の検証を再び分岐させます。
     * 文字列値の保存と「ユーザーが設定した」状態をここで同時に扱います。空文字は
     * 入力値の解除を意味するため、明示設定フラグも解除して未設定へ戻します。
     */
    fun setParameter(node: CommandNode, key: String, value: String) {
        require(key.isNotBlank()) { "設定キーが空です" }
        if (node.type == CommandType.VARIABLE && key == "name") {
            require(CommandValueRules.isVariableName(value)) { "予約済みまたは不正な変数名です" }
        }
        if (node.type == CommandType.TEMP_SET && key == "name") {
            require(CommandValueRules.isVariableName(TemporaryTemplate.normalized(value))) { "予約済みまたは不正な変数名です" }
        }
        if (node.type == CommandType.TEMP_SET && key == "tempType") {
            val type = requireNotNull(ValueType.parse(value)) { "不正な一時変数型です" }
            changeTemporaryType(node, type)
            return
        }
        if (node.type == CommandType.VARIABLE && key == "type") {
            val type = requireNotNull(ValueType.parse(value)) { "不正な変数型です" }
            changeTemporaryType(node, type)
            return
        }
        // 通常値を直接入力した時点で、同じ値欄の一時変数参照を解除します。
        // TEMP_SETのitemは「一時値そのもの」の入力であり、この切替を
        // 適用すると自分自身の保存経路を壊すため、temporaryValueBinding側で除外します。
        temporaryValueBinding(node, key)?.let { binding ->
            setTemporaryReference(node, binding.referenceKey, null)
            node.clearConfigured(binding.referenceKey)
        }
        node.params[key] = value
        if (value.isBlank()) {
            node.clearConfigured(key)
        } else {
            node.markConfigured(key)
        }
        if (node.type == CommandType.PARTICLE && key == ParticleSettings.PARAM_PARTICLE) {
            normalizeParticleData(node)
        }
    }

    /**
     * PARTICLEの種類変更を、旧種類の詳細データを引きずらない単一操作にします。
     *
     * 詳細データの型は種類ごとに変わります。データ不要な種類へ変えた後に旧値を
     * 残すと、非表示タブへの検証エラーと実行時の無表示スキップを起こします。
     * データを必要とする種類同士でも、旧値が新種類で解釈できなければ同じく
     * 表示・実行へ漏らさないため、両GUI共通の保存境界であるここで除去します。
     */
    private fun normalizeParticleData(node: CommandNode) {
        val particle = ParticleSettings.particle(node) ?: return
        if (!ParticleSettings.requiresData(particle)) {
            node.params.remove(ParticleSettings.PARAM_DATA)
            node.clearConfigured(ParticleSettings.PARAM_DATA)
            return
        }
        val raw = node.params[ParticleSettings.PARAM_DATA]
        if (!raw.isNullOrBlank() && ParticleSettings.parseData(particle, raw).isFailure) {
            node.params.remove(ParticleSettings.PARAM_DATA)
            node.clearConfigured(ParticleSettings.PARAM_DATA)
        }
    }

    /**
     * 図形設定の完了状態を、現形状が必要とする項目だけから判定します。
     *
     * 他形状の残存値は表示・検証・実行へ漏らさないため、完了判定の対象外に
     * します。形状未選択（POINT_SCATTER）の図形表示は未完了として扱います。
     * 終点・対角点は表示位置（geometryEnd）として独立に判定するため、
     * ここには含めません。
     */
    private fun isGeometryConfigured(node: CommandNode): Boolean {
        val geometry = ParticleGeometry
        val shape = geometry.shape(node)
        if (shape == ParticleShape.POINT_SCATTER) return false
        val numericKeys = when (shape) {
            ParticleShape.LINE -> emptyList()
            ParticleShape.CIRCLE ->
                listOf(geometry.PARAM_RADIUS, geometry.PARAM_AXIS_YAW, geometry.PARAM_AXIS_PITCH, geometry.PARAM_ROTATION)
            ParticleShape.ARC ->
                listOf(geometry.PARAM_RADIUS, geometry.PARAM_START_ANGLE, geometry.PARAM_END_ANGLE, geometry.PARAM_AXIS_YAW, geometry.PARAM_AXIS_PITCH, geometry.PARAM_ROTATION)
            ParticleShape.BOX -> listOf(geometry.PARAM_WIREFRAME)
            ParticleShape.SPHERE ->
                listOf(geometry.PARAM_RADIUS, geometry.PARAM_AXIS_YAW, geometry.PARAM_AXIS_PITCH, geometry.PARAM_ROTATION)
            ParticleShape.HELIX ->
                listOf(geometry.PARAM_RADIUS, geometry.PARAM_HEIGHT, geometry.PARAM_TURNS, geometry.PARAM_AXIS_YAW, geometry.PARAM_AXIS_PITCH, geometry.PARAM_ROTATION)
            ParticleShape.POINT_SCATTER -> emptyList()
        } + geometry.PARAM_STEP
        val numbersConfigured = numericKeys.any { key ->
            val value = node.params[key]
            value != null && value.isNotBlank() &&
                (value != node.type.defaults[key] || node.isExplicitlyConfigured(key))
        }
        val shapeConfigured = node.params[geometry.PARAM_SHAPE]?.isNotBlank() == true
        return shapeConfigured || numbersConfigured
    }

    /** 複数の文字列パラメータを同じ明示設定契約で保存します。 */
    fun setParameters(node: CommandNode, values: Map<String, String>) {
        values.forEach { (key, value) -> setParameter(node, key, value) }
    }

    /**
     * issue #10: 召喚種類の設定です。
     *
     * 種類変更時に新しい種類で利用できない個別設定を残さず、
     * staleな値を実行時まで持ち越しません。種類が確定できない場合
     * （空欄・不正ID）は検証層へ委ね、個別設定を削除しません。
     * 両GUIの召喚種類保存はこの入口を使い、分岐させません。
     */
    fun setSummonEntity(node: CommandNode, raw: String) {
        require(node.type == CommandType.SUMMON_ENTITY) { "召喚種類を持たないノードです: ${node.type}" }
        setParameter(node, "summonEntity", raw)
        val type = EntityDataSchema.knownSummonType(raw) ?: return
        pruneSummonData(node, type)
    }

    /**
     * issue #10: 指定種類で利用できない個別設定を除去します。
     *
     * 共通項目は種類を問わず残し、型固有項目だけを対象にします。
     * 明示設定の記録も同時に除去し、表示上の設定済み残骸を残しません。
     */
    fun pruneSummonData(node: CommandNode, entityType: org.bukkit.entity.EntityType) {
        val allowed = EntityDataSchema.propertiesFor(entityType).map { it.id }.toSet()
        node.params.keys
            .mapNotNull { key ->
                EntityDataSchema.summonDataIdOf(key)?.takeIf { it !in allowed }?.let { key }
            }
            .forEach { key ->
                node.params.remove(key)
                node.clearConfigured(key)
            }
    }

    /**
     * issue #34-1: 通常対象の読込先です。
     *
     * 内包対象の役割（位置・向き仕様の内部対象）はここでは扱いません。
     * 用途不適合の役割を通常対象へ流すと別フィールドの書き換えになるため、
     * 内包役割・未知役割は明示的に拒否し、[embeddedTargetSpec] の利用を強制します。
     */
    fun targetSpec(node: CommandNode, role: CommandSettingRole?): TargetSpec? = when (role) {
        null, CommandSettingRole.NODE_TARGET -> node.targetSpec
        CommandSettingRole.DESTINATION -> node.destinationTargetSpec
        CommandSettingRole.SECONDARY_TARGET -> node.secondaryTargetSpec
        CommandSettingRole.TEMPORARY_ENTITY -> node.temporaryEntityTargetSpec
        else -> error("${node.type} の通常対象として不適合な役割です: $role")
    }

    /**
     * 変数の型変更を、古い型の入力値が新しい型へ幽霊表示されない単一操作にします。
     *
     * 変数値は型によって入力経路も実行時の保存形式も変わります。型だけを書き換えて
     * 旧payloadを残すと、見えない値が実行・出力側へ流れます。型変更時は共通の
     * 可変部分を全消去し、次の型の入力を必ずその型の設定画面から開始させます。
     * TEMP_SET（tempType欄）と変数操作コマンド（type欄）の両方で使います。
     */
    fun changeTemporaryType(node: CommandNode, type: ValueType) {
        check(node.type == CommandType.TEMP_SET || node.type == CommandType.VARIABLE) {
            "一時変数型を持たないノードです: ${node.type}"
        }
        val key = if (node.type == CommandType.TEMP_SET) "tempType" else "type"
        val current = ValueType.parse(node.string(key, ValueType.NUMBER.name))
        if (current != type) {
            setOf(
                "value", "range", "x", "y", "z", "yaw", "pitch", "item", "itemData", "entityId",
                "sound", "volume", "effect", "level", "seconds",
            ).forEach(node.params::remove)
            // 型ごとの入力を切り替えた後に、旧型の生成方法だけが残る状態を防ぎます。
            node.params["changeMode"] = VariableChangeMode.ASSIGN.name
            node.temporaryEntityTargetSpec = null
            node.temporaryLocationPositionSpec = null
            node.temporaryLocationFacingSpec = null
            node.clearConfigured(
                "value", "range", "x", "y", "z", "yaw", "pitch", "item", "itemData", "entityId",
                "sound", "volume", "effect", "level", "seconds", "entity", "location",
                "locationPosition", "locationFacing",
            )
            node.markConfigured("changeMode")
        }
        node.params[key] = type.name
        node.markConfigured(key)
    }

    /**
     * issue #34-1: 通常対象の保存先です。
     *
     * 未知または用途不適合の役割を通常対象へ流さず明示的に拒否します。
     * 内包対象の役割は [setEmbeddedTargetSpec] を利用してください。
     *
     * issue #34-4: 一時変数参照の詳細条件は保存時に除去します。
     * issue #35-3: 固定実体（FIXED_ENTITY）は撤去しました。
     * 実行側が適用しない絞り込みを残さず、GUI・保存・実行の契約を一致させます。
     */
    fun setTargetSpec(node: CommandNode, role: CommandSettingRole?, spec: TargetSpec) {
        // TEMP_SET ENTITYは対象を複数保持できないTemporaryValueへ変換するため、
        // 共通TargetSpecの詳細条件はそのまま利用しつつ、解決数だけ1体へ正規化します。
        val normalizedSpec = if (role == CommandSettingRole.TEMPORARY_ENTITY) {
            pruneSingleReferenceFilters(spec.copy(limit = 1))
        } else {
            pruneSingleReferenceFilters(spec)
        }
        when (role) {
            null, CommandSettingRole.NODE_TARGET -> node.targetSpec = normalizedSpec
            CommandSettingRole.DESTINATION -> {
                node.destinationTargetSpec = normalizedSpec
                node.destinationSpec = null
            }
            CommandSettingRole.SECONDARY_TARGET -> node.secondaryTargetSpec = normalizedSpec
            CommandSettingRole.TEMPORARY_ENTITY -> node.temporaryEntityTargetSpec = normalizedSpec
            else -> error("${node.type} の通常対象として不適合な役割です: $role")
        }
        val configuredKey = configuredFieldKey("target", role)
        if (isTargetSpecConfigured(normalizedSpec)) {
            node.markConfigured(configuredKey)
        } else {
            // 方式だけ選択して必須の実値が未入力の場合は、選択履歴を
            // 「設定完了」として残しません。次の入力で完全なSpecになった時点で
            // 初めて明示設定へ昇格させます。
            node.clearConfigured(configuredKey)
        }
    }

    /**
     * issue #29: 内包対象を編集する役割かを返します。
     *
     * 位置・向きの各役割は対象指定を内部に持ち、通常対象画面へ進む経路を
     * 持ちません。TARGET_FILTERS画面はこれらの役割では内包対象を編集します。
     * 移動先は旧来の対象移動経路のため除外します。
     * issue #34-1: STRUCTURE_PLACE_POSITION（建造物設置位置）・GROUP_PIVOT
     * （群pivot）も位置設定木の内部に対象を持ちます。旧来の対象移動経路では
     * ないため内包対象として扱い、通常の targetSpec への読み書きを禁止します。
     */
    fun isEmbeddedTargetRole(role: CommandSettingRole?): Boolean = when (role) {
        CommandSettingRole.CONDITION_POSITION,
        CommandSettingRole.BLOCK_POSITION,
        CommandSettingRole.BLOCK_FROM,
        CommandSettingRole.BLOCK_TO,
        // issue #34-1: 建造物設置位置・群pivotも内包対象です。
        CommandSettingRole.STRUCTURE_PLACE_POSITION,
        CommandSettingRole.GROUP_PIVOT,
        CommandSettingRole.SOUND_POSITION,
        CommandSettingRole.PARTICLE_POSITION,
        CommandSettingRole.PARTICLE_END,
        CommandSettingRole.SUMMON_POSITION,
        CommandSettingRole.FIREWORK_POSITION,
        CommandSettingRole.TEMPORARY_LOCATION_POSITION,
        CommandSettingRole.EXECUTION_POSITION,
        CommandSettingRole.DESTINATION_FACING,
        CommandSettingRole.TEMPORARY_LOCATION_FACING,
        -> true
        else -> false
    }

    /** issue #29: 位置・向き仕様に内包された対象指定を返します。 */
    fun embeddedTargetSpec(node: CommandNode, role: CommandSettingRole?): TargetSpec? {
        if (!isEmbeddedTargetRole(role)) return null
        return when (role) {
            CommandSettingRole.DESTINATION_FACING,
            CommandSettingRole.TEMPORARY_LOCATION_FACING,
            -> facingSpec(node, role)?.target
            else -> positionSpec(node, role)?.target
        }
    }

    /**
     * issue #34-4: 単一参照の取得元に適用されない詳細条件を除去します。
     *
     * 一時変数参照は変数名だけで解決し、種類・距離・範囲・領域・タグ・名・
     * 基準位置などの絞り込みは実行時に適用されません。
     * issue #35-3: 固定実体（FIXED_ENTITY）は撤去しました。
     * 残したままにすると保持した条件を黙って無視することになるため、
     * 保存の共通入口で除去します。上限数・並べ替え・来歴は触りません。
     */
    fun pruneSingleReferenceFilters(spec: TargetSpec): TargetSpec {
        if (spec.kind != TargetKind.TEMPORARY) return spec
        return spec.copy(
            entityType = null,
            minimumDistance = null,
            maximumDistance = null,
            gameMode = null,
            tag = null,
            name = null,
            dx = null,
            dy = null,
            dz = null,
            region = null,
            searchOrigin = null,
        )
    }
    /**
     * issue #29: 内包対象だけを書き換えます。
     *
     * 種別・座標等の他要素は維持し、保存形式（PositionSpec/FacingSpecの
     * target部）は変えません。検証・実行・出力への影響はありません。
     * 向きでは注視・複写の種別を保持し、単一体に正規化します。
     *
     * issue #34-4: 固定実体・一時変数参照の詳細条件は保存時に除去します。
     *
     * @param facingKind 向き役割で種別を明示する場合に指定します（TARGET／COPY_ORIENTATION）。
     */
    fun setEmbeddedTargetSpec(
        node: CommandNode,
        role: CommandSettingRole?,
        spec: TargetSpec,
        facingKind: FacingKind? = null,
    ) {
        check(isEmbeddedTargetRole(role)) { "${node.type} の内包対象の役割が不正です: $role" }
        // issue #34-4: 内包対象も通常対象と同じ単一参照契約で除去します。
        val pruned = pruneSingleReferenceFilters(spec)
        when (role) {
            CommandSettingRole.DESTINATION_FACING,
            CommandSettingRole.TEMPORARY_LOCATION_FACING,
            -> {
                val resolvedKind = facingKind
                    ?: facingSpec(node, role)?.kind?.takeIf {
                        it == FacingKind.TARGET || it == FacingKind.COPY_ORIENTATION
                    } ?: FacingKind.TARGET
                require(resolvedKind == FacingKind.TARGET || resolvedKind == FacingKind.COPY_ORIENTATION) {
                    "向き参照先を持つ種別ではありません: $resolvedKind"
                }
                val base = facingSpec(node, role)
                    ?.takeIf { it.kind == resolvedKind } ?: FacingSpec(resolvedKind)
                setFacingSpec(node, base.copy(kind = resolvedKind, target = pruned.copy(limit = 1)), role)
            }
            else -> {
                val base = positionSpec(node, role)
                    ?.takeIf { it.kind == PositionKind.TARGET } ?: PositionSpec(PositionKind.TARGET)
                setPositionSpec(node, role, base.copy(kind = PositionKind.TARGET, target = pruned))
            }
        }
    }

    fun positionSpec(node: CommandNode, role: CommandSettingRole?): PositionSpec? = when (role) {
        CommandSettingRole.DESTINATION -> node.destinationSpec
        CommandSettingRole.SOUND_POSITION -> node.soundPositionSpec
        CommandSettingRole.PARTICLE_POSITION -> node.particlePositionSpec
        CommandSettingRole.PARTICLE_END -> node.particleEndPositionSpec
        CommandSettingRole.SUMMON_POSITION -> node.summonPositionSpec
        CommandSettingRole.FIREWORK_POSITION -> node.fireworkPositionSpec
        CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec
        CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec
        CommandSettingRole.BLOCK_FROM -> node.blockFromSpec
        CommandSettingRole.BLOCK_TO -> node.blockToSpec
        CommandSettingRole.STRUCTURE_PLACE_POSITION -> node.structurePlacePositionSpec
        CommandSettingRole.GROUP_PIVOT -> node.groupPivotPositionSpec
        CommandSettingRole.TEMPORARY_LOCATION_POSITION -> node.temporaryLocationPositionSpec
        CommandSettingRole.EXECUTION_POSITION -> node.executionPositionSpec
        else -> null
    }

    /**
     * 位置ドメインの現在種別を返します。
     *
     * 移動先だけは「座標」と「別エンティティ」が別の構造化値へ保存されます。
     * destinationTargetSpecをPositionSpecへ詰め替えて扱うと、対象設定後に親画面の
     * 選択色・現在値が未設定へ戻り、対象の詳細設定も別ドメインへ漏れます。
     * 表示・選択判定はこの共通関数を使い、実データはtargetSpec/positionSpecの
     * 各setterへ分けて保存します。
     */
    fun positionKind(node: CommandNode, role: CommandSettingRole?): PositionKind? = when (role) {
        CommandSettingRole.DESTINATION -> when {
            node.destinationTargetSpec != null -> PositionKind.TARGET
            else -> node.destinationSpec?.kind
        }
        CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec?.kind
        CommandSettingRole.SOUND_POSITION -> node.soundPositionSpec?.kind
        CommandSettingRole.PARTICLE_POSITION -> node.particlePositionSpec?.kind
        CommandSettingRole.PARTICLE_END -> node.particleEndPositionSpec?.kind
        CommandSettingRole.SUMMON_POSITION -> node.summonPositionSpec?.kind
        CommandSettingRole.FIREWORK_POSITION -> node.fireworkPositionSpec?.kind
        CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec?.kind
        CommandSettingRole.BLOCK_FROM -> node.blockFromSpec?.kind
        CommandSettingRole.BLOCK_TO -> node.blockToSpec?.kind
        CommandSettingRole.STRUCTURE_PLACE_POSITION -> node.structurePlacePositionSpec?.kind
        CommandSettingRole.GROUP_PIVOT -> node.groupPivotPositionSpec?.kind
        CommandSettingRole.TEMPORARY_LOCATION_POSITION -> node.temporaryLocationPositionSpec?.kind
        CommandSettingRole.EXECUTION_POSITION -> node.executionPositionSpec?.kind
        else -> null
    }

    fun setPositionSpec(node: CommandNode, role: CommandSettingRole?, spec: PositionSpec) {
        when (role) {
            CommandSettingRole.DESTINATION -> {
                node.destinationSpec = spec
                node.destinationTargetSpec = null
            }
            CommandSettingRole.SOUND_POSITION -> node.soundPositionSpec = spec
            CommandSettingRole.PARTICLE_POSITION -> node.particlePositionSpec = spec
            CommandSettingRole.PARTICLE_END -> node.particleEndPositionSpec = spec
            CommandSettingRole.SUMMON_POSITION -> node.summonPositionSpec = spec
            CommandSettingRole.FIREWORK_POSITION -> node.fireworkPositionSpec = spec
            CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec = spec
            CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec = spec
            CommandSettingRole.BLOCK_FROM -> node.blockFromSpec = spec
            CommandSettingRole.BLOCK_TO -> node.blockToSpec = spec
            CommandSettingRole.STRUCTURE_PLACE_POSITION -> node.structurePlacePositionSpec = spec
            CommandSettingRole.GROUP_PIVOT -> node.groupPivotPositionSpec = spec
            CommandSettingRole.TEMPORARY_LOCATION_POSITION -> node.temporaryLocationPositionSpec = spec
            CommandSettingRole.EXECUTION_POSITION -> node.executionPositionSpec = spec
            else -> error("${node.type} の位置設定役割が不正です: $role")
        }
        val configuredKey = configuredFieldKey(
            when (role) {
                CommandSettingRole.SOUND_POSITION -> "soundPosition"
                CommandSettingRole.PARTICLE_POSITION -> "particlePosition"
                CommandSettingRole.PARTICLE_END -> "particleEnd"
                CommandSettingRole.SUMMON_POSITION -> "summonPosition"
                CommandSettingRole.FIREWORK_POSITION -> "fireworkPosition"
                CommandSettingRole.TEMPORARY_LOCATION_POSITION -> "locationPosition"
                CommandSettingRole.EXECUTION_POSITION -> "executionPosition"
                CommandSettingRole.STRUCTURE_PLACE_POSITION -> "structurePlacePosition"
                CommandSettingRole.GROUP_PIVOT -> "pivotPosition"
                else -> "position"
            },
            role,
        )
        if (isPositionSpecConfigured(spec)) {
            node.markConfigured(configuredKey)
        } else {
            // COORDINATES/CAPTUREDの方式だけを選んだ段階では、座標や向きが
            // 未入力なので設定済みにしません。入力ダイアログの確定後だけ記録します。
            node.clearConfigured(configuredKey)
        }
    }

    fun facingSpec(node: CommandNode, role: CommandSettingRole? = null): FacingSpec? = when (role) {
        CommandSettingRole.DESTINATION_FACING -> node.destinationFacingSpec
        CommandSettingRole.TEMPORARY_LOCATION_FACING -> node.temporaryLocationFacingSpec
        else -> null
    }

    fun setFacingSpec(
        node: CommandNode,
        spec: FacingSpec,
        role: CommandSettingRole? = null,
    ) {
        check(
            role == CommandSettingRole.DESTINATION_FACING ||
                role == CommandSettingRole.TEMPORARY_LOCATION_FACING
        ) {
            "${node.type} の向き設定役割が不正です: $role"
        }
        if (role == CommandSettingRole.DESTINATION_FACING) {
            node.destinationFacingSpec = spec
            if (isFacingSpecConfigured(spec)) {
                node.markConfigured("destinationFacing")
            } else {
                node.clearConfigured("destinationFacing")
            }
        } else if (role == CommandSettingRole.TEMPORARY_LOCATION_FACING) {
            node.temporaryLocationFacingSpec = spec
            val configuredKey = configuredFieldKey("facing", role)
            if (isFacingSpecConfigured(spec)) {
                node.markConfigured(configuredKey)
            } else {
                node.clearConfigured(configuredKey)
            }
        }
    }

    /**
     * 設定画面で表示する値の状態を、文字列値と構造化値の両方から一元判定します。
     *
     * 既存JSONにはconfiguredFieldsがないため、まず構造化値／既定値との差を推定し、
     * 新しい画面から明示的に既定値を選んだ場合だけCommandNodeの明示記録を優先します。
     * ただし、必須の実値を持つ構造化値は明示フラグだけで完了扱いにしません。
     * 方式選択後に座標などが未入力の中間Specを保存できるため、実値の完全性を
     * 最初に確認してから、明示設定フラグを補助的に参照します。
     */
    fun isFieldConfigured(
        node: CommandNode,
        fieldKey: String,
        role: CommandSettingRole? = null,
    ): Boolean {
        // roleを省略したInventoryの一覧描画でも、コマンド型から保存先を復元します。
        // これをしないとBLOCK_OPERATIONのpositionが別の保存先として判定され、
        // InventoryとGestureで同じ値を表示していても選択状態だけがずれます。
        val effectiveRole = role ?: descriptor(node, fieldKey).role
        return when (fieldKey) {
            // issue #34-1: 内包役割の "target" は位置・向き仕様の内部対象を見ます。
            // targetSpec へ流すと別フィールド（ノード本体の対象）を参照するため、
            // 内包対象と通常対象をここで分岐します。
            "target" -> if (isEmbeddedTargetRole(effectiveRole)) {
                isTargetSpecConfigured(embeddedTargetSpec(node, effectiveRole))
            } else {
                isTargetSpecConfigured(targetSpec(node, effectiveRole))
            }
            // "entity" は一時対象指定の専用キーです。召喚の種類は v17 で
            // "summonEntity" へ分離したため、ここでは扱いません（汎用分岐へ）。
            "entity" -> isTargetSpecConfigured(node.temporaryEntityTargetSpec)
            "location" -> isPositionSpecConfigured(node.temporaryLocationPositionSpec) &&
                isFacingSpecConfigured(node.temporaryLocationFacingSpec)
            // "destination" はテレポート移動先の専用キーです。配置先は v17 で
            // "placeDestination" へ分離したため、ここでは扱いません（汎用分岐へ）。
            "destination" -> isPositionSpecConfigured(node.destinationSpec) ||
                isTargetSpecConfigured(node.destinationTargetSpec)
            "other" -> isTargetSpecConfigured(node.secondaryTargetSpec)
            "position" -> when (effectiveRole) {
                CommandSettingRole.DESTINATION -> isPositionSpecConfigured(node.destinationSpec) ||
                    isTargetSpecConfigured(node.destinationTargetSpec)
                CommandSettingRole.CONDITION_POSITION -> isPositionSpecConfigured(node.conditionPositionSpec)
                CommandSettingRole.BLOCK_POSITION -> isPositionSpecConfigured(node.blockPositionSpec)
                CommandSettingRole.BLOCK_FROM -> isPositionSpecConfigured(node.blockFromSpec)
                CommandSettingRole.BLOCK_TO -> isPositionSpecConfigured(node.blockToSpec)
                // issue #34-1: 建造物設置位置・群pivotも位置仕様の完全性で判定します。
                // 従来の else -> false では汎用 "position" キー経路の選択表示が
                // 未完了に固定されるため、保存先と同じ仕様を参照します。
                CommandSettingRole.STRUCTURE_PLACE_POSITION -> isPositionSpecConfigured(node.structurePlacePositionSpec)
                CommandSettingRole.GROUP_PIVOT -> isPositionSpecConfigured(node.groupPivotPositionSpec)
                CommandSettingRole.SOUND_POSITION -> isPositionSpecConfigured(node.soundPositionSpec)
                CommandSettingRole.PARTICLE_POSITION -> isPositionSpecConfigured(node.particlePositionSpec)
                CommandSettingRole.PARTICLE_END -> isPositionSpecConfigured(node.particleEndPositionSpec)
                CommandSettingRole.SUMMON_POSITION -> isPositionSpecConfigured(node.summonPositionSpec)
                CommandSettingRole.FIREWORK_POSITION -> isPositionSpecConfigured(node.fireworkPositionSpec)
                CommandSettingRole.EXECUTION_POSITION -> isPositionSpecConfigured(node.executionPositionSpec)
                CommandSettingRole.TEMPORARY_LOCATION_POSITION -> isPositionSpecConfigured(node.temporaryLocationPositionSpec)
                else -> false
            }
            "from" -> isPositionSpecConfigured(node.blockFromSpec)
            "to" -> isPositionSpecConfigured(node.blockToSpec)
            "destinationFacing" -> isFacingSpecConfigured(node.destinationFacingSpec)
            "facing" -> if (effectiveRole == CommandSettingRole.DESTINATION_FACING) {
                isFacingSpecConfigured(node.destinationFacingSpec)
            } else if (effectiveRole == CommandSettingRole.TEMPORARY_LOCATION_FACING) {
                isFacingSpecConfigured(node.temporaryLocationFacingSpec)
            } else {
                false
            }
            "soundPosition" -> isPositionSpecConfigured(node.soundPositionSpec)
            "particlePosition" -> isPositionSpecConfigured(node.particlePositionSpec)
            "particleEnd" -> isPositionSpecConfigured(node.particleEndPositionSpec)
            // #30: 図形の終点・対角点は表示位置として独立タブで編集します。
            "geometryEnd" -> isPositionSpecConfigured(node.particleEndPositionSpec)
            "summonPosition" -> isPositionSpecConfigured(node.summonPositionSpec)
            "fireworkPosition" -> isPositionSpecConfigured(node.fireworkPositionSpec)
            "executionPosition" -> isPositionSpecConfigured(node.executionPositionSpec)
            // issue #11: 建造物名・回転・反転・設置位置の明示設定です。
            // 回転・反転は明示記録の有無で判定します。既定値は保存用であり、
            // 未操作の新規ノードは未設定です（新規ノード可視性試験の契約）。
            "structure" -> node.string("structure").isNotBlank() && node.isExplicitlyConfigured("structure")
            "rotation" -> node.isExplicitlyConfigured("rotation")
            "mirror" -> node.isExplicitlyConfigured("mirror")
            "structurePlace" -> isPositionSpecConfigured(node.structurePlacePositionSpec)
            // issue #24: 群名・群操作の明示設定です。既定値は保存用であり、
            // 未操作の新規ノードは未設定です（#11 と同一契約）。
            "group" -> node.string("group").isNotBlank() && node.isExplicitlyConfigured("group")
            "pivot" -> isPositionSpecConfigured(node.groupPivotPositionSpec)
            // 再生位置と対象は、対象と再生位置のどちらかが揃えば設定済みです。
            // 停止操作では再生位置を使用しないため、対象だけを確認します。
            "soundTargetPosition" -> if (SoundSettings.operation(node) ==
                SoundOperation.STOP
            ) {
                isTargetSpecConfigured(node.targetSpec)
            } else {
                isTargetSpecConfigured(node.targetSpec) || isPositionSpecConfigured(node.soundPositionSpec)
            }
            // 効果音設定は操作ごとに束ねます。再生はID・音量・ピッチ、停止は
            // ID・カテゴリのいずれかがあれば設定済みです。
            "soundSetting" -> if (SoundSettings.operation(node) ==
                SoundOperation.STOP
            ) {
                node.string("sound").isNotBlank() || node.string("soundCategory").isNotBlank()
            } else {
                node.string("sound").isNotBlank() ||
                    listOf("volume", "pitch").any { key ->
                        val value = node.params[key]
                        value != null && value.isNotBlank() &&
                            (value != node.type.defaults[key] || node.isExplicitlyConfigured(key))
                    }
            }
            // 表示対象は、対象の指定があれば設定済みです。
            // #30で表示方式の選択は廃止し、対象指定だけを扱います。
            "particleDisplay" -> isTargetSpecConfigured(node.targetSpec)
            // パーティクル種類と詳細データは同じタブで編集します。
            // 既定値のままでは完了扱いにしません。
            "particle" -> {
                val id = node.params[ParticleSettings.PARAM_PARTICLE]
                val idConfigured = id != null && id.isNotBlank() &&
                    (id != node.type.defaults[ParticleSettings.PARAM_PARTICLE] ||
                        node.isExplicitlyConfigured(ParticleSettings.PARAM_PARTICLE))
                val particle = ParticleSettings.particle(node)
                idConfigured ||
                    (particle != null && ParticleSettings.requiresData(particle) &&
                        node.params[ParticleSettings.PARAM_DATA]?.isNotBlank() == true)
            }
            // 図形設定は、形状と現形状の数値・終端位置が揃えば設定済みです。
            "geometrySettings" -> isGeometryConfigured(node)
            "particleParameters" -> listOf(
                ParticleSettings.PARAM_DELTA_X,
                ParticleSettings.PARAM_DELTA_Y,
                ParticleSettings.PARAM_DELTA_Z,
                ParticleSettings.PARAM_SPEED,
                ParticleSettings.PARAM_COUNT,
            ).any { key ->
                val value = node.params[key]
                value != null && value.isNotBlank() &&
                    (value != node.type.defaults[key] || node.isExplicitlyConfigured(key))
            }
            "particleData" -> {
                val particle = ParticleSettings.particle(node)
                // 詳細データを必要としない種類は入力欄自体を出さないため、残存値が
                // あっても「設定済み」にしません。除去は種類変更時の正規化が担います。
                if (particle == null || !ParticleSettings.requiresData(particle)) false
                else node.params[ParticleSettings.PARAM_DATA]?.isNotBlank() == true
            }
            // 花火の中身は、素材IDと実体のどちらかがあれば設定済みです。
            "fireworkContents" -> node.string("fireworkItem").isNotBlank() ||
                node.string("fireworkData").isNotBlank()
            "item" -> typedValueConfigured(node, "item") || node.string("itemData").isNotBlank()
            "block", "sound", "effect" -> typedValueConfigured(node, fieldKey)
            "diskId" -> node.string("diskId").isNotBlank() || node.snapshot != null
            "condition" -> conditionDetailConfigured(node)
            else -> {
                val value = node.params[fieldKey] ?: return false
                value.isNotBlank() &&
                    (value != node.type.defaults[fieldKey] ||
                        node.isExplicitlyConfigured(configuredFieldKey(fieldKey, effectiveRole)))
            }
        }
    }

    /** 対象の詳細条件は各項目が独立して設定されるため、複数選択色へ投影します。 */
    fun isTargetFilterConfigured(
        node: CommandNode,
        role: CommandSettingRole?,
        parameter: String,
    ): Boolean {
        // issue #29: 位置・向きの役割では内包対象を参照します。
        val spec = if (isEmbeddedTargetRole(role)) {
            embeddedTargetSpec(node, role)
        } else {
            targetSpec(node, role)
        } ?: return false
        val explicitlyConfigured = node.isExplicitlyConfigured(configuredFieldKey("target.$parameter", role))
        return when (parameter) {
            "kind" -> true
            "entityType" -> !spec.entityType.isNullOrBlank()
            "distance" -> spec.minimumDistance != null || spec.maximumDistance != null
            "range", "dx", "dy", "dz" -> when (parameter) {
                "dx" -> spec.dx != null
                "dy" -> spec.dy != null
                "dz" -> spec.dz != null
                else -> spec.dx != null || spec.dy != null || spec.dz != null
            }
            "limit" -> spec.limit != null
            // sortだけは入力欄ではなく択一操作です。既定値を明示選択した履歴も
            // 保持しますが、文字列・数値入力項目では空値を完了扱いにしません。
            "sort" -> spec.sort != TargetSort.NEAREST || explicitlyConfigured
            "gameMode" -> !spec.gameMode.isNullOrBlank()
            "tag" -> !spec.tag.isNullOrBlank()
            "name" -> !spec.name.isNullOrBlank()
            // issue #4: 明示領域（`region` 本体を含む）とその子選択肢は、
            // 親の領域設定から導出する共有判定へ寄せます。
            else -> isRegionFilterConfigured(spec, parameter)
        }
    }

    /**
     * issue #4・#5: 領域とその子選択肢の設定状態です。
     *
     * 子選択肢ID（`region`／`region-anchor:` 等）は領域設定から導出し、
     * 親同様に表示・検証・実行の正本だけを見ます。対象絞り込みと条件の
     * 両系統で共有し、分岐を増やしません。
     */
    fun isRegionConfigured(region: RegionSpec?, parameter: String): Boolean {
        val active = region?.takeIf { it.hasAnySetting() }
        return when {
            parameter == "region" -> active != null
            parameter.startsWith("region-anchor:") ->
                active?.anchor?.kind?.name == parameter.removePrefix("region-anchor:")
            parameter == "region-range" ->
                active?.let { it.dx != null || it.dy != null || it.dz != null } == true
            parameter == "region-distance" ->
                active?.minimumDistance != null || active?.maximumDistance != null
            parameter == "region-position" -> active?.anchor?.position != null
            parameter == "region-temp" -> !active?.anchor?.tempName.isNullOrBlank()
            parameter.startsWith("region-target-preset:") ->
                active?.anchor?.kind == RegionAnchorKind.TARGET_ENTITY &&
                    SettingPresets.matchTargetPreset(active.anchor.target)?.name ==
                    parameter.removePrefix("region-target-preset:")
            else -> false
        }
    }

    private fun isRegionFilterConfigured(spec: TargetSpec, parameter: String): Boolean =
        isRegionConfigured(spec.region, parameter)

    private fun conditionDetailConfigured(node: CommandNode): Boolean =
        isTargetSpecConfigured(node.targetSpec) || isPositionSpecConfigured(node.conditionPositionSpec) ||
            node.selectedControlBlockStates().isNotEmpty() ||
            node.params.any { (key, value) ->
                key in setOf("sneaking", "variable", "operator", "value", "block", "item", "itemData") &&
                    value.isNotBlank() &&
                    (value != node.type.defaults[key] || node.isExplicitlyConfigured(key))
            }

    /** 一時変数参照だけは、変数名がなければ実行対象が確定しません。 */
    internal fun isTargetSpecConfigured(spec: TargetSpec?): Boolean = spec != null && when (spec.kind) {
        TargetKind.TEMPORARY -> !spec.tempName.isNullOrBlank() &&
            CommandValueRules.isVariableName(TemporaryTemplate.normalized(spec.tempName))
        else -> true
    }

    /** 座標・捕捉方式は必須値が揃った場合だけ設定完了とします。 */
    private fun isPositionSpecConfigured(spec: PositionSpec?): Boolean = spec != null && when (spec.kind) {
        PositionKind.COORDINATES -> listOf(spec.x, spec.y, spec.z).all { it?.isFinite() == true }
        PositionKind.CAPTURED -> listOf(spec.x, spec.y, spec.z).all { it?.isFinite() == true } &&
            listOf(spec.yaw, spec.pitch).all { it?.isFinite() == true }
        PositionKind.TEMPORARY -> !spec.tempName.isNullOrBlank() &&
            CommandValueRules.isVariableName(TemporaryTemplate.normalized(spec.tempName))
        else -> true
    }

    /** 座標・数値回転方式は入力値が揃った場合だけ設定完了とします。注視・複写は単一体参照の完全性で判定します。 */
    private fun isFacingSpecConfigured(spec: FacingSpec?): Boolean = spec != null && when (spec.kind) {
        FacingKind.COORDINATES -> listOf(spec.x, spec.y, spec.z).all { it?.isFinite() == true }
        FacingKind.ROTATION -> listOf(spec.yaw, spec.pitch).all { it?.isFinite() == true }
        FacingKind.TEMPORARY -> !spec.tempName.isNullOrBlank() &&
            CommandValueRules.isVariableName(TemporaryTemplate.normalized(spec.tempName))
        FacingKind.TARGET, FacingKind.COPY_ORIENTATION -> isTargetSpecConfigured(spec.target)
    }

    /**
     * 位置設定の表示・選択用完全性を返します。
     *
     * 座標・一時変数は必須値の有無を要求し、対象由来は参照先の設定済みで判定します。
     * 選択表示されていても未完了の項目は未完了扱いにします。
     */
    internal fun isPositionSpecComplete(spec: PositionSpec?): Boolean =
        isPositionSpecConfigured(spec) && (spec?.kind != PositionKind.TARGET || isTargetSpecConfigured(spec.target))

    /**
     * 向き設定の表示・選択用完全性を返します。
     *
     * 座標・角度・一時変数は必須値の有無を要求し、注視・複写は参照先の設定済みで判定します。
     */
    internal fun isFacingSpecComplete(spec: FacingSpec?): Boolean = isFacingSpecConfigured(spec)

    fun allowedVariableOperations(type: ValueType): List<VariableOperation> =
        VariableOperation.entries

    /**
     * 対象種別に対して詳細条件が意味を持つかを判定します。
     * 実行側の解決（matches）に合わせ、プレイヤー種別へentityType、
     * エンティティの種類へgameModeを指定しても解決しないため、GUIでは提示しません。
     *
     * issue #34-4: 一時変数参照は詳細条件を使いません。実行側はこの取得元に
     * 絞り込みを適用しないため、GUIでも提示しません。保存時には残存条件を
     * 除去し、保持した条件を黙って無視しないようにします。
     * issue #35-3: 固定実体（FIXED_ENTITY）は撤去しました。
     */
    fun targetFilterApplies(kind: TargetKind?, parameter: String): Boolean {
        if (kind == TargetKind.TEMPORARY) {
            return parameter == "kind"
        }
        return when (parameter) {
            "kind" -> kind != null
            "entityType" -> kind in ENTITY_KINDS
            "gameMode" -> kind in PLAYER_KINDS
            else -> true
        }
    }

    /**
     * 周囲系（付近プレイヤー／付近エンティティ）かを返します。
     *
     * 周囲系の詳細設定項目は最大距離だけとし、他の絞り込みは提示しません。
     */
    fun isNearbyKind(kind: TargetKind?): Boolean =
        kind == TargetKind.NEARBY_PLAYERS || kind == TargetKind.NEARBY_ENTITIES

    /**
     * 詳細画面に並べる絞り込み項目IDを返します。
     *
     * 周囲系は最大距離だけ、単一参照では上限数を除外し、それ以外は
     * 対象種別ごとの適用可否に従います。表示順序は固定です。
     */
    fun visibleTargetFilterIds(kind: TargetKind?, singleOnly: Boolean = false): List<String> =
        listOf("entityType", "distance", "range", "region", "limit", "sort", "gameMode", "tag", "name")
            .filter { id -> !isNearbyKind(kind) || id == "distance" }
            .filter { id -> !singleOnly || id != "limit" }
            .filter { id -> targetFilterApplies(kind, id) }

    /**
     * 対象種別と詳細条件の境界を共有します。
     * 親画面の種別選択後に詳細子画面へ進めるかを、描画側と入力状態遷移側で
     * 別々に持つと、表示された「詳細設定」が開けない対象が生まれるためです。
     *
     * issue #34-4: 一時変数参照は詳細条件を持たないため、詳細子画面へ進めません。
     * issue #35-3: 固定実体（FIXED_ENTITY）は撤去しました。
     */
    fun targetSupportsDetailedFilters(kind: TargetKind?): Boolean =
        kind != null && kind != TargetKind.TEMPORARY &&
            kind in FILTERABLE_TARGET_KINDS

    /**
     * issue #4 の領域基準の表示名キーです。
     *
     * Gesture・Inventoryの両系統で同じ名称を使い、基準名の分岐を増やしません。
     * Name は固定ラベルの識別に使い、現在値・状態はLore側へ分離します。
     */
    fun regionAnchorNameKey(kind: RegionAnchorKind): LocalizationKey<String> = when (kind) {
        RegionAnchorKind.CONTROL_BLOCK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REGION_ANCHOR_CONTROL_BLOCK
        RegionAnchorKind.FIXED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REGION_ANCHOR_FIXED
        RegionAnchorKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REGION_ANCHOR_TEMPORARY
        RegionAnchorKind.TARGET_ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REGION_ANCHOR_TARGET_ENTITY
    }

    /**
     * issue #4 の明示領域の要約文です。
     *
     * 基準名・範囲・距離を一つの表示へまとめ、Gesture・Inventoryの両系統で
     * 同じ文面を使います。未設定時はnullを返し、空行を残しません。
     */
    fun formatRegionSummary(
        region: RegionSpec?,
        text: (LocalizationKey<String>) -> String,
    ): String? {
        val active = region?.takeIf { it.hasAnySetting() } ?: return null
        fun format(value: Double?): String = value?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        }.orEmpty()
        val unset = text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
        val parts = mutableListOf(text(regionAnchorNameKey(active.anchor.kind)))
        val extents = listOf(
            "${text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX)}=${format(active.dx).ifBlank { unset }}",
            "${text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DY)}=${format(active.dy).ifBlank { unset }}",
            "${text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DZ)}=${format(active.dz).ifBlank { unset }}",
        ).joinToString(" / ")
        parts += extents
        if (active.minimumDistance != null || active.maximumDistance != null) {
            parts += "${format(active.minimumDistance)}..${format(active.maximumDistance)}"
        }
        return parts.joinToString(" / ")
    }

    /**
     * プログラム全体の編集を共通保存境界へ通します。
     *
     * 名前・タイマー・有効化方式を画面ごとに直接保存すると、変更履歴・配置表示・
     * 実行時状態の更新順序が分岐します。ここで正本の独立コピーを読み込み、保存後
     * の補助処理も共通化します。
     */
    fun updateScript(
        plugin: KantanCommanderPlugin,
        scriptId: UUID,
        editorId: UUID? = null,
        expectedRevision: Long? = null,
        afterSave: (DiskScript) -> Unit = {},
        change: (DiskScript) -> Unit,
    ): Boolean {
        val script = plugin.scripts.update(scriptId, editorId, expectedRevision) { current ->
            change(current)
            current
        } ?: return false
        afterSave(script)
        refreshDisplays(plugin, script.id)
        // プログラム名・タイマーはノードを変更しないため差分は空集合です。
        plugin.gestureEditor.refreshForScript(script.id, changedNodeIds = emptySet())
        return true
    }

    /**
     * issue #8 のディスク引数定義を両GUIが同じ保存境界で更新します。
     *
     * 引数の追加・改名・型変更・削除を一つの操作として扱い、読み込み・保存・
     * 表示更新をまとめます。名前の重複・不正は検証層でも拒否しますが、
     * 保存前にここでも検査し、不正な定義を保存しません。
     */
    fun updateDiskArguments(
        plugin: KantanCommanderPlugin,
        scriptId: UUID,
        editorId: UUID? = null,
        expectedRevision: Long? = null,
        change: (MutableList<DiskArgument>) -> Unit,
    ): Boolean {
        val current = plugin.scripts.load(scriptId) ?: return false
        val candidate = current.arguments.toMutableList()
        change(candidate)
        val names = candidate.map { it.name }
        if (names.any { !CommandValueRules.isVariableName(it) }) return false
        if (names.size != names.toSet().size) return false
        return updateScript(
            plugin,
            scriptId,
            editorId,
            expectedRevision = expectedRevision,
        ) { script ->
            script.arguments = candidate.toList()
        }
    }

    /**
     * issue #8 の呼び出し引数のリテラルが型に適合するかを判定します。
     *
     * 検証層と同じ規則であり、両GUIの入力検証で共有します。複合型は変数指定だけを
     * 受け付けるため、ここでは数値リテラルだけを特別扱いします。
     */
    fun isCallArgumentLiteralValid(type: ValueType, raw: String): Boolean = when (type) {
        ValueType.NUMBER -> CommandValueRules.parseFiniteDouble(raw) != null ||
            VariableTemplate.isSingleReference(raw) ||
            TemporaryTemplate.isSingleReference(raw)
        else -> !VariableTemplate.hasMalformedReference(raw) &&
            !TemporaryTemplate.hasMalformedReference(raw)
    }

    /** インベントリ／ジェスチャー共通のグラフ更新処理です。 */
    fun <T : Any> updateGraph(
        plugin: KantanCommanderPlugin,
        scriptId: UUID,
        editorId: UUID? = null,
        expectedRevision: Long? = null,
        change: (CommandGraph) -> T?,
    ): T? {
        // 配布時の破棄対象にする変更ノード集合です。保存確定後にだけ使います。
        var changedNodeIds: Set<UUID>? = null
        val updated = plugin.scripts.update(scriptId, editorId, expectedRevision) { script ->
            val before = script.graph
            val beforeSignature = GraphStructureFingerprint.fingerprint(before)
            val candidateGraph = before.deepCopy()
            val result = change(candidateGraph) ?: return@update null
            // 描画セルの衝突・幅・高さ検証を保存前に必ず通し、両GUIの経路編集で
            // 「保存後にだけ描画不能になる」差を作りません。
            // issue #15 の差分化として、接続構造が変わらない値変更では配置計算を
            // 省略します。署名が同じ間は配置が変わらないため検証の意味がなく、
            // 構造変更時だけ全量レイアウトします。
            if (EditorRenderPlanner.needsPreSaveLayout(beforeSignature, GraphStructureFingerprint.fingerprint(candidateGraph))) {
                GraphLayoutEngine.layout(candidateGraph)
            }
            // 開いているeditorがいるときだけ差分を特定します。誰も開いていない
            // 保存では配布自体が空振りのため、差分計算のコストをかけません。
            changedNodeIds = if (plugin.gestureEditor.hasOpenEditorForScript(scriptId)) {
                GraphChangeClassifier.changedNodeIds(before, candidateGraph)
            } else {
                null
            }
            script.graph = candidateGraph
            result
        } ?: return null
        refreshDisplays(plugin, scriptId)
        plugin.gestureEditor.refreshForScript(scriptId, changedNodeIds = changedNodeIds)
        return updated
    }

    /** インベントリ／ジェスチャー共通のノード保存処理です。配置済み表示も更新します。 */
    fun updateNode(
        plugin: KantanCommanderPlugin,
        context: CommandSettingContext,
        configuredFields: Set<String> = emptySet(),
        editorId: UUID? = null,
        expectedRevision: Long? = null,
        change: (CommandNode) -> Unit,
    ): CommandNode? = updateGraph(plugin, context.scriptId, editorId, expectedRevision) { graph ->
        graph.nodes[context.nodeId]?.also { node ->
            change(node)
            node.markConfigured(
                *configuredFields
                    .map { configuredFieldKey(it, context.role) }
                    .toTypedArray(),
            )
        }
    }

    /** プログラム名を両GUI共通の更新入口から変更します。 */
    fun updateScriptName(
        plugin: KantanCommanderPlugin,
        scriptId: UUID,
        name: String,
        editorId: UUID? = null,
        expectedRevision: Long? = null,
    ): Boolean {
        require(name.isNotBlank()) { "プログラム名が空です" }
        return updateScript(plugin, scriptId, editorId, expectedRevision = expectedRevision) { it.name = name }
    }

    /**
     * プログラム全体設定である起動方法も、両GUIが同じ保存境界を通ります。
     *
     * 起動方法の変更は、読み込み・検証・保存・実行時登録更新・表示更新を
     * 一つの操作として扱います。明示の起動方法を保存し、従来欄（`timer`／
     * `activation`）も整合させるため、旧来の実行経路・表示・出力が
     * 読み替えなしに追従します。間隔は小数秒（tick境界）で受け付け、
     * 0秒は受け付けません。上限は86400秒です。
     */
    fun updateTrigger(
        plugin: KantanCommanderPlugin,
        scriptId: UUID,
        trigger: ScriptTrigger,
        editorId: UUID? = null,
        expectedRevision: Long? = null,
    ): Boolean {
        val normalized = trigger.normalized()
        if (normalized.kind == TriggerKind.TIMER) {
            require(CommandValueRules.isTimerSeconds(normalized.intervalSeconds)) {
                "タイマー間隔は0より大きく${MAX_TIMER_SECONDS}秒以下のtick境界（0.05秒単位）で指定してください"
            }
        }
        require(normalized.kind != TriggerKind.EVENT || normalized.event != null) {
            "イベント起動には種別の指定が必要です"
        }
        if (normalized.kind == TriggerKind.EVENT && normalized.event != null) {
            require(normalized.conditions.validationProblem(normalized.event) == null) {
                normalized.conditions.validationProblem(normalized.event).orEmpty()
            }
        }
        return updateScript(
            plugin,
            scriptId,
            editorId,
            expectedRevision = expectedRevision,
            afterSave = {
                plugin.resetActivationTiming(it.id)
                // issue #7: 起動方法の変更をイベント起動の登録へ反映します。
                plugin.refreshEventTriggerIndex()
            },
        ) { script ->
            script.trigger = normalized
            // 従来欄も整合させ、旧来の実行経路・表示・出力が追従できるようにします。
            when (normalized.kind) {
                TriggerKind.TIMER -> {
                    script.timer.intervalSeconds = normalized.intervalSeconds
                    script.timer.enabled = true
                }
                TriggerKind.REDSTONE, TriggerKind.EVENT -> {
                    script.timer.enabled = false
                    script.activation = ActivationMode.NEEDS_REDSTONE
                }
            }
        }
    }

    /** 保存成功後だけ配置表示を更新し、補助表示の失敗を設定保存へ波及させません。 */
    private fun refreshDisplays(plugin: KantanCommanderPlugin, scriptId: UUID) {
        // 補助表示の失敗は定常のためコンソールへ出しません。
        runCatching { plugin.placements.refreshDisplaysForScript(scriptId) }
    }

    private fun text() = CommandSettingDescriptor(CommandSettingEditor.TEXT)

    /**
     * 構造化フィールドの明示設定キーを、保存先の役割ごとに名前空間化します。
     *
     * issue #34-1: 内包対象の詳細条件キー（target.xxx）は通常対象と名前空間を
     * 分離します。分離しないと pivot／設置位置の対象条件と群・ノード本体の
     * 対象条件が同じ明示設定フラグを共有し、設定済み表示が相互に汚染されます。
     */
    private fun configuredFieldKey(fieldKey: String, role: CommandSettingRole?): String {
        fun namespaced(prefix: String): String =
            if (fieldKey.startsWith("target.")) "$prefix.$fieldKey" else prefix
        return when (role) {
            CommandSettingRole.CONDITION_POSITION -> if (fieldKey.startsWith("target.")) {
                "condition.$fieldKey"
            } else "condition.position"
            CommandSettingRole.SOUND_POSITION -> namespaced("soundPosition")
            CommandSettingRole.PARTICLE_POSITION -> namespaced("particlePosition")
            CommandSettingRole.PARTICLE_END -> namespaced("particleEnd")
            CommandSettingRole.SUMMON_POSITION -> namespaced("summonPosition")
            CommandSettingRole.FIREWORK_POSITION -> namespaced("fireworkPosition")
            CommandSettingRole.TEMPORARY_ENTITY -> "entity"
            CommandSettingRole.TEMPORARY_LOCATION -> "location"
            CommandSettingRole.TEMPORARY_LOCATION_POSITION -> namespaced("locationPosition")
            CommandSettingRole.TEMPORARY_LOCATION_FACING -> "locationFacing"
            CommandSettingRole.EXECUTION_POSITION -> namespaced("executionPosition")
            CommandSettingRole.DESTINATION -> if (fieldKey.startsWith("target.")) {
                "destination.$fieldKey"
            } else "destination"
            CommandSettingRole.SECONDARY_TARGET -> if (fieldKey.startsWith("target.")) {
                "other.$fieldKey"
            } else "other"
            CommandSettingRole.STRUCTURE_PLACE_POSITION -> namespaced("structurePlace")
            CommandSettingRole.GROUP_PIVOT -> namespaced("pivot")
            CommandSettingRole.BLOCK_POSITION -> namespaced("blockPosition")
            CommandSettingRole.BLOCK_FROM -> namespaced("blockFrom")
            CommandSettingRole.BLOCK_TO -> namespaced("blockTo")
            else -> fieldKey
        }
    }

    /** ITEM／BLOCKの主値に対する完了判定を共通化します。 */
    private fun typedValueConfigured(node: CommandNode, fieldKey: String): Boolean {
        return when (temporaryValueSource(node, fieldKey)) {
            CommandValueSource.TEMPORARY -> temporaryValueReference(node, fieldKey) != null
            CommandValueSource.LITERAL -> when (fieldKey) {
                "item" -> node.string("item").isNotBlank() || node.string("itemData").isNotBlank()
                else -> node.string(fieldKey).isNotBlank()
            }
            null -> node.string(fieldKey).isNotBlank()
        }
    }

    private val FILTERABLE_TARGET_KINDS = PLAYER_KINDS + ENTITY_KINDS
}
