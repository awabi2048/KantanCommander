package me.awabi2048.kantancommander.dsl

import com.google.gson.JsonParser
import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphValidator
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.ValueType
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.logging.Logger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * 実機稼働中の旧形式プログラム（旧GUI保存JSON）から DSL への一括変換を検証します。
 *
 * 各サンプルについて、ScriptStore の読込（レガシー移行）→ DSL 印刷 →
 * DSL 解析・意味検証・コンパイル → 両グラフの構造・実行検証と、
 * 印刷テキストの不動点・意味ノード集合の一致を確認します。
 *
 * サンプルディレクトリが存在しない環境ではテストをスキップします。
 */
class DslLegacyConversionTest {
    private val sourceDir = File(LEGACY_DIR)

    @Test
    fun `旧形式サンプル全件がDSL変換と往復検証を通る`() {
        assumeTrue(sourceDir.isDirectory, "サンプルディレクトリがありません: $LEGACY_DIR")

        // ScriptStore は検証失敗ファイルを隔離先へ移動するため、
        // 原本を触らないようコピーした一時ディレクトリで読み込みます。
        val work = Files.createTempDirectory("dsl-legacy-").toFile()
        try {
            sourceDir.listFiles { f -> f.isFile && f.extension.equals("json", true) }
                ?.forEach { it.copyTo(work.resolve(it.name), overwrite = true) }
        } catch (e: Exception) {
            assumeTrue(false, "サンプルコピーに失敗しました: ${e.message}")
        }

        val store = ScriptStore(work, Logger.getLogger("dsl-legacy-test"))
        val scripts = store.listAll()
        assertTrue(scripts.isNotEmpty(), "読み込めたサンプルが0件です")

        val worldTypes = loadWorldTypes(scripts)
        val byId = scripts.associateBy { it.id }
        // 名前の一意なものだけ名前解決へ載せ、重複時はUUID表記へ退避します。
        val uniqueNames = scripts.map { it.name }.toSet()
        val nameResolver: (UUID) -> String? = { id ->
            byId[id]?.name?.takeIf { name -> scripts.count { it.name == name } == 1 }
        }
        val diskResolver: (String) -> DslDiskLink? = { name ->
            val target = byId[runCatching { UUID.fromString(name) }.getOrNull()]
                ?: scripts.firstOrNull { it.name == name }
            target?.let { DslDiskLink(it.id, it.graph, it.arguments) }
        }
        uniqueNames // 評価済みであることを明示（名前解決は resolver 側で行う）

        val failures = mutableListOf<String>()
        /** 旧グラフ自体が実行検証に通らない既存不正データ（変換とは別系の記録）です。 */
        val invalidData = mutableListOf<String>()
        var converted = 0
        for (script in scripts) {
            // 旧グラフが実行検証に通らないものは既存データの設定不足です。
            // DSL は「未設定」を表現しないため、それらは変換対象外として記録します。
            val valid = ExecutableScriptValidator.validate(script).isEmpty()
            try {
                roundTrip(script, worldTypes, nameResolver, diskResolver, valid)
                converted++
                if (!valid) invalidData += "${script.name}（変換済み）"
            } catch (e: Exception) {
                if (valid) failures += "${script.name} (${script.id}): ${e.message}"
                else invalidData += "${script.name} (${script.id}): ${e.message}"
            } catch (e: AssertionError) {
                if (valid) failures += "${script.name} (${script.id}): ${e.message}"
                else invalidData += "${script.name} (${script.id}): ${e.message}"
            }
            // デバッグ: 変換失敗したDSLをダンプ
            if (System.getenv("DSL_DUMP") != null) {
                runCatching {
                    val t = DslPrinter(nameResolver).print(script.graph, script.name)
                    println("---- ${script.name} ----\n$t")
                }
            }
        }
        if (invalidData.isNotEmpty()) {
            println("=== 実行検証に通らない既存データ ${invalidData.size}件（変換は実施） ===")
            invalidData.take(20).forEach(::println)
        }
        if (failures.isNotEmpty()) {
            println("=== DSL変換失敗 ${failures.size}/${scripts.size} ===")
            failures.take(80).forEach(::println)
        }
        assertEquals(
            emptyList<String>(), failures,
            "旧形式変換の失敗があります（成功 $converted/${scripts.size} 件）",
        )
    }

