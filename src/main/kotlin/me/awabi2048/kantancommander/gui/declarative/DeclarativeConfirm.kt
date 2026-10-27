package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutDiagnostic
import net.kyori.adventure.text.Component

/** 確認系子画面の文言です。解決済みの表示文を呼び出し側が渡します。 */
data class ConfirmTexts(
    val title: Component,
    val warn: Component,
    val yes: Component,
    val no: Component,
)

/**
 * 確認系子画面（削除・上書き確認）です。
 *
 * 構造・寸法・素材語彙は JAR 同梱の `gesture-gui/confirm.html` が保持し、
 * ここでは文言の要素ID対応とコンパイル入口だけを持ちます。
 * 要素 ID（confirm-delete / confirm-cancel）は旧実装から継承するため、
 * 共有 onAction への振分けは旧実装と同一です。
 */
object DeclarativeConfirm {
    /** 確定ボタンの要素・action ID です。危険操作側です（背景は markup の `danger` 語彙）。 */
    const val YES_ID = "confirm-delete"

    /** 取消ボタンの要素・action ID です。 */
    const val NO_ID = "confirm-cancel"

    private const val RESOURCE = "gesture-gui/confirm.html"

    /** 文言を要素IDへ対応付けて、画面をコンパイルします。 */
    fun compile(
        texts: ConfirmTexts,
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
    ): KantanLayoutBridge.Compiled = KantanLayoutBridge.compileHtmlResource(
        resourcePath = RESOURCE,
        environment = KantanGuiTheme.environment(
            components = mapOf(
                "confirm-title" to texts.title,
                "confirm-warn" to texts.warn,
                YES_ID to texts.yes,
                NO_ID to texts.no,
            ),
        ),
        onAction = onAction,
        onDiagnostics = onDiagnostics,
    )
}
