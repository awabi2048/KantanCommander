package me.awabi2048.kantancommander.export

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.logging.Logger

/**
 * issue #32 のバニラ出力テストです。
 *
 * tagとattribute操作は等価なバニラコマンドへ変換し、motionとデータ編集は
 * 不正確な変換を行わず未対応として明示することを固定します。
 */
class EntityOperationExportTest {
    @TempDir
    lateinit var temp: File

    private fun exporter(): Pair<ScriptStore, VanillaDatapackExporter> {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        return store to VanillaDatapackExporter(store, temp.resolve("exports"))
    }

    private fun operationScript(configure: (me.awabi2048.kantancommander.model.CommandNode) -> Unit): DiskScript {
        val (store, _) = exporter()
        val script = store.create(UUID.randomUUID(), "entity-operation")
        GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            configure(this)
        }
        return script
    }

    @Test
    fun `tag operation lowers to tag command`() {
        val (_, exporter) = exporter()
        val script = operationScript {
            it.params["operation"] = "tag"
            it.params["tagOperation"] = "add"
            it.params["tag"] = "kc_test"
        }
        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            exporter.compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("tag ") && body.contains("kc_test"))
    }

    @Test
    fun `attribute base value lowers to per target attribute command`() {
        // attributeは単一対象専用のため、対象ごとに execute as … run へ展開します。
        val (_, exporter) = exporter()
        val script = operationScript {
            it.params["operation"] = "attribute"
            it.params[EntityAttributeSupport.PARAM_ATTRIBUTE] = "minecraft:max_health"
            it.params[EntityAttributeSupport.PARAM_OPERATION] = EntityAttributeSupport.OPERATION_BASE_VALUE
            it.params[EntityAttributeSupport.PARAM_AMOUNT] = "20.0"
        }
        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            exporter.compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("execute as @a[") && body.contains("attribute @s minecraft:max_health base set 20.0"))
    }

    @Test
    fun `attribute operation expands multi targets per entity`() {
        // 複数対象セレクターをそのまま渡すと無効になるため、as で1件ずつ実行します。
        val (store, exporter) = exporter()
        val script = store.create(UUID.randomUUID(), "entity-operation-multi")
        GraphEditor.append(script.graph, CommandType.ENTITY_OPERATION).apply {
            targetSpec = TargetSpec(TargetKind.NEARBY_PLAYERS)
            params["operation"] = "attribute"
            params[EntityAttributeSupport.PARAM_ATTRIBUTE] = "minecraft:max_health"
            params[EntityAttributeSupport.PARAM_OPERATION] = EntityAttributeSupport.OPERATION_BASE_VALUE
            params[EntityAttributeSupport.PARAM_AMOUNT] = "20.0"
        }
        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            exporter.compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("execute as @a[") && body.contains("attribute @s minecraft:max_health base set 20.0"))
    }

    @Test
    fun `attribute modifier add lowers with vanilla operation name`() {
        val (_, exporter) = exporter()
        val script = operationScript {
            it.params["operation"] = "attribute"
            it.params[EntityAttributeSupport.PARAM_ATTRIBUTE] = "minecraft:movement_speed"
            it.params[EntityAttributeSupport.PARAM_OPERATION] = EntityAttributeSupport.OPERATION_MODIFIER_ADD
            it.params[EntityAttributeSupport.PARAM_MODIFIER_KEY] = "kantan:boost"
            it.params[EntityAttributeSupport.PARAM_AMOUNT] = "0.1"
            it.params[EntityAttributeSupport.PARAM_MODIFIER_OPERATION] = EntityAttributeSupport.MODIFIER_MULTIPLY_TOTAL
        }
        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            exporter.compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("modifier add kantan:boost 0.1 add_multiplied_total"))
    }

    @Test
    fun `attribute modifier remove lowers to modifier remove`() {
        val (_, exporter) = exporter()
        val script = operationScript {
            it.params["operation"] = "attribute"
            it.params[EntityAttributeSupport.PARAM_ATTRIBUTE] = "minecraft:movement_speed"
            it.params[EntityAttributeSupport.PARAM_OPERATION] = EntityAttributeSupport.OPERATION_MODIFIER_REMOVE
            it.params[EntityAttributeSupport.PARAM_MODIFIER_KEY] = "kantan:boost"
        }
        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            exporter.compileForStandalone(script),
        )
        assertTrue(result.functions.values.joinToString("\n").contains("modifier remove kantan:boost"))
    }

    @Test
    fun `data edit is reported as unsupported without conversion`() {
        val (_, exporter) = exporter()
        val script = operationScript {
            it.params["operation"] = "data"
            it.params[EntityDataSchema.PARAM_PROPERTY] = "gravity"
            it.params[EntityDataSchema.PARAM_VALUE] = "false"
        }
        val result = assertInstanceOf(
            StandaloneCompilation.Failure::class.java,
            exporter.compileForStandalone(script),
        )
        assertTrue(result.errors.any { it.contains("データ編集はバニラ出力に未対応") })
    }

    @Test
    fun `mannequin data edit is reported as unsupported without conversion`() {
        val (_, exporter) = exporter()
        val script = operationScript {
            it.params["operation"] = "data"
            it.params[EntityDataSchema.PARAM_PROPERTY] = EntityDataSchema.PROPERTY_MANNEQUIN_POSE
            it.params[EntityDataSchema.PARAM_VALUE] = EntityDataSchema.MANNEQUIN_POSE_STANDING
        }
        val result = assertInstanceOf(
            StandaloneCompilation.Failure::class.java,
            exporter.compileForStandalone(script),
        )
        assertTrue(result.errors.any { it.contains("データ編集はバニラ出力に未対応") })
    }

    @Test
    fun `motion is reported as unsupported without conversion`() {
        val (store, exporter) = exporter()
        val script = store.create(UUID.randomUUID(), "entity-motion")
        GraphEditor.append(script.graph, CommandType.ENTITY_ACTION).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            params["action"] = "motion"
            params["motionX"] = "1.0"
            params["motionY"] = "0.0"
            params["motionZ"] = "0.0"
        }
        val result = assertInstanceOf(
            StandaloneCompilation.Failure::class.java,
            exporter.compileForStandalone(script),
        )
        assertTrue(result.errors.any { it.contains("動き設定はバニラ出力に未対応") })
    }
}
