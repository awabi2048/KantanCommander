package me.awabi2048.kantancommander.gui.setting

import me.awabi2048.kantancommander.gui.GestureSettingSelectionAction
import me.awabi2048.kantancommander.gui.settingSelectionAction

/**
 * 「選択→必要ならもう一段設定へ」の汎用遷移エンジンです。
 *
 * 対象・位置・向き・条件など個別画面がそれぞれ `wasSelected/hasChildren` 判定と
 * `remember/push/show` の順序を複写していた構造的課題を解消し、すべての二段階選択を
 * 同一規則へ載せます。個別画面は遷移先フレームの生成だけに専念し、遷移判定を持ちません。
 *
 * - 1回目のクリックは選択状態の更新だけを行い、現在フレームに留めます。
 * - 子を持つ同じ項目の再クリックだけが子フレームへ進みます。
 * - 葉を選んだ後も現在フレームに留め、兄弟項目の選択可能性を変えません。
 */
object SettingSelectionEngine {
    /** 二段階遷移の判定結果です。 */
    sealed interface Next {
        /** 現在フレームに留まり再描画します。 */
        data object Stay : Next
        /** 子フレームへ進みます。 */
        data object EnterChild : Next
    }

    /**
     * 汎用二段階判定を返します。
     *
     * @param wasSelected 同一項目が既に選択済みか（永続値・経路の両方を投影済みであること）。
     * @param hasChildren 選択肢が詳細子を持つか。
     */
    fun decide(wasSelected: Boolean, hasChildren: Boolean): Next =
        when (settingSelectionAction(wasSelected, hasChildren)) {
            GestureSettingSelectionAction.ENTER_CHILD -> Next.EnterChild
            GestureSettingSelectionAction.STAY_ON_FRAME -> Next.Stay
        }

    /**
     * 子へ進むべきかを返します。
     *
     * 呼び出し側の `pushSettingFrame/showSettingScreen` 分岐をこの1行に置換するための
     * 糖衣であり、判定規則自体は [decide] と同一です。
     */
    fun shouldEnterChild(wasSelected: Boolean, hasChildren: Boolean): Boolean =
        decide(wasSelected, hasChildren) is Next.EnterChild
}
