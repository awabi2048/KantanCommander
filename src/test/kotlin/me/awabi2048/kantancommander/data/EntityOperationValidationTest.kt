package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #32 の検証（アクション整理・操作分離）のテストです。
 *
 * ride／dismountの維持、motion成分の有限数値検証、tag／attribute／dataの
 * 操作別検証、静的対象外の編集時検出を固定します。
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
    fun `legacy tag under entity action is rejected with migration message`() {
        val script = actionScriptWith {
            it.params["action"] = "tag"
            it.params["tag"] = "kc_test"
        }
        val errors = ExecutableScriptValidator.validate(script)
        assertTrue(errors.any { it.fieldKeys == setOf("action") && it.message.contains("エンティティを操作") })
    }

    @Test
    fun `legacy equip under entity action is rejected with migration message`() {
        // #21統合で旧equipの読み込み互換を終了し、PLACE_ITEMへの移行漏れとして拒否します。
        val script = actionScriptWith {
            it.params["action"] = "equip"
            it.params["slot"] = "HAND"
            it.params["item"] = "minecraft:stone"
            it.params["overwrite"] = "false"
        }
        val errors = ExecutableScriptValidator.validate(script)
        assertTrue(errors.any { it.fieldKeys == setOf("action") && it.message.contains("アイテムを配置する") })
    }

    @Test
    fun `tag operation validates tag value`() {
        val valid = scriptWith {
            it.params["operation"] = "tag"
            it.params["tagOperation"] = "add"
            it.params["tag"] = "kc_test"
        }
        assertTrue(ExecutableScriptValidator.validate(valid).isEmpty())

        val invalid = scriptWith {
            it.params["operation"] = "tag"
            it.params["tagOperation"] = "add"
            it.params["tag"] = ""
        }
        assertTrue(ExecutableScriptValidator.validate(invalid).any { it.fieldKeys == setOf("tag") })
    }

    @Test
    fun `attribute base value requires finite amount`() {
        val valid = scriptWith {
            it.params["operation"] = "attribute"
            it.params[EntityAttributeSupport.PARAM_ATTRIBUTE] = "minecraft:max_health"
            it.params[EntityAttributeSupport.PARAM_OPERATION] = EntityAttributeSupport.OPERATION_BASE_VALUE
            it.params[EntityAttributeSupport.PARAM_AMOUNT] = "20.0"
        }
        assertTrue(ExecutableScriptValidator.validate(valid).isEmpty())

        val invalid = scriptWith {
            it.params["operation"] = "attribute"
            it.params[EntityAttributeSupport.PARAM_ATTRIBUTE] = "minecraft:max_health"
            it.params[EntityAttributeSupport.PARAM_OPERATION] = EntityAttributeSupport.OPERATION_BASE_VALUE
            it.params[EntityAttributeSupport.PARAM_AMOUNT] = "infinite"
        }
        assertTrue(
            ExecutableScriptValidator.validate(invalid).any {
                it.fieldKeys == setOf(EntityAttributeSupport.PARAM_AMOUNT)
            },
        )
    }

    @Test
    fun `attribute modifier add requires key amount and operation`() {
        val script = scriptWith {
            it.params["operation"] = "attribute"
            it.params[EntityAttributeSupport.PARAM_ATTRIBUTE] = "minecraft:movement_speed"
            it.params[EntityAttributeSupport.PARAM_OPERATION] = EntityAttributeSupport.OPERATION_MODIFIER_ADD
            it.params[EntityAttributeSupport.PARAM_MODIFIER_KEY] = "not a key"
            it.params[EntityAttributeSupport.PARAM_AMOUNT] = "0.1"
            it.params[EntityAttributeSupport.PARAM_MODIFIER_OPERATION] = "explode"
        }
        val errors = ExecutableScriptValidator.validate(script)
        assertTrue(errors.any { it.fieldKeys == setOf(EntityAttributeSupport.PARAM_MODIFIER_KEY) })
        assertTrue(errors.any { it.fieldKeys == setOf(EntityAttributeSupport.PARAM_MODIFIER_OPERATION) })
    }

    @Test
    fun `attribute modifier remove requires only key`() {
        val valid = scriptWith {
            it.params["operation"] = "attribute"
            it.params[EntityAttributeSupport.PARAM_ATTRIBUTE] = "minecraft:movement_speed"
            it.params[EntityAttributeSupport.PARAM_OPERATION] = EntityAttributeSupport.OPERATION_MODIFIER_REMOVE
            it.params[EntityAttributeSupport.PARAM_MODIFIER_KEY] = "kantan:boost"
        }
        assertTrue(ExecutableScriptValidator.validate(valid).isEmpty())
    }

    @Test
    fun `data rejects unknown property`() {
        val script = scriptWith {
            it.params["operation"] = "data"
            it.params[EntityDataSchema.PARAM_PROPERTY] = "no_such_property"
            it.params[EntityDataSchema.PARAM_VALUE] = "true"
        }
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.fieldKeys == setOf(EntityDataSchema.PARAM_PROPERTY)
            },
        )
    }

    @Test
    fun `data validates boolean enum and text values`() {
        val booleanValid = scriptWith {
            it.params["operation"] = "data"
            it.params[EntityDataSchema.PARAM_PROPERTY] = "gravity"
            it.params[EntityDataSchema.PARAM_VALUE] = "false"
        }
        assertTrue(ExecutableScriptValidator.validate(booleanValid).isEmpty())

        val booleanInvalid = scriptWith {
            it.params["operation"] = "data"
            it.params[EntityDataSchema.PARAM_PROPERTY] = "gravity"
            it.params[EntityDataSchema.PARAM_VALUE] = "maybe"
        }
        assertTrue(
            ExecutableScriptValidator.validate(booleanInvalid).any {
                it.fieldKeys == setOf(EntityDataSchema.PARAM_VALUE)
            },
        )

        val enumInvalid = scriptWith {
            it.params["operation"] = "data"
            it.params[EntityDataSchema.PARAM_PROPERTY] = "rabbit_type"
            it.params[EntityDataSchema.PARAM_VALUE] = "purple"
        }
        // ウサギ種はプレイヤー対象に静的対象外でもあるため、項目・値の両方を検証します。
        val enumErrors = ExecutableScriptValidator.validate(enumInvalid)
        assertTrue(enumErrors.any { it.fieldKeys == setOf(EntityDataSchema.PARAM_VALUE) })

        val textValid = scriptWith {
            it.params["operation"] = "data"
            it.params[EntityDataSchema.PARAM_PROPERTY] = "custom_name"
            it.params[EntityDataSchema.PARAM_VALUE] = "hello"
        }
        assertTrue(ExecutableScriptValidator.validate(textValid).isEmpty())
    }

    @Test
    fun `data rejects statically unsupported property for known type`() {
        val script = scriptWith {
            it.params["operation"] = "data"
            it.params[EntityDataSchema.PARAM_PROPERTY] = "rabbit_type"
            it.params[EntityDataSchema.PARAM_VALUE] = "brown"
        }
        // 対象はプレイヤー確定のため、ウサギ固有項目は編集時に検出します。
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.fieldKeys == setOf(EntityDataSchema.PARAM_PROPERTY)
            },
        )
    }

    @Test
    fun `data defers to runtime when entity type is unknown`() {
        val script = DiskScript(name = "entity-data-runtime", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
            params["operation"] = "data"
            params[EntityDataSchema.PARAM_PROPERTY] = "ai"
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
                params["operation"] = "data"
                params[EntityDataSchema.PARAM_PROPERTY] = EntityDataSchema.PROPERTY_MANNEQUIN_PROFILE_NAME
                params[EntityDataSchema.PARAM_VALUE] = "Notch"
            }
        }
        assertTrue(ExecutableScriptValidator.validate(valid).isEmpty())

        val invalid = DiskScript(name = "mannequin-profile-invalid", owner = UUID.randomUUID()).also { script ->
            GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
                targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
                params["operation"] = "data"
                params[EntityDataSchema.PARAM_PROPERTY] = EntityDataSchema.PROPERTY_MANNEQUIN_PROFILE_NAME
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
                params["operation"] = "data"
                params[EntityDataSchema.PARAM_PROPERTY] = EntityDataSchema.PROPERTY_MANNEQUIN_PROFILE_NAME
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
                params["operation"] = "data"
                params[EntityDataSchema.PARAM_PROPERTY] = EntityDataSchema.PROPERTY_MANNEQUIN_POSE
                params[EntityDataSchema.PARAM_VALUE] = EntityDataSchema.MANNEQUIN_POSE_STANDING
            }
        }
        assertTrue(ExecutableScriptValidator.validate(valid).isEmpty())

        val invalid = DiskScript(name = "mannequin-pose-invalid", owner = UUID.randomUUID()).also { script ->
            GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
                targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
                params["operation"] = "data"
                params[EntityDataSchema.PARAM_PROPERTY] = EntityDataSchema.PROPERTY_MANNEQUIN_POSE
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
                params["operation"] = "data"
                params[EntityDataSchema.PARAM_PROPERTY] = EntityDataSchema.PROPERTY_MANNEQUIN_DESCRIPTION
                params[EntityDataSchema.PARAM_VALUE] = ""
            }
        }
        assertTrue(ExecutableScriptValidator.validate(empty).isEmpty())

        val text = DiskScript(name = "mannequin-description-text", owner = UUID.randomUUID()).also { script ->
            GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
                targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
                params["operation"] = "data"
                params[EntityDataSchema.PARAM_PROPERTY] = EntityDataSchema.PROPERTY_MANNEQUIN_DESCRIPTION
                params[EntityDataSchema.PARAM_VALUE] = "案内人"
            }
        }
        assertTrue(ExecutableScriptValidator.validate(text).isEmpty())
    }

    @Test
    fun `mannequin property is rejected for player target`() {
        val script = scriptWith {
            it.params["operation"] = "data"
            it.params[EntityDataSchema.PARAM_PROPERTY] = EntityDataSchema.PROPERTY_MANNEQUIN_IMMOVABLE
            it.params[EntityDataSchema.PARAM_VALUE] = "true"
        }
        // 対象はプレイヤー確定のため、Mannequin固有項目は編集時に検出する。
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.fieldKeys == setOf(EntityDataSchema.PARAM_PROPERTY)
            },
        )
    }

    @Test
    fun `unknown operation is rejected`() {
        val script = scriptWith {
            it.params["operation"] = "dance"
        }
        assertTrue(ExecutableScriptValidator.validate(script).any { it.fieldKeys == setOf("operation") })
    }
}
