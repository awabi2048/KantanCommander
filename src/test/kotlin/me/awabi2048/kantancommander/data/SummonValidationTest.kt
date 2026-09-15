package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.EntityDataSchema
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #10 の召喚時個別設定の検証テストです。
 *
 * 既存Entity編集と同じ項目ID・型・検証を使うこと、旧 `customName` 欄の
 * 拒否、種類確定時の静的対象外の検出を固定します。実行時の適用と
 * ロールバックは実機で確認し、ここでは検証契約を扱います。
 */
class SummonValidationTest {
    private fun summon(configure: me.awabi2048.kantancommander.model.CommandNode.() -> Unit): DiskScript {
        val script = DiskScript(name = "summon-data", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.SUMMON_ENTITY).apply {
            params["summonEntity"] = "minecraft:zombie"
            configure()
        }
        return script
    }

    @Test
    fun `shared schema data passes for the summon type`() {
        val script = summon {
            params[EntityDataSchema.summonDataKey("custom_name")] = "Boss"
            params[EntityDataSchema.summonDataKey("silent")] = "true"
        }
        assertFalse(ExecutableScriptValidator.validate(script).any { it.nodeId != null })
    }

    @Test
    fun `unknown property is rejected without fallback`() {
        val script = summon {
            params[EntityDataSchema.summonDataKey("no_such_property")] = "true"
        }
        assertTrue(
            ExecutableScriptValidator.validate(script)
                .any { it.message.contains("不明な設定項目") },
        )
    }

    @Test
    fun `statically unsupported property is rejected for the known type`() {
        // ゾンビにウサギ固有項目は適用できません。
        val script = summon {
            params[EntityDataSchema.summonDataKey(EntityDataSchema.PROPERTY_RABBIT_TYPE)] = "brown"
        }
        assertTrue(
            ExecutableScriptValidator.validate(script)
                .any { it.message.contains("適用できない設定項目") },
        )
    }

    @Test
    fun `invalid values are rejected per kind`() {
        val boolean = summon {
            params[EntityDataSchema.summonDataKey("silent")] = "maybe"
        }
        assertTrue(
            ExecutableScriptValidator.validate(boolean)
                .any { it.message.contains("trueまたはfalse") },
        )

        val enum = summon {
            params[EntityDataSchema.summonDataKey(EntityDataSchema.PROPERTY_RABBIT_TYPE)] = "purple"
            params["summonEntity"] = "minecraft:rabbit"
        }
        assertTrue(
            ExecutableScriptValidator.validate(enum)
                .any { it.message.contains("設定値が不正") },
        )

        val integer = summon {
            params["summonEntity"] = "minecraft:slime"
            params[EntityDataSchema.summonDataKey(EntityDataSchema.PROPERTY_SLIME_SIZE)] = "9999"
        }
        // 範囲外は値検証で検出します（スライムの大きさ自体は対象です）。
        assertTrue(
            ExecutableScriptValidator.validate(integer)
                .any { it.message.contains("設定値が範囲外") },
        )
    }

    @Test
    fun `legacy custom name field is rejected with migration message`() {
        // 読込時は正規化済みのため、ここに残る手組み値は移行漏れとして拒否します。
        val script = summon {
            params["customName"] = "Boss"
        }
        assertTrue(
            ExecutableScriptValidator.validate(script)
                .any { it.fieldKeys == setOf("customName") && it.message.contains("個別データの設定") },
        )
    }

    @Test
    fun `blank values are unset equivalents`() {
        val script = summon {
            params[EntityDataSchema.summonDataKey("custom_name")] = ""
        }
        assertFalse(ExecutableScriptValidator.validate(script).any { it.nodeId != null })
    }
}
