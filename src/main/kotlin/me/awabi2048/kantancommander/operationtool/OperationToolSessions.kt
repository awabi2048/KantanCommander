package me.awabi2048.kantancommander.operationtool

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * issue #14の操作ツールwandのセッション管理です。
 *
 * プレイヤー単位に [OperationToolSession] を保持します。サーバー再起動時は
 * プラグイン無効化で破棄され、選択状態は解除されます。ログアウト・ワールド移動
 * だけでは破棄しません。
 */
class OperationToolSessions(
    /** Entity最大選択数です。設定値として扱い、セッション作成時に適用します。 */
    private val maxEntities: Int = OperationToolSession.DEFAULT_MAX_ENTITIES,
    /** Box選択の最大一辺です。設定値として扱い、セッション作成時に適用します。 */
    private val maxBoxEdge: Double = OperationToolSession.DEFAULT_MAX_BOX_EDGE,
) {
    private val sessions = ConcurrentHashMap<UUID, OperationToolSession>()

    /** プレイヤーのセッションを取得または作成します。 */
    fun getOrCreate(playerId: UUID): OperationToolSession =
        sessions.computeIfAbsent(playerId) { OperationToolSession(maxEntities, maxBoxEdge) }

    /** プレイヤーのセッションを取得します。存在しない場合はnullです。 */
    fun get(playerId: UUID): OperationToolSession? = sessions[playerId]

    /** プレイヤーのセッションを破棄します。 */
    fun remove(playerId: UUID) {
        sessions.remove(playerId)
    }

    /** 全セッションを破棄します。プラグイン無効化時に呼び出します。 */
    fun clear() {
        sessions.clear()
    }
}
