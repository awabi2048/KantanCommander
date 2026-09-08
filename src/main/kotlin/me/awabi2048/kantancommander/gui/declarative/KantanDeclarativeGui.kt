package me.awabi2048.kantancommander.gui.declarative

/**
 * 宣言的レイアウトへの画面単位の切替フラグです。
 *
 * 旧実装は破棄せず、各画面の分岐点でこのフラグにより新旧を選択します。
 * 検証済みの画面から true へ倒し、不具合時は false へ戻すだけで旧実装へ復帰できます。
 * 上部画面（グラフ描画部を含む）は対象外とし、legacy 実装で維持します。
 */
object KantanDeclarativeGui {
    /** 確認系子画面（削除・上書き確認）を宣言実装へ切り替えます。 */
    var useConfirm: Boolean = true

    /** テスト開始確認子画面を宣言実装へ切り替えます。 */
    var useTestConfirm: Boolean = false

    /** ワールド変数削除確認子画面を宣言実装へ切り替えます。 */
    var useWorldVariableDeleteConfirm: Boolean = false

    /** PICKER 画面を宣言実装へ切り替えます。 */
    var usePicker: Boolean = true

    /** 設定系画面を宣言実装へ切り替えます。切り分け実験のため旧実装に戻します。 */
    var useSettings: Boolean = false
}
