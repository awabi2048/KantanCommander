package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.conditionSubject
import me.awabi2048.kantancommander.model.hasDiskContent
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.LogRecord

@Suppress("DEPRECATION")
class ScriptStoreTest {
    @TempDir
    lateinit var temp: File

    @Test
    fun `legacy kind-only condition migrates to its subject`() {
        // issue #27: 旧 `kind` のみの保存は読込時に判定対象へ正本化し、
        // 従来と同じ条件判定として実行・検証できるようにします。
        // v18受付（18・17・16・15）の範囲内で検証するため、形式番号は15を使います。
        val dir = temp.resolve("condition-subject").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val conditionId = UUID.randomUUID()
        val mergeId = UUID.randomUUID()
        dir.resolve("$scriptId.json").writeText(
            """
            {
              "formatVersion": 15,
              "id": "$scriptId",
              "name": "legacy-condition",
              "owner": "${UUID.randomUUID()}",
              "createdAt": 0,
              "activation": "NEEDS_REDSTONE",
              "timer": {"enabled": false, "intervalSeconds": 1.0},
              "graph": {
                "entryNodeId": "$conditionId",
                "nodes": {
                  "$conditionId": {
                    "id": "$conditionId",
                    "type": "CONDITION",
                    "params": {"kind": "TARGET_EXISTS", "inverted": "false"},
                    "targetSpec": {"kind": "NEARBY_PLAYERS", "sort": "NEAREST"},
                    "pairedNodeId": "$mergeId",
                    "trueNext": "$mergeId"
                  },
                  "$mergeId": {
                    "id": "$mergeId",
                    "type": "MERGE",
                    "params": {},
                    "pairedNodeId": "$conditionId"
                  }
                }
              },
              "revision": 0
            }
            """.trimIndent()
        )
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val node = loaded.graph.nodes.getValue(conditionId)
        // プレイヤー系対象の旧 TARGET_EXISTS は PLAYER へ正本化します。
        assertEquals(ConditionSubject.PLAYER, node.conditionSubject())
        assertTrue("kind" !in node.params)
        // 反転指定は維持します。
        assertEquals("false", node.string("inverted", ""))
        // 実行前検証で判定対象タブの未設定として拒否しません。
        assertFalse(
            ExecutableScriptValidator.validate(loaded).any {
                it.nodeId == conditionId && it.fieldKeys == setOf("kind")
            },
        )
    }

