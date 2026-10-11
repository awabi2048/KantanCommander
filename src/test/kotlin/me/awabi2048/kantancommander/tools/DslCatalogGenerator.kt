package me.awabi2048.kantancommander.tools

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.awabi2048.kantancommander.dsl.DSL_COMMAND_TABLE
import me.awabi2048.kantancommander.dsl.DslChecker
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.EventTriggerArguments
import me.awabi2048.kantancommander.model.EventTriggerKind
import me.awabi2048.kantancommander.model.ValueProperties
import me.awabi2048.kantancommander.model.ValueType
import java.io.File

/**
 * `.kcp` DSL の語彙表を `editors/vscode-kcp/data/dsl-catalog.json` へ
 * 出力するカタログ生成ツールです。
 *
 * コマンド表（DSL_COMMAND_TABLE）・組み込み呼出しの引数仕様・詳細値・
 * イベント引数・キーワード等、エディタ側が診断・補完に使う語彙を
 * Kotlin 側の単一正本から機械的に写します。拡張機能はこの JSON を
 * 読むだけで本体実装と同じ語彙を使えます。
 *
 * 実行方法: `./gradlew generateDslCatalog`
 */
object DslCatalogGenerator {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.firstOrNull() ?: "editors/vscode-kcp/data/dsl-catalog.json")
        out.parentFile?.mkdirs()
        out.writeText(gson.toJson(build()), Charsets.UTF_8)
        println("wrote ${out.path}")
    }

    private fun build(): JsonObject = JsonObject().apply {
        add("keywords", strArray(listOf(
            "program", "var", "if", "else", "repeat", "break", "continue", "return",
            "call", "world", "del", "import", "trigger", "defarg", "null", "true", "false",
        )))
        add("valueTypes", strArray(ValueType.entries.map { it.name.lowercase() }))
        add("specialPositions", strArray(listOf("disk", "captured")))
        // 数値関数です。`pow` は `^` 演算子の関数形（位置成分 `^x` と衝突するため）、
        // 三角関数はモデルの式言語と同名です。引数は位置引数のみで全て number 型です。
        add("numericFunctions", JsonObject().apply {
            addProperty("pow", 2)
            me.awabi2048.kantancommander.model.NumericExpression.TRIGONOMETRIC_FUNCTIONS
                .sorted().forEach { addProperty(it, 1) }
        })
        add("reservedNames", strArray(DslChecker.RESERVED_NAMES.toList()))
        add("gamemodeConstants", strArray(DslChecker.GAMEMODE_NAMES.toList()))
        add("targetKinds", strArray(DslChecker.TARGET_KINDS.toList()))
        add("triggerSides", strArray(DslChecker.TRIGGER_SIDES.toList()))
        add("triggerStates", strArray(DslChecker.TRIGGER_STATES.toList()))
        add("eventKinds", strArray(DslChecker.EVENT_KINDS))
        addProperty("varNamePattern", DslChecker.namePattern.pattern)
        addProperty("maxCommandTimeSeconds", 86400.0)
        addProperty("tickSeconds", 0.05)

        add("commands", JsonObject().apply {
            DSL_COMMAND_TABLE.forEach { (name, spec) ->
                add(name, JsonObject().apply {
                    addProperty("type", spec.type.name)
                    add("args", JsonObject().apply {
                        spec.args.forEach { (argName, rule) ->
                            add(argName, JsonObject().apply {
                                addProperty("kind", rule.kind.name)
                                addProperty("required", rule.required)
                                rule.enumValues?.let { add("enumValues", strArray(it.toList())) }
                            })
                        }
                    })
                    spec.extraParamPrefix?.let { addProperty("extraParamPrefix", it) }
                })
            }
        })

        // コンストラクタ・条件呼出しなど、値位置で使う組み込み呼出しの引数仕様です。
        add("valueCalls", JsonObject().apply {
            fun ctorSpec(args: Map<String, DslChecker.ArgSpec>, result: String) = JsonObject().apply {
                addProperty("result", result)
                add("args", JsonObject().apply {
                    args.forEach { (n, s) ->
                        add(n, JsonObject().apply {
                            addProperty("type", s.type.name.lowercase())
                            addProperty("required", s.required)
                        })
                    }
                })
            }
            add("location", ctorSpec(DslChecker.LOCATION_ARGS, "location"))
            add("item", ctorSpec(DslChecker.ITEM_ARGS, "item"))
            add("block", ctorSpec(DslChecker.BLOCK_ARGS, "block"))
            add("target", ctorSpec(DslChecker.TARGET_ARGS, "entity"))
            add("random", ctorSpec(DslChecker.RANDOM_ARGS, "number"))
            add("myworld_spawn", ctorSpec(DslChecker.MYWORLD_ARGS, "location"))
            add("orientation", JsonObject().apply {
                addProperty("result", "entity")
                add("args", JsonObject().apply {
                    add("target", JsonObject().apply {
                        addProperty("type", "entity")
                        addProperty("required", true)
                    })
                })
            })
            add("player", ctorSpec(DslChecker.TARGET_COND_ARGS, "boolean"))
            add("exists", ctorSpec(DslChecker.TARGET_COND_ARGS, "boolean"))
        })

        add("eventArgs", JsonObject().apply {
            EventTriggerKind.entries.forEach { kind ->
                add(kind.name.lowercase(), JsonObject().apply {
                    EventTriggerArguments.argumentsFor(kind).forEach { arg ->
                        addProperty(arg.name, arg.type.name.lowercase())
                    }
                })
            }
        })

        add("properties", JsonObject().apply {
            ValueProperties.ALL.forEach { def ->
                add(def.id, JsonObject().apply {
                    add("sources", strArray(def.sources.map { it.name.lowercase() }))
                    addProperty("result", def.result.name.lowercase())
                    def.argType?.let { addProperty("argType", it.name.lowercase()) }
                })
            }
        })

        add("entityData", JsonObject().apply {
            EntityDataSchema.allIds.forEach { id ->
                addProperty(id, EntityDataSchema.definition(id)?.valueKind?.name?.lowercase() ?: "text")
            }
        })
    }

    private fun strArray(values: List<String>): JsonArray = JsonArray().apply {
        values.forEach { add(it) }
    }
}