    @Test
    fun `初回読込でDSLへ変換書き戻しされ次回読込はDSLから復元される`() {
        assumeTrue(sourceDir.isDirectory, "サンプルディレクトリがありません: $LEGACY_DIR")

        val work = Files.createTempDirectory("dsl-legacy-store-").toFile()
        try {
            sourceDir.listFiles { f -> f.isFile && f.extension.equals("json", true) }
                ?.forEach { it.copyTo(work.resolve(it.name), overwrite = true) }
        } catch (e: Exception) {
            assumeTrue(false, "サンプルコピーに失敗しました: ${e.message}")
        }

        // 初回読込: グラフのみの旧JSONへ dsl 欄が書き戻されることを確認します。
        val first = ScriptStore(work, Logger.getLogger("dsl-legacy-store"))
        val scripts = first.listAll()
        assumeTrue(scripts.isNotEmpty())
        val worldTypes = loadWorldTypes(scripts)
        val checked = scripts.filter {
            it.dsl != null && ExecutableScriptValidator.validate(it).isEmpty()
        }.take(10)
        assumeTrue(checked.isNotEmpty(), "DSL へ変換された有効サンプルがありません")
        checked.forEach { script ->
            val file = work.resolve("${script.id}.json")
            assertTrue(
                file.readText(Charsets.UTF_8).contains("\"dsl\""),
                "初回読込で dsl 欄が書き戻されていません: ${script.name}",
            )
        }
        val expected = checked.associate { it.id to it.dsl!! }

        // 二度目の読込は dsl 欄から再コンパイルし、同じ意味内容を持つことを
        // 確認します（新しい ScriptStore でキャッシュを持ち越しません）。
        val second = ScriptStore(work, Logger.getLogger("dsl-legacy-store"))
        checked.forEach { script ->
            val reloaded = second.load(script.id)
                ?: error("DSL 正本から復元できません: ${script.name}")
            assertEquals(
                signatures(script.graph).toSet(),
                signatures(reloaded.graph).toSet(),
                "DSL 正本から復元したグラフの意味内容が一致しません: ${script.name}",
            )
            // 復元側を再印刷すると保存済みの dsl 欄と同じ正規形になることを確認します。
            assertEquals(
                expected[script.id],
                DslPrinter({ id -> second.load(id)?.name }).print(reloaded.graph, reloaded.name),
                "DSL 正本の再印刷が保存テキストと一致しません: ${script.name}",
            )
        }
    }

    /**
     * 旧JSON → DSL → モデル → DSL の往復です。
     * 再コンパイル結果の構造・実行検証（旧グラフが不正な場合は実行検証を除く）、
     * 印刷の不動点、意味内容ノード集合の一致を確認します。
     */
    private fun roundTrip(
        script: DiskScript,
        worldTypes: Map<String, ValueType>,
        nameResolver: (UUID) -> String?,
        diskResolver: (String) -> DslDiskLink?,
        expectValid: Boolean,
    ) {
        val text1 = DslPrinter(nameResolver).print(script.graph, script.name)
        val g1 = DslCompiler(worldTypes, diskResolver).compile(DslParser.parse(text1))
        assertEquals(emptyList<String>(), GraphValidator.validate(g1), "再コンパイルの構造検証")
        val s1 = script.copy(id = UUID.randomUUID(), graph = g1)
        if (expectValid) {
            assertEquals(
                emptyList<String>(),
                ExecutableScriptValidator.validate(s1).map { "${it.code}:${it.message}" },
                "再コンパイルの実行検証",
            )
        }
        val text2 = DslPrinter(nameResolver).print(g1, script.name)
        assertEquals(text1, text2, "印刷テキストは不動点であるべきです")
        // 多重集合比較: 欠落側と超過側だけを報告します。
        val expected = signatures(script.graph).groupingBy { it }.eachCount()
        val actual = signatures(g1).groupingBy { it }.eachCount()
        val missing = (expected.keys + actual.keys)
            .filter { (expected[it] ?: 0) != (actual[it] ?: 0) }
            .map { k ->
                val parts = mutableListOf<String>()
                repeat(maxOf(0, (expected[k] ?: 0) - (actual[k] ?: 0))) { parts += "- $k" }
                repeat(maxOf(0, (actual[k] ?: 0) - (expected[k] ?: 0))) { parts += "+ $k" }
                parts
            }.flatten()
        assertEquals(emptyList<String>(), missing, "往路・復路で意味ノード集合が一致すべきです")
    }

