package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiElement
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBox
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiColumn
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustom
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiGrid
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutDiagnostic
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiOverlay
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiRow
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiViewport
import com.awabi2048.ccsystem.core.gesturegui.layout.GestureGuiLayoutCompiler
import com.awabi2048.ccsystem.core.gesturegui.layout.GestureGuiLayoutEngine

/**
 * 宣言文書から低レベル表示・操作面への共通橋渡しです。
 *
 * Kantan 側の操作振分けは要素 ID 基準のため、宣言ノード ID に旧来の要素 ID を
 * そのまま用い、共有の onAction へ接続します。処理 logic の重複は作りません。
 * 解決・変換の診断は呼び出し側へ返し、表示崩れの混入を機械的に検出できます。
 */
object KantanLayoutBridge {
    /** 橋渡しの出力である要素・表示・診断の一覧です。 */
    data class Compiled(
        val elements: List<GestureGuiElement>,
        val visuals: List<GestureGuiVisual>,
        val diagnostics: List<GestureGuiLayoutDiagnostic>,
    )

    /**
     * 文書を解決・変換します。
     *
     * @param onAction 共有の操作処理です。全 action ID に同じ処理を接続します。
     * @param onDiagnostics 診断の通知先です。空でない場合は表示崩れの疑いがあります。
     */
    fun compile(
        document: GestureGuiDocument,
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
    ): Compiled {
        val actions = collectActions(document.root)
        val resolved = GestureGuiLayoutEngine.layout(document, knownActionIds = actions)
        val handlers = actions.associateWith { onAction }
        val compiled = GestureGuiLayoutCompiler.compile(
            resolved,
            screenId = "kantan-bridge",
            actionHandlers = handlers,
        )
        val diagnostics = resolved.diagnostics + compiled.diagnostics
        if (diagnostics.isNotEmpty()) onDiagnostics(diagnostics)
        return Compiled(
            elements = compiled.view.definition.elements,
            visuals = compiled.view.visuals,
            diagnostics = diagnostics,
        )
    }

    /** 文書内の action ID を重複なく集めます。 */
    fun collectActions(root: GestureGuiNode): Set<String> {
        val actions = linkedSetOf<String>()
        fun visit(node: GestureGuiNode) {
            node.actionId?.let(actions::add)
            childrenOf(node).forEach(::visit)
        }
        visit(root)
        return actions
    }

    private fun childrenOf(node: GestureGuiNode): List<GestureGuiNode> = when (node) {
        is GestureGuiBox -> node.children
        is GestureGuiRow -> node.children
        is GestureGuiColumn -> node.children
        is GestureGuiGrid -> node.children
        is GestureGuiOverlay -> node.children
        is GestureGuiViewport -> listOf(node.content)
        else -> emptyList()
    }
}
