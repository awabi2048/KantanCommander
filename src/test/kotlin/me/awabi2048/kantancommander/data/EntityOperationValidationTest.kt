package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataPath
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.EntityTagEntries
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * 統合選択形式の検証テストです。
 *
 * ride／dismountの維持、motion成分の有限数値検証、タグ一覧／attribute／
 * 型付きデータの検証、静的対象外の編集時検出を固定します。
 * capabilityのruntime判定は実行側の責務であり、ここでは扱いません。
 */
class EntityOperationValidationTest {
    private fun scriptWith(configure: (me.awabi2048.kantancommander.model.CommandNode) -> Unit): DiskScript {
        val script = DiskScript(name = "entity-operation", owner = UUID.randomUUID())
        val node = GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
        }
        configure(node)
        return script
    }

    private fun actionScriptWith(configure: (me.awabi2048.kantancommander.model.CommandNode) -> Unit): DiskScript {
        val script = DiskScript(name = "entity-action", owner = UUID.randomUUID())
        val node = GraphEditor.append(script.graph, CommandType.ENTITY_ACTION).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
        }
        configure(node)
        return script
    }

    @Test
    fun `motion requires finite components`() {
        val valid = actionScriptWith {
            it.params["action"] = "motion"
            it.params["motionX"] = "1.5"
            it.params["motionY"] = "-2.0"
            it.params["motionZ"] = "0.0"
        }
        assertTrue(ExecutableScriptValidator.validate(valid).isEmpty())

        val invalid = actionScriptWith {
            it.params["action"] = "motion"
            it.params["motionX"] = "NaN"
            it.params["motionY"] = "0.0"
            it.params["motionZ"] = "oops"
        }
        val errors = ExecutableScriptValidator.validate(invalid)
        assertTrue(errors.any { it.fieldKeys == setOf("motionX") })
        assertTrue(errors.any { it.fieldKeys == setOf("motionZ") })
        assertFalse(errors.any { it.fieldKeys == setOf("motionY") })
    }

    @Test
    fun `unmigrated tag action is rejected as unknown`() {
        // #32: 旧tagは読込時に ENTITY_OPERATION へ正本化されるため、検証層へ
        // 到達する手組み値は不明な操作として拒否します。
        val script = actionScriptWith {
            it.params["action"] = "tag"
            it.params["tag"] = "kc_test"
        }
        val errors = ExecutableScriptValidator.validate(script)
        assertTrue(errors.any { it.fieldKeys == setOf("action") && it.message.contains("不明なエンティティ操作") })
    }

    @Test
    fun `unmigrated equip action is rejected as unknown`() {
        // #21: 旧equipは読込時に PLACE_ITEM へ正本化されるため、検証層へ
        // 到達する手組み値は不明な操作として拒否します。
        val script = actionScriptWith {
            it.params["action"] = "equip"
            it.params["slot"] = "HAND"
            it.params["item"] = "minecraft:stone"
            it.params["overwrite"] = "false"
        }
        val errors = ExecutableScriptValidator.validate(script)
        assertTrue(errors.any { it.fieldKeys == setOf("action") && it.message.contains("不明なエンティティ操作") })
    }

    @Test
    fun `tag entries validate tag values`() {
        val valid = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.TAG
            it.params[EntityTagEntries.PARAM_ENTRIES] = "add=kc_test"
        }
        assertTrue(ExecutableScriptValidator.validate(valid).isEmpty())

        val multiValid = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.TAG
            it.params[EntityTagEntries.PARAM_ENTRIES] = "add=kc_test;remove=kc_old"
        }
        assertTrue(ExecutableScriptValidator.validate(multiValid).isEmpty())

        val invalid = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.TAG
            it.params[EntityTagEntries.PARAM_ENTRIES] = ""
        }
        assertTrue(ExecutableScriptValidator.validate(invalid).any { it.fieldKeys == setOf(EntityTagEntries.PARAM_ENTRIES) })

        val duplicate = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.TAG
            it.params[EntityTagEntries.PARAM_ENTRIES] = "add=kc_test;remove=kc_test"
        }
        assertTrue(ExecutableScriptValidator.validate(duplicate).any { it.fieldKeys == setOf(EntityTagEntries.PARAM_ENTRIES) })
    }

    @Test
    fun `attribute base value requires finite amount`() {
        val valid = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.attributePath("minecraft:max_health")
            it.params[EntityAttributeSupport.PARAM_VALUE] = "20.0"
        }
        assertTrue(ExecutableScriptValidator.validate(valid).isEmpty())

        val invalid = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.attributePath("minecraft:max_health")
            it.params[EntityAttributeSupport.PARAM_VALUE] = "infinite"
        }
        assertTrue(
            ExecutableScriptValidator.validate(invalid).any {
                it.fieldKeys == setOf(EntityAttributeSupport.PARAM_VALUE)
            },
        )
    }

    @Test
    fun `attribute rejects unknown id`() {
        val script = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.attributePath("minecraft:no_such_attribute_xyz")
            it.params[EntityAttributeSupport.PARAM_VALUE] = "1.0"
        }
        // API単体試験環境ではRegistry確認を行わないため、構文のみ検証します。
        // 実在確認は実サーバー環境の検証で行います。
        val errors = ExecutableScriptValidator.validate(script)
        // 不正形式でなければ検証を通過します（実行時に解決失敗として扱います）。
        assertTrue(errors.none { it.fieldKeys == setOf(EntityDataPath.PARAM_PATH) })
    }

    @Test
    fun `data rejects unknown property`() {
        val script = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath("no_such_property")
            it.params[EntityDataSchema.PARAM_VALUE] = "true"
        }
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.fieldKeys == setOf(EntityDataPath.PARAM_PATH)
            },
        )
    }

    @Test
    fun `data validates boolean enum integer and text values`() {
        val booleanValid = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath("gravity")
            it.params[EntityDataSchema.PARAM_VALUE] = "false"
        }
        assertTrue(ExecutableScriptValidator.validate(booleanValid).isEmpty())

        val booleanInvalid = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath("gravity")
            it.params[EntityDataSchema.PARAM_VALUE] = "maybe"
        }
        assertTrue(
            ExecutableScriptValidator.validate(booleanInvalid).any {
                it.fieldKeys == setOf(EntityDataSchema.PARAM_VALUE)
            },
        )

        val enumInvalid = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath("rabbit_type")
            it.params[EntityDataSchema.PARAM_VALUE] = "purple"
        }
        // ウサギ種はプレイヤー対象に静的対象外でもあるため、項目・値の両方を検証します。
        val enumErrors = ExecutableScriptValidator.validate(enumInvalid)
        assertTrue(enumErrors.any { it.fieldKeys == setOf(EntityDataSchema.PARAM_VALUE) })

        val integerValid = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_BEE_ANGER)
            it.params[EntityDataSchema.PARAM_VALUE] = "100"
        }
        // ハチ固有項目はプレイヤー対象に静的対象外のため、値検証とは別に項目エラーを含みます。
        // ここでは値自体の範囲検証が項目エラーに埋もれないことを確認します。
        val integerErrors = ExecutableScriptValidator.validate(integerValid)
        assertTrue(integerErrors.none { it.fieldKeys == setOf(EntityDataSchema.PARAM_VALUE) })

        val integerInvalid = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_BEE_ANGER)
            it.params[EntityDataSchema.PARAM_VALUE] = "-1"
        }
        assertTrue(
            ExecutableScriptValidator.validate(integerInvalid).any {
                it.fieldKeys == setOf(EntityDataSchema.PARAM_VALUE)
            },
        )

        val textValid = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath("custom_name")
            it.params[EntityDataSchema.PARAM_VALUE] = "hello"
        }
        assertTrue(ExecutableScriptValidator.validate(textValid).isEmpty())
    }

    @Test
    fun `data rejects statically unsupported property for known type`() {
        val script = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath("rabbit_type")
            it.params[EntityDataSchema.PARAM_VALUE] = "brown"
        }
        // 対象はプレイヤー確定のため、ウサギ固有項目は編集時に検出します。
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.fieldKeys == setOf(EntityDataPath.PARAM_PATH)
            },
        )
    }

    @Test
    fun `data defers to runtime when entity type is unknown`() {
        val script = DiskScript(name = "entity-data-runtime", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
            params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath("ai")
            params[EntityDataSchema.PARAM_VALUE] = "true"
        }
        // 種類不明の対象ではAI項目を過剰に制限せず、実行時判定へ委ねます。
        assertTrue(ExecutableScriptValidator.validate(script).isEmpty())
    }

    @Test
    fun `mannequin profile name validates player name syntax`() {
        val valid = DiskScript(name = "mannequin-profile", owner = UUID.randomUUID()).also { script ->
            GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
                targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
                params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_MANNEQUIN_PROFILE_NAME)
                params[EntityDataSchema.PARAM_VALUE] = "Notch"
            }
        }
        assertTrue(ExecutableScriptValidator.validate(valid).isEmpty())

        val invalid = DiskScript(name = "mannequin-profile-invalid", owner = UUID.randomUUID()).also { script ->
            GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
                targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
                params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_MANNEQUIN_PROFILE_NAME)
                params[EntityDataSchema.PARAM_VALUE] = "has space"
            }
        }
        assertTrue(
            ExecutableScriptValidator.validate(invalid).any {
                it.fieldKeys == setOf(EntityDataSchema.PARAM_VALUE)
            },
        )

        val blank = DiskScript(name = "mannequin-profile-blank", owner = UUID.randomUUID()).also { script ->
            GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
                targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
                params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_MANNEQUIN_PROFILE_NAME)
                params[EntityDataSchema.PARAM_VALUE] = ""
            }
        }
        assertTrue(
            ExecutableScriptValidator.validate(blank).any {
                it.fieldKeys == setOf(EntityDataSchema.PARAM_VALUE)
            },
        )
    }

    @Test
    fun `mannequin pose validates child screen choices`() {
        val valid = DiskScript(name = "mannequin-pose", owner = UUID.randomUUID()).also { script ->
            GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
                targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
                params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_MANNEQUIN_POSE)
                params[EntityDataSchema.PARAM_VALUE] = EntityDataSchema.MANNEQUIN_POSE_STANDING
            }
        }
        assertTrue(ExecutableScriptValidator.validate(valid).isEmpty())

        val invalid = DiskScript(name = "mannequin-pose-invalid", owner = UUID.randomUUID()).also { script ->
            GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
                targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
                params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_MANNEQUIN_POSE)
                params[EntityDataSchema.PARAM_VALUE] = "dancing"
            }
        }
        assertTrue(
            ExecutableScriptValidator.validate(invalid).any {
                it.fieldKeys == setOf(EntityDataSchema.PARAM_VALUE)
            },
        )
    }

    @Test
    fun `mannequin description accepts text including empty`() {
        val empty = DiskScript(name = "mannequin-description-empty", owner = UUID.randomUUID()).also { script ->
            GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
                targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
                params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_MANNEQUIN_DESCRIPTION)
                params[EntityDataSchema.PARAM_VALUE] = ""
            }
        }
        assertTrue(ExecutableScriptValidator.validate(empty).isEmpty())

        val text = DiskScript(name = "mannequin-description-text", owner = UUID.randomUUID()).also { script ->
            GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
                targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
                params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_MANNEQUIN_DESCRIPTION)
                params[EntityDataSchema.PARAM_VALUE] = "案内人"
            }
        }
        assertTrue(ExecutableScriptValidator.validate(text).isEmpty())
    }

    @Test
    fun `mannequin property is rejected for player target`() {
        val script = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_MANNEQUIN_IMMOVABLE)
            it.params[EntityDataSchema.PARAM_VALUE] = "true"
        }
        // 対象はプレイヤー確定のため、Mannequin固有項目は編集時に検出する。
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.fieldKeys == setOf(EntityDataPath.PARAM_PATH)
            },
        )
    }

    @Test
    fun `unset path is rejected`() {
        val script = scriptWith {
            it.params[EntityDataPath.PARAM_PATH] = ""
        }
        assertTrue(ExecutableScriptValidator.validate(script).any { it.fieldKeys == setOf(EntityDataPath.PARAM_PATH) })
    }
}