    /**
     * 意味内容シグネチャです。DSL 化で構造要素（MERGE・FOR_END・一時変数）は
     * 再構成されるため、観測可能な命令・条件・変数操作ノードの型＋欄を
     * 集合比較します（else-if 展開による共有末端の複製は集合で吸収します）。
     */
    private fun signatures(graph: CommandGraph): List<String> {
        val items = graph.nodes.values
            // MERGE／FOR_END はペアリング専用で意味欄を持たず、コンパイラ生成の
            // 一時変数ノードは DSL へ現れないため比較から外します。
            .filter { it.type !in STRUCTURAL_TYPES }
            .filter { !it.string("name").startsWith("dsl.tmp") }
            .map { n ->
                // RETURN_VALUE は値欄だけを意味内容として保持します。
                if (n.type == CommandType.RETURN_VALUE) {
                    "RETURN_VALUE|${normalizeNumbers(TEMP_REF.replace(n.string("value"), "%{TMP}"))}"
                } else nodeSignature(n)
            }
            .toMutableList()
        // 条件枝の null 出口は実行時にプログラム終了（done）を意味します。
        // DSL 往復では明示の RETURN_VALUE へ正規化されるため、等価な
        // 終了マーカーとして数えます。
        graph.nodes.values.filter { it.type == CommandType.CONDITION }.forEach { c ->
            if (c.trueNext == null) items += "RETURN_VALUE|"
            if (c.falseNext == null) items += "RETURN_VALUE|"
        }
        // else-if 展開や合流位置の違いでノード順は揺れうるため、
        // 多重集合として比較します。
        return items.sorted()
    }

    private fun nodeSignature(n: CommandNode): String {
        // TEMP_SET と EXECUTION スコープの VARIABLE はどちらも実行内変数への
        // 書き込みであり、世代の違う同じ表現です（DSL は TEMP_SET へ統一します）。
        if (n.type == CommandType.TEMP_SET ||
            (n.type == CommandType.VARIABLE && n.string("scope") == "EXECUTION")
        ) {
            return execVarSignature(n)
        }
        val defaults = n.type.defaults
        val params = n.params.toSortedMap().entries
            // 旧GUI保存の残滓キー・既定値と同値の欄は DSL 往復で再構成されないため除外します。
            .filter { (k, v) ->
                if (k in LEGACY_KEYS) return@filter false
                // 空欄は「未設定」と同義であり、往復で未書出しへ正規化されます。
                if (v.isBlank()) return@filter false
                // ENTITY_OPERATION は entityDataPath の種別で値欄が1つだけ意味を持ち、
                // 他の値欄は GUI 操作履歴の残存データです（DSL では選択値のみ復元）。
                if (n.type == CommandType.ENTITY_OPERATION && k in ENTITY_OP_VALUE_KEYS) {
                    val path = n.string("entityDataPath")
                    val active = when {
                        path.startsWith("property:") -> "propertyValue"
                        path.startsWith("attribute:") -> "attributeValue"
                        path == "tag" -> "tagEntries"
                        else -> null
                    }
                    if (k != active) return@filter false
                }
                // CALCULATE の原子式（数値・単一参照）は実行結果が ASSIGN と
                // 同じため、DSL 往復でリテラル代入へ正規化される差分として
                // 同一視します。
                if (k == "changeMode" && v == "CALCULATE" &&
                    ATOMIC_VALUE.matches(n.string("value").trim())
                ) return@filter false
                val d = defaults[k] ?: return@filter true
                normalizeNumbers(v) != normalizeNumbers(d)
            }
            .joinToString(",") { (k, v) ->
                // `%{dsl.tmpN}%` 参照は実体化された式の所在だけを意味するため、
                // 番号を潰して配置の揺れを吸収します。
                "$k=${normalizeNumbers(TEMP_REF.replace(v, "%{TMP}"))}"
            }
        val specs = specSignature(n)
        return "${n.type}|$params|$specs|${n.itemTempRef}|${n.blockTempRef}|${n.snapshot != null}"
    }

