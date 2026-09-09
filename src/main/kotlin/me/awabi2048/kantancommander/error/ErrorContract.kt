package me.awabi2048.kantancommander.error

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import java.util.UUID

/**
 * issue #6 の検証フェーズ分類です。
 *
 * - EDIT_TIME: 入力直後に確定できる不正（数値範囲外・必須不足・明らかな型不一致）。
 * - PERSISTED_STRUCTURAL: 保存データ自体の整合（broken relation・unknown enum・dangling reference）。
 * - PREFLIGHT: 実行開始前に確定できる不正（program missing・signature mismatch・static 権限制限）。
 * - RUNTIME: 実ワールド状態が必要なもの（Entity消失・target解決・capability・Trigger不足）。
 */
enum class ValidationPhase {
    EDIT_TIME,
    PERSISTED_STRUCTURAL,
    PREFLIGHT,
    RUNTIME,
}

/**
 * issue #6 の深刻度分類です。
 *
 * - ERROR: 実行継続不可。チャット原則でも通常実行時に表示します。
 * - WARNING: 部分適用・skip ありで継続可。テスト実行のログ出力時のみ表示します。
 * - INFO: 正常 empty 等の情報。テスト実行のログ出力時のみ表示します。
 */
enum class ErrorSeverity {
    ERROR,
    WARNING,
    INFO,
}

/**
 * issue #6 の失敗時挙動分類です。
 *
 * PARTIAL_APPLY は mixed/dynamic target で一部対象だけが capability 対応する
 * 操作のための枠組みです。#6 では器と計数定義までとし、本体適用ロジックは
 * #21 側に委ねます。
 */
enum class FailurePolicy {
    STOP_PROGRAM,
    SKIP_COMMAND,
    FALSE_BRANCH,
    RETURN_ERROR,
    EMPTY_RESULT,
    PARTIAL_APPLY,
}

/**
 * issue #6 の共通エラーコードです。
 *
 * message 文字列ではなく code/phase をテスト可能にするため、実行・検証の
 * 分岐条件はこのコードで判定します。表示文は [displayKey] 経由で必ず
 * 生成済み LocalizationKey へ解決し、原文フォールバックは行いません。
 */
