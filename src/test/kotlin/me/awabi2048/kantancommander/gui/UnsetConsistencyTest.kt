package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * 未設定概念の三者一致を固定します。
 *
 * 表示（未設定表示・選択なし）・注意喚起（タブへの投影）・検証（未設定エラー）は
 * 同じ未設定概念へ接続しなければならず、いずれかが外れると「未設定なのに
 * 完了扱い」になります。数値型の実行用既定値は例外であり、検証を通過します。
 */
class UnsetConsistencyTest {
    private data class RequiredField(
        val type: CommandType,
        val fieldKey: String,
        /** 検証エラーの報告先フィールドです。 */
        val errorField: String,
        val validValue: String,
        /** 束ね表示は行単位の検証へ委ね、型レベルの表示検査から外します。 */
        val checkDisplay: Boolean = true,
    )

    private val requiredFields = listOf(
        RequiredField(CommandType.PARTICLE, "particle", "particle", "FLAME"),
        RequiredField(CommandType.SUMMON_ENTITY, "summonEntity", "summonEntity", "minecraft:pig"),
        RequiredField(CommandType.APPLY_EFFECT, "effect", "effect", "minecraft:speed"),
        RequiredField(CommandType.PLAY_SOUND, "soundSetting", "soundSetting", "minecraft:block.note_block.harp", checkDisplay = false),
        // issue #24: 群名は未設定表示・注意喚起・検証の三者一致を固定します。
        RequiredField(CommandType.ENTITY_GROUP, "group", "group", "my_group-01"),
    )

    private fun blankNode(type: CommandType): CommandNode = type.newNode()

    private fun filledNode(case: RequiredField): CommandNode = typeNewNode(case).apply {
        CommandSettingsModel.setParameter(this, paramKey(case), case.validValue)
    }

    private fun typeNewNode(case: RequiredField): CommandNode = case.type.newNode()

    private fun paramKey(case: RequiredField): String = when (case.fieldKey) {
        "soundSetting" -> "sound"
        else -> case.fieldKey
    }

    private fun fieldValue(type: CommandType, node: CommandNode, fieldKey: String): DisplayValue =
        requireNotNull(EditorMenuLayout.fields(type, node).firstOrNull { it.key == fieldKey }) {
            "field not visible: $type.$fieldKey"
        }.value(node)

    private fun isUnsetDisplay(value: DisplayValue): Boolean =
        value is DisplayValue.Localized && value.key == KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET

    private fun validate(node: CommandNode) =
        ExecutableScriptValidator.validate(DiskScript(name = "unset-audit").also { script ->
            GraphEditor.append(script.graph, node.type).apply {
                params.putAll(node.params)
            }
        })

    @Test
    fun `blank required fields show unset without selection`() {
        requiredFields.filter { it.checkDisplay }.forEach { case ->
            val node = blankNode(case.type)
            assertFalse(
                CommandSettingsModel.isFieldConfigured(node, case.fieldKey),
                "blank must be unconfigured: ${case.type}.${case.fieldKey}",
            )
            assertTrue(
                isUnsetDisplay(fieldValue(case.type, node, case.fieldKey)),
                "blank must display unset: ${case.type}.${case.fieldKey}",
            )
        }
        // 束ね表示は行単位の検証へ委ねます。設定済み判定だけを固定します。
        requiredFields.filter { !it.checkDisplay }.forEach { case ->
            assertFalse(
                CommandSettingsModel.isFieldConfigured(blankNode(case.type), case.fieldKey),
                "blank must be unconfigured: ${case.type}.${case.fieldKey}",
            )
        }
    }

    @Test
    fun `blank required fields fail validation on their own field`() {
        requiredFields.forEach { case ->
            val errors = validate(blankNode(case.type))
            assertTrue(
                errors.any { it.fieldKeys.contains(case.errorField) },
                "blank must error on ${case.errorField}: ${errors.map { it.fieldKeys }}",
            )
        }
    }

    @Test
    fun `validation errors project onto visible tabs`() {
        requiredFields.forEach { case ->
            val node = blankNode(case.type)
            val errors = validate(node)
            val errorFields = errors.flatMap { it.fieldKeys }.toSet()
            assertTrue(errorFields.contains(case.errorField), "missing error field: $case")
            val visibleKey = CommandSettingsModel.visibleAttentionFieldKey(node, case.errorField)
            assertNotNull(visibleKey, "unmapped attention field: $case")
            assertTrue(
                CommandSettingsModel.gestureVisibleFields(node).any { it.key == visibleKey },
                "attention target not visible: $case -> $visibleKey",
            )
        }
    }

    @Test
    fun `filled required fields show values without errors`() {
        requiredFields.forEach { case ->
            val node = filledNode(case)
            assertTrue(
                CommandSettingsModel.isFieldConfigured(node, case.fieldKey),
                "filled must be configured: ${case.type}.${case.fieldKey}",
            )
            val errors = validate(node)
            assertTrue(
                errors.none { it.fieldKeys.contains(case.errorField) },
                "filled must not error on ${case.errorField}: ${errors.map { it.fieldKeys }}",
            )
            if (case.checkDisplay) {
                assertFalse(
                    isUnsetDisplay(fieldValue(case.type, node, case.fieldKey)),
                    "filled must display a value: ${case.type}.${case.fieldKey}",
                )
            }
        }
    }

    @Test
    fun `numeric execution defaults pass validation by design`() {
        // 数値型の実行用既定値は未設定エラーにしません。設定済み表示も
        // 既定値のままでは付きません。この例外は仕様として固定します。
        val node = CommandType.PLAY_SOUND.newNode().apply {
            CommandSettingsModel.setParameter(this, "sound", "minecraft:block.note_block.harp")
        }
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "soundSetting"))
        assertTrue(validate(node).isEmpty())
        assertFalse(CommandSettingsModel.isFieldConfigured(CommandType.PLAY_SOUND.newNode(), "soundSetting"))
    }
}
