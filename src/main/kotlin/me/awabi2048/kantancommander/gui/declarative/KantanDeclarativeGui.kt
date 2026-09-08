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
    var useTestConfirm: Boolean = true

    /** テスト実行中状態画面を宣言実装へ切り替えます。毎tick再生成されます。 */
    var useTestStatus: Boolean = true

    /** テスト結果画面を宣言実装へ切り替えます。 */
    var useTestResult: Boolean = true

    /**
     * グラフCustom経路のshadow検証を行います。
     * 表示は旧来のままにし、差分だけを記録します。上部画面の切替え時に除去します。
     */
    var shadowGraphCustom: Boolean = true

    /**
     * 上部画面を宣言実装へ切り替えます。
     * グラフ本体はCustom描画口、外枠は宣言転記です。
     */
    var useUpperNew: Boolean = true

    /** ワールド変数削除確認子画面を宣言実装へ切り替えます。 */
    var useWorldVariableDeleteConfirm: Boolean = false

    /** PICKER 画面を宣言実装へ切り替えます。 */
    var usePicker: Boolean = true

    /** 設定系画面を宣言実装へ切り替えます。切り分け実験が終わったため戻します。 */
    var useSettings: Boolean = true

    /** 設定候補子画面を宣言実装へ切り替えます。 */
    var useSettingChoices: Boolean = true

    /** ワールド変数一覧子画面を宣言実装へ切り替えます。 */
    var useWorldVariables: Boolean = true

    /** ワールド変数型選択子画面を宣言実装へ切り替えます。 */
    var useWorldVariableType: Boolean = true
}
