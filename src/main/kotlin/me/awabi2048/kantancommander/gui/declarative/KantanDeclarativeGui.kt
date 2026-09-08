package me.awabi2048.kantancommander.gui.declarative

/**
 * 上部画面の宣言移行が残す切替フラグです。
 *
 * 下部画面の旧実装は除去済みであり、フラグによる切替えは上部画面だけに残ります。
 * 上部旧経路と shadow 検証の除去が終われば、このオブジェクト自体を除去します。
 */
object KantanDeclarativeGui {
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
}
