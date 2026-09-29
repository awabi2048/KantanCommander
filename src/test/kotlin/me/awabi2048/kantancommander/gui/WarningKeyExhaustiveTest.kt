package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/**
 * 表示フィールドと警告キー解決の網羅テストです。
 *
 * 警告キーの網羅は実行時フォールバックの対象ではなく、このテストで静的に
 * 強制します。全コマンド型×全表示フィールド×主要パラメータ組合せで
 * 解決できることをここで固定します。
 */
class WarningKeyExhaustiveTest {

    /** 表示フィールドがパラメータで変わる種別の、全組合せバリアントを列挙します。 */
    private fun variantsOf(type: CommandType): List<me.awabi2048.kantancommander.model.CommandNode> {
        val variants = mutableListOf(type.newNode())
        when (type) {
            CommandType.TEMP_SET -> for (valueType in ValueType.entries) {
                for (mode in VariableChangeMode.entries) {
                    variants += type.newNode().apply {
                        params["tempType"] = valueType.name
                        params["changeMode"] = mode.name
                    }
                }
            }
            CommandType.VARIABLE -> for (scope in VariableScope.entries) {
                for (operation in VariableOperation.entries) {
                    for (valueType in ValueType.entries) {
                        for (mode in VariableChangeMode.entries) {
                            variants += type.newNode().apply {
                                params["scope"] = scope.name
                                params["operation"] = operation.name
                                params["type"] = valueType.name
                                params["changeMode"] = mode.name
                            }
                        }
                    }
                }
            }
            CommandType.CONDITION, CommandType.FOLDED_IF -> for (subject in ConditionSubject.entries) {
                variants += type.newNode().apply { params["subject"] = subject.name }
            }
            else -> {}
        }
        return variants
    }

    @Test
    fun `every visible field resolves a warning key`() {
        val failures = mutableListOf<String>()
        for (type in CommandType.entries) {
            for (node in variantsOf(type)) {
                for (field in CommandSettingsModel.gestureVisibleFields(node)) {
                    if (CommandSettingsModel.warningKeyForField(node, field.key) == null) {
                        failures += "${node.type} key=${field.key} params=${node.params}"
                    }
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail<Unit>("警告キー未解決:\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun `every node variant fits the setting tab limit`() {
        // SettingTabSet は生成時点で上限を保証するため、全組合せを実際に構築して
        // 上限違反が実行時へ到達しないことを固定します。
        val failures = mutableListOf<String>()
        for (type in CommandType.entries) {
            for (node in variantsOf(type)) {
                val fields = CommandSettingsModel.gestureVisibleFields(node)
                val capped = fields.count { it.key != SettingTabSet.UNCAPPED_FIELD_KEY }
                if (capped > SettingTabSet.TAB_MAX) {
                    failures += "${node.type} capped=$capped params=${node.params} keys=${fields.map { it.key }}"
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail<Unit>("タブ上限超過:\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun `entity scope variable embeds target under scope instead of a tab`() {
        val node = CommandType.VARIABLE.newNode().apply {
            params["scope"] = VariableScope.ENTITY.name
            params["operation"] = VariableOperation.SET.name
            params["type"] = ValueType.LOCATION.name
            params["changeMode"] = VariableChangeMode.ASSIGN.name
        }
        // 対象はタブ列へ出さず、検証エラーはscopeタブへ投影します。
        val keys = CommandSettingsModel.gestureVisibleFields(node).map { it.key }
        assertFalse("target" in keys, "ENTITYスコープの対象はタブに出しません: $keys")
        assertTrue("scope" in keys)
        assertEquals("scope", CommandSettingsModel.visibleAttentionFieldKey(node, "target"))
        // scopeタブの設定済み判定は対象指定まで含みます。
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "scope"))
        node.targetSpec = TargetSpec(kind = TargetKind.NEAREST_PLAYER)
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "scope"))
        // スコープを離れるときは編集入口のない対象指定を残しません。
        CommandSettingsModel.setParameter(node, "scope", VariableScope.WORLD.name)
        assertNull(node.targetSpec)
    }

    @Test
    fun `attention warning resolves by validation field key`() {
        // タブへ内包される項目は検証キー側の固有警告が優先されます。
        val entityVariable = CommandType.VARIABLE.newNode().apply {
            params["scope"] = VariableScope.ENTITY.name
        }
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET,
            CommandSettingsModel.attentionWarningKey(entityVariable, "scope", setOf("target")),
        )
        // 検証キーに対応する警告がない項目は、タブ側の警告へ退避します。
        val condition = CommandType.CONDITION.newNode().apply {
            params["subject"] = ConditionSubject.PLAYER.name
        }
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_CONDITION,
            CommandSettingsModel.attentionWarningKey(condition, "condition", setOf("sneaking")),
        )
    }

    @Test
    fun `unknown warning key falls back to the generic field warning`() {
        val node = CommandType.MERGE.newNode()
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_FIELD,
            CommandSettingsModel.incompleteWarningKey(node, "noSuchField"),
        )
    }
}