    /**
     * 実行内変数書き込みの正規形シグネチャです。
     * TEMP_SET／VARIABLE(EXECUTION) の表層差（operation・scope・type 欄）を
     * 落とし、書き込み様式と値欄だけを比較します。CALCULATE の原子式
     * （数値・単一参照）は ASSIGN と実行結果が同じため ASSIGN へ畳みます。
     */
    private fun execVarSignature(n: CommandNode): String {
        var mode = n.string("changeMode", "ASSIGN")
        when (n.string("operation")) {
            "DELETE" -> mode = "DELETE"
            "COPY" -> mode = "COPY"
        }
        val value = n.string("value")
        if (mode == "CALCULATE" && ATOMIC_VALUE.matches(value.trim())) mode = "ASSIGN"
        // 値欄の意味は書き込み様式・値型で決まります。entity/location 変数は
        // 値を spec 欄へ持つため `value`/`range` は GUI 操作履歴の残存データです。
        val hasSpecValue = n.temporaryEntityTargetSpec != null ||
            n.temporaryLocationPositionSpec != null || n.temporaryLocationFacingSpec != null
        val relevant = when {
            mode == "COPY" -> listOf("source")
            mode == "PROPERTY" -> listOf("propertySource", "property")
            mode == "RANDOM" -> listOf("range")
            mode == "DELETE" || hasSpecValue -> emptyList()
            else -> listOf("value")
        }
        val fields = relevant
            .mapNotNull { k ->
                n.string(k).takeIf(String::isNotBlank)
                    ?.let { "$k=${normalizeNumbers(TEMP_REF.replace(it, "%{TMP}"))}" }
            }
            .joinToString(",")
        return "EXECVAR|${n.string("name")}|$mode|$fields|${specSignature(n)}"
    }

    /** 位置・対象・向き spec 欄のシグネチャ部です（宣言順・非 null のみ）。 */
    private fun specSignature(n: CommandNode): String {
        val specList = mutableListOf(
            n.targetSpec, n.secondaryTargetSpec, n.destinationSpec, n.destinationTargetSpec,
            n.destinationFacingSpec, n.conditionPositionSpec, n.blockPositionSpec,
            n.blockFromSpec, n.blockToSpec, n.structurePlacePositionSpec,
            n.groupPivotPositionSpec, n.soundPositionSpec, n.particlePositionSpec,
            n.particleEndPositionSpec, n.fireworkPositionSpec, n.summonPositionSpec,
            n.executionPositionSpec, n.temporaryEntityTargetSpec,
            n.temporaryLocationPositionSpec, n.temporaryLocationFacingSpec,
        )
        // BLOCK_OPERATION は operation で使う位置欄が決まり、他方は GUI 操作
        // 履歴の残存データです（setblock 単点／fill 範囲の切替で残ります）。
        if (n.type == CommandType.BLOCK_OPERATION) {
            if (n.string("operation") == "fill") specList.remove(n.blockPositionSpec)
            else {
                specList.remove(n.blockFromSpec)
                specList.remove(n.blockToSpec)
            }
        }
        return specList.filterNotNull()
            .joinToString(",") { normalizeNumbers(TEMP_REF.replace(canonicalSpec(it).toString(), "TMP")) }
    }

    /**
     * spec 中の TEMPORARY 対象参照を正規形へ揃えます。
     * kind=TEMPORARY は変数参照であるため選択条件（sort・距離等）は意味を
     * 持たず、GUI 操作履歴として残っている値を比較から外します。
     */
    private fun canonicalSpec(spec: Any): Any = when (spec) {
        is TargetSpec -> if (spec.kind == TargetKind.TEMPORARY) {
            TargetSpec(kind = TargetKind.TEMPORARY, tempName = spec.tempName)
        } else spec
        is PositionSpec -> spec.copy(target = spec.target?.let { canonicalSpec(it) as TargetSpec })
        is FacingSpec -> spec.copy(target = spec.target?.let { canonicalSpec(it) as TargetSpec })
        else -> spec
    }

