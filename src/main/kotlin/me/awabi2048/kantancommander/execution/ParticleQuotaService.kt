package me.awabi2048.kantancommander.execution

import java.util.ArrayDeque
import java.util.UUID

/**
 * ワールド単位のParticle送信量を、直近1秒のスライディングウィンドウで制限します。
 *
 * Particleはサーバーが寿命を追跡できるEntityではなく、クライアントへ送信した時点で
 * 表示処理が始まります。そのため「表示中の処理数」を数えるのではなく、1回の送信で
 * 指定された個数を予約し、直近1秒の送信合計が上限を超える場合は送信自体を拒否します。
 * 同じワールドの複数プログラム・複数実行者を同じ台帳で扱い、ワールド間は分離します。
 */
class ParticleQuotaService(
    private val limitProvider: () -> Int,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private data class Event(val timestampNanos: Long, val amount: Int)

    private class Window {
        val events = ArrayDeque<Event>()
        var used: Long = 0L
    }

    private val windows = mutableMapOf<UUID, Window>()

    /**
     * 直近1秒に送信された個数へamountを加算できる場合だけtrueを返します。
     * 判定と加算は同一ロック内で行い、同一メインスレッド外から呼ばれても上限を
     * すり抜けないようにします。設定値はリロード後の次回判定から反映します。
     */
    @Synchronized
    fun tryAcquire(worldId: UUID, amount: Int): Boolean =
        tryAcquire(worldId, amount, nowNanos())

    /** 時刻を差し替えられるテスト用の判定入口です。通常コードは引数なしを使います。 */
    @Synchronized
    internal fun tryAcquire(worldId: UUID, amount: Int, atNanos: Long): Boolean {
        val limit = limitProvider().coerceAtLeast(0)
        if (amount <= 0 || amount > limit) return false

        val window = windows.getOrPut(worldId, ::Window)
        val cutoff = atNanos - ONE_SECOND_NANOS
        while (window.events.peekFirst()?.timestampNanos?.let { it <= cutoff } == true) {
            val expired = window.events.removeFirst()
            window.used -= expired.amount.toLong()
        }

        if (window.used > limit.toLong() - amount.toLong()) return false
        window.events.addLast(Event(atNanos, amount))
        window.used += amount.toLong()
        return true
    }

    internal fun usage(worldId: UUID, atNanos: Long): Int = synchronized(this) {
        val window = windows[worldId] ?: return@synchronized 0
        val cutoff = atNanos - ONE_SECOND_NANOS
        while (window.events.peekFirst()?.timestampNanos?.let { it <= cutoff } == true) {
            val expired = window.events.removeFirst()
            window.used -= expired.amount.toLong()
        }
        window.used.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private companion object {
        const val ONE_SECOND_NANOS = 1_000_000_000L
    }
}
