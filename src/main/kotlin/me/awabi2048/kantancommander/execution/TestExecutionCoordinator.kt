package me.awabi2048.kantancommander.execution

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.data.ScriptMutationLockedException
import me.awabi2048.kantancommander.data.ScriptMutationOperation
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 拡張コマンドブロック単位の実行排他を管理します。
 *
 * 同一スクリプトを参照する別のコピーまで止めると、ディスク参照・インストールが
 * 同期されてしまいます。そのため排他キーはスクリプトIDではなく、実際に実行を
 * 発生させる拡張コマンドブロックの配置キーです。通常実行同士は並行許可し、通常と
 * テスト、テスト同士だけを同じ配置で拒否します。
 */
class TestExecutionCoordinator(
    private val executor: SequenceExecutor,
) {
    private data class ActiveTest(
        val scopeKey: String,
        val scriptId: UUID,
        val ownerId: UUID,
        val worldId: UUID,
        var handle: ExecutionHandle? = null,
        var showResult: Boolean = true,
    )

    private val activeTests = linkedMapOf<String, ActiveTest>()
    private val normalExecutions = mutableMapOf<String, Int>()

    /** 通常実行を開始できる場合だけ、終了時に解放するトークンを返します。 */
    @Synchronized
    fun beginNormal(scopeKey: String): AutoCloseable? {
        val active = activeTests[scopeKey]
        if (active != null) {
            onlinePlayer(active.ownerId)?.sendMessage(busyMessage(onlinePlayer(active.ownerId)))
            return null
        }
        normalExecutions[scopeKey] = (normalExecutions[scopeKey] ?: 0) + 1
        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (!closed.compareAndSet(false, true)) return@AutoCloseable
            synchronized(this) {
                val count = (normalExecutions[scopeKey] ?: 1) - 1
                if (count <= 0) normalExecutions.remove(scopeKey) else normalExecutions[scopeKey] = count
            }
        }
    }

    /**
     * テストを開始します。グラフは呼び出し元で確定したスナップショットを受け取り、
     * 実行中の保存・削除とは切り離します。
     */
    @Synchronized
    fun startTest(
        scopeKey: String,
        script: DiskScript,
        ownerId: UUID,
        origin: org.bukkit.Location,
        debugMode: Boolean,
        observer: ExecutionObserver,
        onFinished: (ExecutionResult) -> Unit,
    ): Boolean {
        val active = activeTests[scopeKey]
        if (active != null) {
            onlinePlayer(ownerId)?.sendMessage(busyMessage(onlinePlayer(ownerId)))
            if (active.ownerId != ownerId) {
                onlinePlayer(active.ownerId)?.sendMessage(busyMessage(onlinePlayer(active.ownerId)))
            }
            return false
        }
        if ((normalExecutions[scopeKey] ?: 0) > 0) {
            onlinePlayer(ownerId)?.sendMessage(normalBusyMessage(onlinePlayer(ownerId)))
            return false
        }
        val registered = ActiveTest(scopeKey, script.id, ownerId, origin.world.uid)
        activeTests[scopeKey] = registered
        val options = ExecutionOptions(
            observer = observer,
            debugDelayTicks = if (debugMode) DEBUG_DELAY_TICKS else 0L,
            // テスト失敗を通常実行の作成者通知へ二重送信しません。結果画面と、
            // 指定されたチャットログだけがテスト結果の通知先です。
            suppressCreatorFailureNotification = true,
        )
        val handle = executor.executeTest(script, origin, options) { result ->
            val shouldNotify = synchronized(this) {
                val current = activeTests.remove(scopeKey)
                current?.showResult == true
            }
            if (shouldNotify && onlinePlayer(ownerId) != null) onFinished(result)
        }
        registered.handle = handle
        // コマンド数0の事前検証失敗は同期完了するため、callback内で既に登録が
        // 解除されています。戻り値は「テスト処理を受理したか」を表します。
        return true
    }

    @Synchronized
    fun cancel(scopeKey: String, showResult: Boolean, reason: String = "manual_stop"): Boolean {
        val active = activeTests[scopeKey] ?: return false
        active.showResult = showResult
        active.handle?.cancel(reason)
        return true
    }

    /** ブロック撤去・ワールド終了・プラグイン停止時に、画面を経由せず安全終了します。 */
    @Synchronized
    fun abortAll() {
        // cancel()は同期的に完了コールバックを呼び、activeTestsから自身を削除します。
        // Mapのvaluesを直接反復するとConcurrentModificationExceptionになるため、
        // 先にスナップショット化します。
        activeTests.values.toList().forEach {
            it.showResult = false
            it.handle?.cancel("external_shutdown")
        }
    }

    /** ワールド単位の終了では、別ワールドの実行まで巻き込まないようにします。 */
    @Synchronized
    fun abortForWorld(worldId: UUID) {
        activeTests.values
            .filter { it.worldId == worldId }
            .toList()
            .forEach {
                it.showResult = false
                it.handle?.cancel("world_unload")
            }
    }

    fun shutdown() = abortAll()

    @Synchronized
    fun isActive(scopeKey: String): Boolean = scopeKey in activeTests

    @Synchronized
    fun isScriptLocked(scriptId: UUID): Boolean = activeTests.values.any { it.scriptId == scriptId }

    /** ScriptStoreの通常保存境界から呼び出し、別セッションの編集を遮断します。 */
    @Synchronized
    fun assertMutationAllowed(
        scriptId: UUID,
        operation: ScriptMutationOperation,
    ) {
        if (activeTests.values.any { it.scriptId == scriptId }) {
            throw ScriptMutationLockedException(scriptId, operation)
        }
    }

    /** 管理コマンドなどの明示的なロックバイパス後に、テスト操作者へ通知します。 */
    @Synchronized
    fun notifyBypassedMutation(scriptId: UUID, operation: ScriptMutationOperation) {
        val action = if (operation == ScriptMutationOperation.DELETE) "削除" else "変更"
        activeTests.values
            .filter { it.scriptId == scriptId }
            .mapNotNull { onlinePlayer(it.ownerId) }
            .forEach { player ->
                player.sendMessage(
                    KcI18n.text(
                        player,
                        KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_TEST_ADMIN_MUTATION,
                        mapOf("action" to action),
                    ),
                )
            }
    }

    private fun onlinePlayer(id: UUID): Player? = Bukkit.getPlayer(id)?.takeIf(Player::isOnline)

    private fun busyMessage(player: Player?): String =
        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_TEST_BUSY)

    private fun normalBusyMessage(player: Player?): String =
        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_TEST_NORMAL_BUSY)

    companion object {
        const val DEBUG_DELAY_TICKS: Long = 20L
    }
}