    private fun normalizeNumbers(text: String): String =
        NUMBER_TOKEN.replace(text) { m ->
            m.value.toDoubleOrNull()?.let { d ->
                if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
            } ?: m.value
        }

    /**
     * 付随データのワールド変数定義を全ワールド分まとめて型表へします。
     * 実データには変数定義ファイルへ含まれないワールド変数への参照が残って
     * います（他プログラムの WORLD スコープ書き込みで実行時に生成される
     * ため、旧GUIではプログラム内宣言が不要でした）。コーパス内の
     * WORLD スコープ操作と `${name}` 参照から型を推論して補完します。
     */
    private fun loadWorldTypes(scripts: List<DiskScript>): Map<String, ValueType> {
        val dir = sourceDir.resolve("attendant_data/world-variables")
        val result = mutableMapOf<String, ValueType>()
        dir.listFiles { f -> f.extension.equals("json", true) }?.forEach { file ->
            val defs = JsonParser.parseString(file.readText(Charsets.UTF_8))
                .asJsonObject["definitions"]?.asJsonObject ?: return@forEach
            for ((name, def) in defs.entrySet()) {
                val type = def.asJsonObject["type"]?.asString
                    ?.let { runCatching { ValueType.valueOf(it) }.getOrNull() }
                    ?: continue
                result.putIfAbsent(name, type)
            }
        }
        scripts.flatMap { it.graph.nodes.values }.forEach { n ->
            // WORLD スコープの変数操作は型欄を持つため、そのまま定義として採用します。
            if (n.type == CommandType.VARIABLE && n.string("scope") == "WORLD") {
                val type = n.string("type")
                    .let { runCatching { ValueType.valueOf(it) }.getOrNull() }
                if (n.string("name").isNotBlank() && type != null) {
                    result.putIfAbsent(n.string("name"), type)
                }
            }
            // 残りの `${name}` 参照は文字列埋め込みのみで使われているため
            // STRING として補完します（数値・真偽値で必須の変数は上記で型付き
            // 定義として拾えています）。システム変数は loop.count へ写像済みです。
            n.params.values.forEach { v ->
                WORLD_VAR_REF.findAll(v).forEach { m ->
                    val name = m.groupValues[1]
                    if (name != "CURRENT_LOOP_COUNT") {
                        result.putIfAbsent(name, ValueType.STRING)
                    }
                }
            }
        }
        return result
    }

    private companion object {
        const val LEGACY_DIR = "D:/Minecraft/Chiyogami-26.2/transported/old_kankoma_programs"
        val NUMBER_TOKEN = Regex("[+-]?\\d+\\.\\d+|[+-]?\\d+")
        /**
         * 旧条件形式の残存キー・GUI の表示状態欄・DSL では別表現へ正規化される欄です。
         * `inverted` は条件式の `!`／枝構造へ畳み込まれるため欄としては保持しません。
         */
        val LEGACY_KEYS = setOf("leftScope", "leftName", "right", "configuredFields", "kind", "inverted")

        /** ENTITY_OPERATION の排他的な値欄です（entityDataPath の種別で1つだけ有効）。 */
        val ENTITY_OP_VALUE_KEYS = setOf("propertyValue", "attributeValue", "tagEntries")

        /** `dsl.tmpN` 生成一時変数の参照（シグネチャ比較で番号を潰す）です。 */
        val TEMP_REF = Regex("%\\{dsl\\.tmp\\d+}%|dsl\\.tmp\\d+")

        /** パラメータ文字列中の `${name}` ワールド変数参照です。 */
        val WORLD_VAR_REF = Regex("\\$\\{([^}]+)}")

        /**
         * CALCULATE で「数値リテラル」または「単一変数参照」だけの式です。
         * この形は ASSIGN（値欄の解決結果をそのまま書き込み）と実行結果が
         * 同じであるため、シグネチャ比較では ASSIGN へ畳みます。
         */
        val ATOMIC_VALUE = Regex("-?\\d+(\\.\\d+)?|%\\{[^}]+}%|\\$\\{[^}]+}")

        /** DSL 表層に出ず意味欄も持たない構造専用ノードです。 */
        val STRUCTURAL_TYPES = setOf(
            CommandType.MERGE,
            CommandType.FOR_END,
        )
    }
}