enum class ErrorCode(
    val code: String,
    val defaultSeverity: ErrorSeverity,
    val defaultPhase: ValidationPhase,
    val defaultPolicy: FailurePolicy,
) {
    // 汎用・既存理由との互換。
    COMMAND_FAILED("command_failed", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.STOP_PROGRAM),
    NODE_FAILED("node_failed", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.STOP_PROGRAM),
    INVALID_SCRIPT("invalid_script", ErrorSeverity.ERROR, ValidationPhase.PREFLIGHT, FailurePolicy.STOP_PROGRAM),
    OUTSIDE_MYWORLD("outside_myworld", ErrorSeverity.ERROR, ValidationPhase.PREFLIGHT, FailurePolicy.STOP_PROGRAM),
    MANUAL_STOP("manual_stop", ErrorSeverity.INFO, ValidationPhase.RUNTIME, FailurePolicy.STOP_PROGRAM),
    COMMAND_LIMIT("command_limit", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.STOP_PROGRAM),
    MISSING_NODE("missing_node", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    INVALID_WAIT_SECONDS("invalid_wait_seconds", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.STOP_PROGRAM),
    TEST_START_REJECTED("test_start_rejected", ErrorSeverity.WARNING, ValidationPhase.PREFLIGHT, FailurePolicy.RETURN_ERROR),

    // 検証フェーズ固有。
    TIMER_CONFLICT("timer_conflict", ErrorSeverity.ERROR, ValidationPhase.PREFLIGHT, FailurePolicy.STOP_PROGRAM),
    TIMER_RANGE("timer_range", ErrorSeverity.ERROR, ValidationPhase.EDIT_TIME, FailurePolicy.RETURN_ERROR),
    GRAPH_CYCLE("graph_cycle", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    GRAPH_UNREACHABLE("graph_unreachable", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    GRAPH_DUPLICATE_ID("graph_duplicate_id", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    GRAPH_KEY_MISMATCH("graph_key_mismatch", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    GRAPH_UNKNOWN_REFERENCE("graph_unknown_reference", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    GRAPH_INVALID_PAIR("graph_invalid_pair", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    GRAPH_MULTIPLE_PARENTS("graph_multiple_parents", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    GRAPH_DEPTH_EXCEEDED("graph_depth_exceeded", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    GRAPH_NODE_LIMIT("graph_node_limit", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    PERSISTED_SHAPE_INVALID("persisted_shape_invalid", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    UNKNOWN_ENUM_VALUE("unknown_enum_value", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    DANGLING_REFERENCE("dangling_reference", ErrorSeverity.ERROR, ValidationPhase.PERSISTED_STRUCTURAL, FailurePolicy.STOP_PROGRAM),
    REQUIRED_PROGRAM_MISSING("required_program_missing", ErrorSeverity.ERROR, ValidationPhase.PREFLIGHT, FailurePolicy.STOP_PROGRAM),
    DISK_CALL_SIGNATURE_MISMATCH("disk_call_signature_mismatch", ErrorSeverity.ERROR, ValidationPhase.PREFLIGHT, FailurePolicy.STOP_PROGRAM),
    STATIC_PERMISSION_DENIED("static_permission_denied", ErrorSeverity.ERROR, ValidationPhase.PREFLIGHT, FailurePolicy.STOP_PROGRAM),
    REQUIRED_PROFILE_MISSING("required_profile_missing", ErrorSeverity.ERROR, ValidationPhase.PREFLIGHT, FailurePolicy.STOP_PROGRAM),

    // Runtime 解決の区別。empty/unresolved/unsupported/mismatch/invalid を分離します。
    TARGET_EMPTY("target_empty", ErrorSeverity.INFO, ValidationPhase.RUNTIME, FailurePolicy.EMPTY_RESULT),
    TARGET_SPEC_INVALID("target_spec_invalid", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.RETURN_ERROR),
    TEMPORARY_UNRESOLVED("temporary_unresolved", ErrorSeverity.WARNING, ValidationPhase.RUNTIME, FailurePolicy.SKIP_COMMAND),
    FIXED_ENTITY_UNRESOLVED("fixed_entity_unresolved", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.STOP_PROGRAM),
    VARIABLE_UNDEFINED("variable_undefined", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.RETURN_ERROR),
    VARIABLE_SCOPE_MISSING("variable_scope_missing", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.RETURN_ERROR),
    VARIABLE_OWNER_MISSING("variable_owner_missing", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.RETURN_ERROR),
    VARIABLE_TYPE_MISMATCH("variable_type_mismatch", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.RETURN_ERROR),
    VARIABLE_UNSET("variable_unset", ErrorSeverity.WARNING, ValidationPhase.RUNTIME, FailurePolicy.RETURN_ERROR),
    CONDITION_UNRESOLVED("condition_unresolved", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.RETURN_ERROR),
    CONDITION_INVALID_TYPE("condition_invalid_type", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.RETURN_ERROR),
    CAPABILITY_MISMATCH("capability_mismatch", ErrorSeverity.WARNING, ValidationPhase.RUNTIME, FailurePolicy.PARTIAL_APPLY),
    NON_APPLICABLE_TARGET("non_applicable_target", ErrorSeverity.INFO, ValidationPhase.RUNTIME, FailurePolicy.PARTIAL_APPLY),
    CONFIG_INVALID("config_invalid", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.RETURN_ERROR),
    PARTICLE_QUOTA_EXCEEDED("particle_quota_exceeded", ErrorSeverity.WARNING, ValidationPhase.RUNTIME, FailurePolicy.SKIP_COMMAND),
    WORLD_STATE_UNAVAILABLE("world_state_unavailable", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.STOP_PROGRAM),
    TRIGGER_CONTEXT_MISSING("trigger_context_missing", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.RETURN_ERROR),

    // 未分類の受け皿。暗黙 skip の温存には使わず、棚卸し漏れの検出用です。
    UNKNOWN("unknown", ErrorSeverity.ERROR, ValidationPhase.RUNTIME, FailurePolicy.STOP_PROGRAM),
    ;

    companion object {
        private val BY_CODE = entries.associateBy(ErrorCode::code)

        /** 旧 reason 文字列を共通コードへ読み替えます。未知は UNKNOWN に畳まず null で返します。 */
        fun fromLegacyReason(reason: String?): ErrorCode? {
            if (reason == null) return null
            // preflight の結合 reason は "invalid_script: ..." 形式のため接頭辞で判定します。
            if (reason.startsWith("invalid_script")) return INVALID_SCRIPT
            return BY_CODE[reason.substringBefore(":").trim()]
        }
    }
}

/**
 * DISK_CALL 方向けの呼び出し枠です。
 *
 * callee 内の runtime error が caller 側で「DISK_CALL failed」だけに潰れないよう、
 * 発生箇所の script/node を積み上げます。#8 の引数・戻り値・Call Frame 本体は
 * 対象外で、ここでは形だけを定義します。
 */
data class CallFrame(
    val scriptId: UUID,
    val nodeId: UUID,
)

/** PARTIAL_APPLY 枠組みの計数です。適用数・非適用数・失敗数を追跡します。 */
data class PartialApplicability(
    val appliedCount: Int = 0,
    val nonApplicableCount: Int = 0,
    val failedCount: Int = 0,
) {
    init {
        require(appliedCount >= 0 && nonApplicableCount >= 0 && failedCount >= 0) {
            "partial counts must be non-negative"
        }
    }

    val totalCount: Int get() = appliedCount + nonApplicableCount + failedCount
}

/**
 * issue #6 の共通エラー形式です。
 *
 * - code/phase/severity/policy をテスト可能にします。
 * - scriptId/nodeId/fieldKeys/path で発生箇所を特定します。
 * - callStack は DISK_CALL 方向けの器で、通常は空のままです。
 * - partial は PARTIAL_APPLY 時のみ意味を持ちます。
 * - message はサーバーログ・デバッグ用の人間向け文で、プレイヤー表示の契約にはしません。
 */
data class ExecutionError(
    val code: ErrorCode,
    val severity: ErrorSeverity = code.defaultSeverity,
    val phase: ValidationPhase = code.defaultPhase,
    val scriptId: UUID? = null,
    val nodeId: UUID? = null,
    val fieldKeys: Set<String> = emptySet(),
    val path: String? = null,
    val message: String? = null,
    val cause: Throwable? = null,
    val callStack: List<CallFrame> = emptyList(),
    val failurePolicy: FailurePolicy = code.defaultPolicy,
    val partial: PartialApplicability? = null,
) {
    /** 呼び出し元へ受け渡す際、外側の script/node を積み増します。 */
    fun withCallerFrame(frame: CallFrame): ExecutionError = copy(callStack = callStack + frame)

    /** テスト実行ログ・チャット表示の可否です。通常実行時は ERROR のみ表示します。 */
    fun visibleInNormalExecution(): Boolean = severity == ErrorSeverity.ERROR

    fun rendered(): String {
        val location = listOfNotNull(path, nodeId?.toString()).joinToString("/").ifEmpty { "root" }
        return "$location: ${code.code}"
    }
}

/**
 * 共通コードを既存の生成済みキーへ解決します。
 *
 * 新規キー追加は cc-system 側の生成・fingerprint 更新を伴うため、#6 枠組みでは
 * 既存キーへの写像に留め、原文フォールバックは行いません。専用キーの細分化は
 * 後続調整で行います。
 */
fun ErrorCode.displayKey(): LocalizationKey<String> = when (this) {
    ErrorCode.COMMAND_FAILED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_COMMAND_FAILED
    ErrorCode.NODE_FAILED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_NODE_FAILED
    ErrorCode.INVALID_WAIT_SECONDS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_INVALID_WAIT_SECONDS
    ErrorCode.COMMAND_LIMIT -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_COMMAND_LIMIT
    ErrorCode.MISSING_NODE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_MISSING_NODE
    ErrorCode.MANUAL_STOP -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_MANUAL_STOP
    ErrorCode.TEST_START_REJECTED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_TEST_START_REJECTED
    ErrorCode.OUTSIDE_MYWORLD, ErrorCode.WORLD_STATE_UNAVAILABLE ->
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_OUTSIDE_MYWORLD
    // 検証・解決系は既存の汎用失敗キーへ寄せ、code 自体で区別を保持します。
    ErrorCode.TIMER_CONFLICT, ErrorCode.TIMER_RANGE,
    ErrorCode.GRAPH_CYCLE, ErrorCode.GRAPH_UNREACHABLE,
    ErrorCode.GRAPH_DUPLICATE_ID, ErrorCode.GRAPH_KEY_MISMATCH,
    ErrorCode.GRAPH_UNKNOWN_REFERENCE, ErrorCode.GRAPH_INVALID_PAIR,
    ErrorCode.GRAPH_MULTIPLE_PARENTS, ErrorCode.GRAPH_DEPTH_EXCEEDED,
    ErrorCode.GRAPH_NODE_LIMIT, ErrorCode.PERSISTED_SHAPE_INVALID,
    ErrorCode.UNKNOWN_ENUM_VALUE, ErrorCode.DANGLING_REFERENCE,
    ErrorCode.REQUIRED_PROGRAM_MISSING, ErrorCode.DISK_CALL_SIGNATURE_MISMATCH,
    ErrorCode.STATIC_PERMISSION_DENIED, ErrorCode.REQUIRED_PROFILE_MISSING,
    ErrorCode.INVALID_SCRIPT, ErrorCode.CONFIG_INVALID,
    ErrorCode.TARGET_SPEC_INVALID, ErrorCode.CONDITION_INVALID_TYPE,
    ErrorCode.VARIABLE_UNDEFINED, ErrorCode.VARIABLE_SCOPE_MISSING,
    ErrorCode.VARIABLE_OWNER_MISSING, ErrorCode.VARIABLE_TYPE_MISMATCH ->
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_COMMAND_FAILED
    ErrorCode.TEMPORARY_UNRESOLVED, ErrorCode.FIXED_ENTITY_UNRESOLVED,
    ErrorCode.VARIABLE_UNSET, ErrorCode.CONDITION_UNRESOLVED,
    ErrorCode.TRIGGER_CONTEXT_MISSING, ErrorCode.UNKNOWN ->
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_NODE_FAILED
    ErrorCode.TARGET_EMPTY, ErrorCode.NON_APPLICABLE_TARGET,
    ErrorCode.CAPABILITY_MISMATCH, ErrorCode.PARTICLE_QUOTA_EXCEEDED ->
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_NODE_FAILED
}
