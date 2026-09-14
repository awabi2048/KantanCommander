package me.awabi2048.kantancommander.export

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataPath
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.EntityTagEntries
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
 * 統合選択形式のバニラ出力テストです。
 *
 * 単一タグとattribute基本値は等価なバニラコマンドへ変換し、複数タグ・
 * データ編集は不正確な変換を行わず未対応として明示することを固定します。
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
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.TAG
            it.params[EntityTagEntries.PARAM_ENTRIES] = "add=kc_test"
        }
        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            exporter.compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("tag ") && body.contains("kc_test"))
    }

    @Test
    fun `multi tag is reported as unsupported without conversion`() {
        val (_, exporter) = exporter()
        val script = operationScript {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.TAG
            it.params[EntityTagEntries.PARAM_ENTRIES] = "add=kc_test;remove=kc_old"
        }
        val result = assertInstanceOf(
            StandaloneCompilation.Failure::class.java,
            exporter.compileForStandalone(script),
        )
        assertTrue(result.errors.any { it.contains("複数タグはバニラ出力に未対応") })
    }

    @Test
    fun `attribute base value lowers to per target attribute command`() {
        // attributeは単一対象専用のため、対象ごとに execute as … run へ展開します。
        val (_, exporter) = exporter()
        val script = operationScript {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.attributePath("minecraft:max_health")
            it.params[EntityAttributeSupport.PARAM_VALUE] = "20.0"
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
            params[EntityDataPath.PARAM_PATH] = EntityDataPath.attributePath("minecraft:max_health")
            params[EntityAttributeSupport.PARAM_VALUE] = "20.0"
        }
        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            exporter.compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("execute as @a[") && body.contains("attribute @s minecraft:max_health base set 20.0"))
    }

    @Test
    fun `data edit is reported as unsupported without conversion`() {
        val (_, exporter) = exporter()
        val script = operationScript {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath("gravity")
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
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_MANNEQUIN_POSE)
            it.params[EntityDataSchema.PARAM_VALUE] = EntityDataSchema.MANNEQUIN_POSE_STANDING
        }
        val result = assertInstanceOf(
            StandaloneCompilation.Failure::class.java,
            exporter.compileForStandalone(script),
        )
        assertTrue(result.errors.any { it.contains("データ編集はバニラ出力に未対応") })
    }

    @Test
    fun `mannequin description edit is reported as unsupported without conversion`() {
        val (_, exporter) = exporter()
        val script = operationScript {
            it.params[EntityDataPath.PARAM_PATH] = EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_MANNEQUIN_DESCRIPTION)
            it.params[EntityDataSchema.PARAM_VALUE] = "案内人"
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