    @Test
    fun `structured script round trips and placement copy is independent`() {
        val store = ScriptStore(temp.resolve("structured"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "test")
        val node = CommandType.DISPLAY_TEXT.newNode()
        script.graph = CommandGraph(node.id, linkedMapOf(node.id to node))
        store.save(script)

        val loaded = requireNotNull(store.load(script.id))
        val copy = requireNotNull(store.copyForPlacement(loaded))
        copy.graph.nodes[node.id]?.params?.set("text", "changed")
        assertEquals("", loaded.graph.nodes[node.id]?.string("text"))
        assertNotEquals(script.id, copy.id)
    }

    @Test
    fun `folded for round trips with its inner graph and copies stay independent`() {
        val store = ScriptStore(temp.resolve("folded"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "test")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        val end = requireNotNull(start.pairedNodeId)
        val body = GraphEditor.appendToForBody(script.graph, start.id, CommandType.DISPLAY_TEXT)
        GraphEditor.append(script.graph, CommandType.GIVE_ITEM)
        assertTrue(GraphEditor.foldForStart(script.graph, start.id))
        store.save(script)

        val loaded = requireNotNull(store.load(script.id))
        val folded = loaded.graph.nodes.getValue(start.id)
        assertEquals(CommandType.FOLDED_FOR, folded.type)
        val inner = requireNotNull(folded.foldedGraph)
        assertEquals(setOf(start.id, body.id, end), inner.nodes.keys)
        // 折り畳み自体は実行内容のため、ディスク内容ありと判定する。
        assertTrue(loaded.hasDiskContent())

        val copy = requireNotNull(store.copyForPlacement(loaded))
        copy.graph.nodes.getValue(start.id).foldedGraph?.nodes?.getValue(body.id)?.params?.set("text", "changed")
        assertEquals("", loaded.graph.nodes.getValue(start.id).foldedGraph?.nodes?.getValue(body.id)?.string("text"))
    }

    @Test
    fun `placement copy migrates embedded previous generation formats`() {
        val store = ScriptStore(temp.resolve("embedded-migration"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "test")
        val node = CommandType.DISPLAY_TEXT.newNode()
        script.graph = CommandGraph(node.id, linkedMapOf(node.id to node))
        store.save(script)

        val loaded = requireNotNull(store.load(script.id))
        // 直前世代の同梱スナップショットは移行して配置できる。
        val migrated = requireNotNull(store.copyForPlacement(loaded.copy(formatVersion = 16)))
        assertEquals(18, migrated.formatVersion)
        assertNotEquals(loaded.id, migrated.id)
        // 受付対象外の旧形式は例外を逃がさずnullとなり、設置側は正常に拒否できる。
        assertNull(store.copyForPlacement(loaded.copy(formatVersion = 14)))
    }

    @Test
    fun `v10 collapsed display state is quarantined as incompatible`() {
        // issue #4 のスレッド指示により旧データは読み込み不可とします。
        // v10は移行せず非対応形式として隔離します。
        val dir = temp.resolve("v10-collapsed").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        val staleFor = UUID.randomUUID()
        val staleCondition = UUID.randomUUID()
        dir.resolve("$scriptId.json").writeText(
            """
            {
              "formatVersion": 10,
              "id": "$scriptId",
              "name": "v10",
              "owner": "${UUID.randomUUID()}",
              "createdAt": 0,
              "listed": false,
              "activation": "NEEDS_REDSTONE",
              "timer": {"enabled": false, "intervalSeconds": 1.0},
              "graph": {
                "entryNodeId": "$nodeId",
                "nodes": {
                  "$nodeId": {
                    "id": "$nodeId",
                    "type": "WAIT",
                    "params": {"seconds": "1"}
                  }
                }
              },
              "revision": 0,
              "contentModified": false,
              "collapsedForStartIds": ["$staleFor"],
              "collapsedConditionIds": ["$staleCondition"]
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        assertNull(store.load(scriptId))
        assertFalse(dir.resolve("$scriptId.json").exists())
        assertEquals(1, requireNotNull(dir.resolve("incompatible").listFiles()).size)
    }

    @Test
    fun `revision rejects stale saves and compare and set updates`() {
        val store = ScriptStore(temp.resolve("revisions"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "revision")
        val first = requireNotNull(store.load(script.id))
        val stale = requireNotNull(store.load(script.id))

        first.name = "first"
        store.save(first)
        assertEquals(first.revision, requireNotNull(store.load(script.id)).revision)

        stale.name = "stale"
        assertThrows(java.util.ConcurrentModificationException::class.java) {
            store.save(stale)
        }
        assertEquals("first", requireNotNull(store.load(script.id)).name)

        val expected = requireNotNull(store.load(script.id)).revision
        assertTrue(
            store.update(script.id, expectedRevision = expected) {
                it.name = "compare-and-set"
                true
            } == true,
        )
        assertNull(
            store.update(script.id, expectedRevision = expected) {
                it.name = "must-not-win"
                true
            },
        )
        assertEquals("compare-and-set", requireNotNull(store.load(script.id)).name)
    }

    @Test
    fun `normal mutations are blocked atomically and bypass mutations notify after success`() {
        var locked = false
        val bypassed = mutableListOf<Pair<UUID, ScriptMutationOperation>>()
        val store = ScriptStore(
            temp.resolve("test-lock"),
            Logger.getAnonymousLogger(),
            mutationGuard = { id, _, operation ->
                if (locked) throw ScriptMutationLockedException(id, operation)
            },
            bypassedMutationNotifier = { id, operation -> bypassed += id to operation },
        )
        val script = store.create(UUID.randomUUID(), "locked")
        val edited = requireNotNull(store.load(script.id)).apply { name = "edited" }

        locked = true
        assertThrows(ScriptMutationLockedException::class.java) { store.save(edited) }
        assertEquals("locked", requireNotNull(store.load(script.id)).name)

        store.saveBypassingExecutionLock(edited)
        assertEquals("edited", requireNotNull(store.load(script.id)).name)
        store.deleteBypassingExecutionLock(script.id)

        assertEquals(
            listOf(
                script.id to ScriptMutationOperation.SAVE,
                script.id to ScriptMutationOperation.DELETE,
            ),
            bypassed,
        )
        assertNull(store.load(script.id))
    }

    @Test
    fun `concurrent updates serialize read modify write and preserve independent node edits`() {
        val store = ScriptStore(temp.resolve("concurrent"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "concurrent")
        val first = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        val second = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        store.save(script)

        val start = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(
                executor.submit<Boolean?> {
                    start.await()
                    store.update(script.id) {
                        it.graph.nodes[first.id]?.params?.set("text", "left")
                        true
                    }
                },
                executor.submit<Boolean?> {
                    start.await()
                    store.update(script.id) {
                        it.graph.nodes[second.id]?.params?.set("text", "right")
                        true
                    }
                },
            )
            futures.forEach { future ->
                assertTrue(future.get(10, TimeUnit.SECONDS) == true)
            }
        } finally {
            executor.shutdownNow()
        }

        val updated = requireNotNull(store.load(script.id))
        assertEquals("left", updated.graph.nodes[first.id]?.string("text"))
        assertEquals("right", updated.graph.nodes[second.id]?.string("text"))
    }

    @Test
    fun `stale node deletion cannot remove a node after another editor saves`() {
        val store = ScriptStore(temp.resolve("stale-delete"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "stale-delete")
        val node = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        store.save(script)

        val deleteView = requireNotNull(store.load(script.id))
        val otherView = requireNotNull(store.load(script.id)).apply {
            graph.nodes[node.id]?.params?.set("text", "keep")
        }
        store.save(otherView)

        assertNull(
            store.update(script.id, expectedRevision = deleteView.revision) {
                if (GraphEditor.delete(it.graph, node.id)) true else null
            },
        )
        val current = requireNotNull(store.load(script.id))
        assertTrue(node.id in current.graph.nodes)
        assertEquals("keep", current.graph.nodes[node.id]?.string("text"))
    }

    @Test
    fun `legacy tick format is quarantined as incompatible`() {
        val dir = temp.resolve("legacy-ticks").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        dir.resolve("$scriptId.json").writeText(
            """
            {
              "formatVersion": 6,
              "id": "$scriptId",
              "name": "legacy",
              "owner": "${UUID.randomUUID()}",
              "createdAt": 1,
              "listed": true,
              "activation": "NEEDS_REDSTONE",
              "timer": {"enabled": true, "intervalUnits": 6},
              "graph": {
                "entryNodeId": "$nodeId",
                "nodes": {
                  "$nodeId": {
                    "id": "$nodeId",
                    "type": "WAIT",
                    "params": {"ticks": "1"}
                  }
                }
              }
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        assertNull(store.load(scriptId))
        assertFalse(dir.resolve("$scriptId.json").exists())
        assertEquals(1, requireNotNull(dir.resolve("incompatible").listFiles()).size)
    }

    @Test
    fun `v9 integer timer seconds are quarantined as incompatible`() {
        val dir = temp.resolve("v9-timer").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val owner = UUID.randomUUID()
        dir.resolve("$scriptId.json").writeText(
            """
            {
              "formatVersion": 9,
              "id": "$scriptId",
              "name": "v9",
              "owner": "$owner",
              "createdAt": 0,
              "listed": false,
              "activation": "NEEDS_REDSTONE",
              "timer": {"enabled": true, "intervalSeconds": 5},
              "graph": {"nodes": {}},
              "revision": 0,
              "contentModified": false
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        assertNull(store.load(scriptId))
        assertFalse(dir.resolve("$scriptId.json").exists())
        assertEquals(1, requireNotNull(dir.resolve("incompatible").listFiles()).size)
    }

    @Test
    fun `unsupported legacy format is quarantined`() {
        val dir = temp.resolve("structured").also(File::mkdirs)
        dir.resolve("${UUID.randomUUID()}.json").writeText("""{"formatVersion":1}""")
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        assertTrue(store.listAll().isEmpty())
    }

    @Test
    fun `unsupported format is quarantined and does not warn repeatedly`() {
        val dir = temp.resolve("incompatible")
        val scriptId = UUID.randomUUID()
        val source = """{"formatVersion":8}"""
        val sourceFile = dir.resolve("$scriptId.json")
        dir.mkdirs()
        sourceFile.writeText(source, Charsets.UTF_8)
        val (logger, handler) = recordingLogger()

        val store = ScriptStore(dir, logger)
        repeat(2) { assertTrue(store.listAll().isEmpty()) }

        assertFalse(sourceFile.exists())
        val quarantined = requireNotNull(dir.resolve("incompatible").listFiles()).single()
        assertTrue(quarantined.name.startsWith("$scriptId-v8-"))
        assertEquals(source, quarantined.readText(Charsets.UTF_8))
        assertEquals(1, handler.warningCount())
    }

    @Test
    fun `unknown target kind is quarantined before deep copy`() {
        val dir = temp.resolve("unknown-target-kind")
        val scriptId = UUID.randomUUID()
        val source = validScriptJson(scriptId).apply {
            val node = getAsJsonObject("graph")
                .getAsJsonObject("nodes")
                .entrySet()
                .single()
                .value
                .asJsonObject
            node.getAsJsonObject("targetSpec").addProperty("kind", "INHERITED_TARGET")
        }
        dir.mkdirs()
        val sourceFile = dir.resolve("$scriptId.json")
        sourceFile.writeText(GsonBuilder().setPrettyPrinting().create().toJson(source), Charsets.UTF_8)
        val (logger, handler) = recordingLogger()

        val store = ScriptStore(dir, logger)
        assertTrue(store.listAll().isEmpty())
        assertNull(store.load(scriptId))
        assertFalse(sourceFile.exists())
        assertEquals(1, handler.warningCount())
        assertEquals(1, requireNotNull(dir.resolve("corrupt").listFiles()).size)
    }

    @Test
    fun `missing target kind is quarantined before Gson can create a nullable model`() {
        val dir = temp.resolve("missing-target-kind")
        val scriptId = UUID.randomUUID()
        val source = validScriptJson(scriptId).apply {
            val node = getAsJsonObject("graph")
                .getAsJsonObject("nodes")
                .entrySet()
                .single()
                .value
                .asJsonObject
            node.getAsJsonObject("targetSpec").remove("kind")
        }
        dir.mkdirs()
        val sourceFile = dir.resolve("$scriptId.json")
        sourceFile.writeText(GsonBuilder().setPrettyPrinting().create().toJson(source), Charsets.UTF_8)
        val (logger, handler) = recordingLogger()

        val store = ScriptStore(dir, logger)
        assertTrue(store.listAll().isEmpty())
        assertFalse(sourceFile.exists())
        assertEquals(1, handler.warningCount())
        assertEquals(1, requireNotNull(dir.resolve("corrupt").listFiles()).size)
    }

    @Test
    fun `failed quarantine is negatively cached until the source file changes`() {
        val dir = temp.resolve("failed-quarantine")
        val scriptId = UUID.randomUUID()
        dir.mkdirs()
        val sourceFile = dir.resolve("$scriptId.json")
        sourceFile.writeText("""{"formatVersion":8}""", Charsets.UTF_8)
        // 隔離先を通常ファイルにして、Files.moveが失敗する状態を作ります。
        dir.resolve("incompatible").writeText("not a directory", Charsets.UTF_8)
        val (logger, handler) = recordingLogger()

        val store = ScriptStore(dir, logger)
        val firstWarningCount = handler.warningCount()
        assertTrue(sourceFile.exists())
        repeat(3) { assertTrue(store.listAll().isEmpty()) }

        assertEquals(1, firstWarningCount)
        assertEquals(firstWarningCount, handler.warningCount())

        // 更新されたファイルは負のキャッシュを無効化し、修復を再確認します。
        sourceFile.writeText("""{"formatVersion":8,"changed":true}""", Charsets.UTF_8)
        assertTrue(store.listAll().isEmpty())
        assertEquals(firstWarningCount + 1, handler.warningCount())
    }

    @Test
    fun `unset placement and written output use independent unlisted scripts`() {
        val store = ScriptStore(temp.resolve("structured"), Logger.getAnonymousLogger())
        val placement = store.createPlacement("unset")

        val node = CommandType.DISPLAY_TEXT.newNode()
        node.params["text"] = "before"
        placement.graph = CommandGraph(node.id, linkedMapOf(node.id to node))
        store.save(placement)

        val output = store.copyForItem(placement)
        placement.graph.nodes[node.id]?.params?.set("text", "after")
        store.save(placement)

        assertNotEquals(placement.id, output.id)
        assertEquals("before", requireNotNull(store.load(output.id)).graph.nodes[node.id]?.string("text"))
        assertEquals("after", requireNotNull(store.load(placement.id)).graph.nodes[node.id]?.string("text"))
    }

    @Test
    fun `invalid copied disk graph is rejected recursively`() {
        val store = ScriptStore(temp.resolve("structured"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "nested")
        val call = CommandType.DISK_CALL.newNode()
        val missing = UUID.randomUUID()
        call.snapshot = CommandGraph(missing, linkedMapOf())
        script.graph = CommandGraph(call.id, linkedMapOf(call.id to call))

        assertThrows(IllegalArgumentException::class.java) { store.save(script) }
    }

    @Test
    fun `configured map width rejects save without replacing stored script`() {
        val store = ScriptStore(
            temp.resolve("width"),
            Logger.getAnonymousLogger(),
            GraphLimits(maximumMapWidth = 3),
        )
        val script = store.create(UUID.randomUUID(), "width")
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)

        assertThrows(IllegalArgumentException::class.java) { store.save(script) }
        assertTrue(requireNotNull(store.load(script.id)).graph.nodes.isEmpty())
    }

    @Test
    fun `configured map height rejects branched save without replacing stored script`() {
        val store = ScriptStore(
            temp.resolve("height"),
            Logger.getAnonymousLogger(),
            GraphLimits(maximumMapHeight = 3),
        )
        val script = store.create(UUID.randomUUID(), "height")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.TRUE, CommandType.DISPLAY_TEXT)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.FALSE, CommandType.DISPLAY_TEXT)

        assertThrows(IllegalArgumentException::class.java) { store.save(script) }
        assertTrue(requireNotNull(store.load(script.id)).graph.nodes.isEmpty())
    }

    @Test
    fun `lowering config limits does not quarantine previously saved scripts`() {
        val dir = temp.resolve("limits-shrink")
        val wideStore = ScriptStore(dir, Logger.getAnonymousLogger())
        val script = wideStore.create(UUID.randomUUID(), "wide")
        repeat(3) { GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT) }
        wideStore.save(script)

        // リロード相当: 同じ保存先をより小さい上限で開き直しても、既存データは隔離されず読み込める。
        val shrunk = ScriptStore(dir, Logger.getAnonymousLogger(), GraphLimits(maximumNodeCount = 2))
        val loaded = requireNotNull(shrunk.load(script.id))
        assertEquals(3, loaded.graph.nodes.size)
        assertFalse(dir.resolve("corrupt").exists() && dir.resolve("corrupt").list().orEmpty().isNotEmpty())
    }

    @Test
    fun `load hands out independent copies so unsaved edits stay invisible`() {
        val store = ScriptStore(temp.resolve("copy"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "copy")
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        store.save(script)

        val first = requireNotNull(store.load(script.id))
        first.graph.nodes.values.forEach { it.params["text"] = "unsaved" }

        val second = requireNotNull(store.load(script.id))
        assertTrue(second.graph.nodes.values.all { it.string("text").isBlank() })
    }

    @Test
    fun `non-default trigger counts as disk output content`() {
        // contentModified 欄の廃止に伴い、内容判定は派生値です。
        // ノード・引数・既定以外の起動方法を内容とし、名前のみの変更は
        // 既定名との区別が付かないため内容に含めません。
        val store = ScriptStore(temp.resolve("metadata-edits"), Logger.getAnonymousLogger())

        val renamed = store.createPlacement("default")
        val renamedEdit = requireNotNull(store.load(renamed.id)).apply { name = "renamed" }
        store.save(renamedEdit)
        assertFalse(requireNotNull(store.load(renamed.id)).hasDiskContent())
        assertTrue(requireNotNull(store.load(renamed.id)).graph.nodes.isEmpty())

        val timed = store.createPlacement("default")
        val timedEdit = requireNotNull(store.load(timed.id)).apply {
            trigger = ScriptTrigger(TriggerKind.TIMER, intervalSeconds = 5.0)
        }
        store.save(timedEdit)
        assertTrue(requireNotNull(store.load(timed.id)).hasDiskContent())
        assertTrue(requireNotNull(store.load(timed.id)).graph.nodes.isEmpty())

        val untouched = store.createPlacement("default")
        assertTrue(!requireNotNull(store.load(untouched.id)).hasDiskContent())
    }

    @Test
    fun `library and history are independent relations and history survives library removal`() {
        val dir = temp.resolve("relations")
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val owner = UUID.randomUUID()
        val editor = UUID.randomUUID()
        val otherEditor = UUID.randomUUID()
        val script = store.createPlacement("placement")

        // 編集者ごとに同じ正本UUIDを履歴へ登録できます。
        store.save(requireNotNull(store.load(script.id)).apply { name = "edited" }, editor)
        store.recordHistory(otherEditor, script.id)
        assertEquals(listOf(script.id), store.listHistory(editor).map { it.id })
        assertEquals(listOf(script.id), store.listHistory(otherEditor).map { it.id })
        assertTrue(store.listLibrary(owner).isEmpty())

        store.addToLibrary(owner, script.id)
        assertEquals(listOf(script.id), store.listLibrary(owner).map { it.id })
        store.removeFromLibrary(owner, script.id)
        assertTrue(store.listLibrary(owner).isEmpty())
        assertEquals(listOf(script.id), store.listHistory(editor).map { it.id })

        // 配置撤去のdeleteは履歴の正本を残します。
        store.delete(script.id)
        assertEquals(listOf(script.id), store.listHistory(editor).map { it.id })
        assertEquals("edited", requireNotNull(store.load(script.id)).name)

        // 再生成したStoreでも関係と正本を復元できます。
        val reopened = ScriptStore(dir, Logger.getAnonymousLogger())
        assertEquals(listOf(script.id), reopened.listHistory(otherEditor).map { it.id })
    }

    private fun validScriptJson(scriptId: UUID) = JsonParser.parseString(
        GsonBuilder().create().toJson(
            DiskScript(
                id = scriptId,
                name = "valid",
                graph = CommandGraph.empty().also { graph ->
                    val node = CommandType.DISPLAY_TEXT.newNode().apply {
                        targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
                    }
                    graph.entryNodeId = node.id
                    graph.nodes[node.id] = node
                },
            ),
        ),
    ).asJsonObject

    private fun recordingLogger(): Pair<Logger, RecordingLogHandler> {
        val logger = Logger.getLogger("ScriptStoreTest-${UUID.randomUUID()}")
        val handler = RecordingLogHandler()
        logger.useParentHandlers = false
        logger.level = Level.ALL
        logger.addHandler(handler)
        return logger to handler
    }

    @Test
    fun `concurrent tag additions keep both editors changes`() {
        // issue #34-2 の再現確認です。Aがタグ追加入力を開く→Bが別のタグを追加して
        // 保存する→Aが入力を確定する、の順序でBの追加分が失われないことを固定します。
        // GUIの保存入口は開始時revisionを必須とし、競合時は保存せず再読込を案内します。
        val store = ScriptStore(temp.resolve("concurrent-tags"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "concurrent-tags")
        val nodeId = requireNotNull(
            store.update(script.id) { current ->
                val node = GraphEditor.append(current.graph, CommandType.ENTITY_OPERATION)
                node.params[me.awabi2048.kantancommander.model.EntityDataPath.PARAM_PATH] =
                    me.awabi2048.kantancommander.model.EntityDataPath.TAG
                node.id
            },
        )

        fun tagsOf(id: UUID): List<me.awabi2048.kantancommander.model.EntityTagEntries.Entry> =
            me.awabi2048.kantancommander.model.EntityTagEntries.parse(
                requireNotNull(store.load(script.id)).graph.nodes.getValue(id)
                    .params.getValue(me.awabi2048.kantancommander.model.EntityTagEntries.PARAM_ENTRIES),
            ).orEmpty()

        fun addTag(tag: String, expectedRevision: Long?): Boolean =
            store.update(script.id, expectedRevision = expectedRevision) { current ->
                val node = current.graph.nodes.getValue(nodeId)
                val latest =
                    me.awabi2048.kantancommander.model.EntityTagEntries.parse(
                        node.params.getValue(me.awabi2048.kantancommander.model.EntityTagEntries.PARAM_ENTRIES),
                    ) ?: return@update null
                val updated =
                    latest + me.awabi2048.kantancommander.model.EntityTagEntries.Entry(
                        me.awabi2048.kantancommander.model.EntityTagEntries.MODE_ADD,
                        tag,
                    )
                if (!me.awabi2048.kantancommander.model.EntityTagEntries.isValid(updated)) return@update null
                node.params[me.awabi2048.kantancommander.model.EntityTagEntries.PARAM_ENTRIES] =
                    me.awabi2048.kantancommander.model.EntityTagEntries.encode(updated)
                true
            } == true

        // Aが入力画面を開いた時点の世代を保持します。
        val openRevision = requireNotNull(store.load(script.id)).revision
        // Bが別のタグを追加して保存します。
        assertTrue(addTag("tag-b", requireNotNull(store.load(script.id)).revision))
        assertTrue(tagsOf(nodeId).any { it.tag == "tag-b" })
        // Aが古い世代で確定しても、Bの追加分は失われません。
        assertFalse(addTag("tag-a", openRevision))
        assertTrue(tagsOf(nodeId).any { it.tag == "tag-b" })
        assertFalse(tagsOf(nodeId).any { it.tag == "tag-a" })
        // Aが最新を再読込して再確定すると、両方が残ります。
        assertTrue(addTag("tag-a", requireNotNull(store.load(script.id)).revision))
        assertTrue(tagsOf(nodeId).any { it.tag == "tag-a" })
        assertTrue(tagsOf(nodeId).any { it.tag == "tag-b" })
    }

    @Test
    fun `removed fixed entity kind is quarantined on load`() {
        // issue #35-3: 旧FIXED_ENTITYの保存データは読込時の型検証で隔離し、
        // 黙って別意味へ変換しません。サーバーは停止せず、理由を残します。
        val dir = temp.resolve("fixed-legacy").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        dir.resolve("$scriptId.json").writeText(
            """
            {
              "formatVersion": 17,
              "id": "$scriptId",
              "name": "fixed-legacy",
              "owner": "${UUID.randomUUID()}",
              "createdAt": 0,
              "activation": "NEEDS_REDSTONE",
              "timer": {"enabled": false, "intervalSeconds": 1.0},
              "graph": {
                "entryNodeId": "$nodeId",
                "nodes": {
                  "$nodeId": {
                    "id": "$nodeId",
                    "type": "ENTITY_DELETE",
                    "params": {},
                    "targetSpec": {"kind": "FIXED_ENTITY", "fixedEntityId": "${UUID.randomUUID()}"}
                  }
                }
              },
              "revision": 0
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )
        val store = ScriptStore(dir, Logger.getAnonymousLogger())

        assertNull(store.load(scriptId))
        assertFalse(dir.resolve("$scriptId.json").exists())
        assertTrue(dir.walkTopDown().filter(File::isFile).any { it.name.startsWith(scriptId.toString()) })
    }
}

private class RecordingLogHandler : Handler() {
    private val records = mutableListOf<LogRecord>()

    override fun publish(record: LogRecord) {
        synchronized(records) { records += record }
    }

    override fun flush() = Unit

    override fun close() = Unit

    fun warningCount(): Int = synchronized(records) {
        records.count { it.level.intValue() >= Level.WARNING.intValue() }
    }
}
