package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiElement
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.gesturegui.html.GestureGuiHtmlEnvironment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBox
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutFacade
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiColumn
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustom
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiGrid
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutDiagnostic
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiOverlay
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiRow
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiViewport

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
     * @param extraActionIds 文書外で生成する操作の action ID です。
     * Custom renderer の動的要素など、文書走査で集まらない ID を渡します。
     * @param customRenderers rendererId から Custom renderer への対応表です。
     */
    fun compile(
        document: GestureGuiDocument,
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        extraActionIds: Set<String> = emptySet(),
        customRenderers: Map<String, com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustomRenderer> = emptyMap(),
        source: String? = null,
    ): Compiled {
        val actions = collectActions(document.root) + extraActionIds
        val resolved = GestureGuiLayoutFacade.layout(document, knownActionIds = actions, source = source)
        val handlers = actions.associateWith { onAction }
        val compiled = GestureGuiLayoutFacade.compile(
            resolved,
            screenId = "kantan-bridge",
            actionHandlers = handlers,
            customRenderers = customRenderers,
            source = source,
        )
        // compiler の診断には解決時の診断も含まれるため、そのまま用います。
        val diagnostics = compiled.diagnostics
        if (diagnostics.isNotEmpty()) onDiagnostics(diagnostics)
        return Compiled(
            elements = compiled.view.definition.elements,
            visuals = compiled.view.visuals,
            diagnostics = diagnostics,
        )
    }

    /**
     * HTMLを解析から診断付きコンパイルまで一続きで処理します。
     *
     * HTML画面の発生源名をLayout Engineまで渡すことで、固定配置の予約領域診断を
     * 有効化します。解析・レイアウト・コンパイルの診断をまとめて返し、呼び出し側が
     * 警告ログや検証結果を一つの経路で扱えるようにします。
     */
    fun compileHtml(
        html: String,
        environment: GestureGuiHtmlEnvironment,
        onAction: (GestureGuiActionContext) -> Unit,
        documentName: String = "kantan.html",
        panel: GestureGuiPanel = GestureGuiPanel(),
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        extraActionIds: Set<String> = emptySet(),
        customRenderers: Map<String, com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustomRenderer> = emptyMap(),
    ): Compiled {
        val parsed = GestureGuiLayoutFacade.parseHtml(html, environment, documentName, panel)
        val actions = collectActions(parsed.document.root) + extraActionIds
        val resolved = GestureGuiLayoutFacade.layout(
            parsed.document,
            knownActionIds = actions,
            source = documentName,
        )
        val compiled = GestureGuiLayoutFacade.compile(
            resolved,
            screenId = "kantan-html-bridge",
            actionHandlers = actions.associateWith { onAction },
            customRenderers = customRenderers,
            source = documentName,
        )
        val diagnostics = (parsed.diagnostics + compiled.diagnostics).distinct()
        if (diagnostics.isNotEmpty()) onDiagnostics(diagnostics)
        return Compiled(
            elements = compiled.view.definition.elements,
            visuals = compiled.view.visuals,
            diagnostics = diagnostics,
        )
    }

    /**
     * JAR 同梱の `.html` リソースを解析から診断付きコンパイルまで一続きで処理します。
     *
     * マークアップは `gesture-gui` 配下の `.html` リソースに保持し、Kotlin 側での文字列生成はしません。
     * cc-system 同梱の基底スタイルシート（語彙 class）を既定で適用します。
     * それ以外の振る舞いは [compileHtml] と同一です。
     *
     * 件数や内容が実行時に決まる一覧（カード・タブ・ページャ等）は、マークアップ側が
     * 配置済みの空容器（ID 付き `div` 等）だけを持ち、[injections] で要素 ID を指定して
     * ノードを注入します。注入ノードの要素 ID は呼び出し側が意味値を付けられるため、
     * `lower-type:<key>` のようなデータ入り ID をそのまま維持できます。
     *
     * @param resourcePath クラスパス上のリソース位置です（例 `"gesture-gui/confirm.html"`）。
     * @param injections 注入先の容器 ID から、追加する子ノード一覧への対応表です。
     *   容器の既存子ノードは残りません。
     */
    fun compileHtmlResource(
        resourcePath: String,
        environment: GestureGuiHtmlEnvironment,
        onAction: (GestureGuiActionContext) -> Unit,
        documentName: String = resourcePath.substringAfterLast('/'),
        panel: GestureGuiPanel = GestureGuiPanel(),
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        extraActionIds: Set<String> = emptySet(),
        customRenderers: Map<String, com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustomRenderer> = emptyMap(),
        injections: Map<String, List<GestureGuiNode>> = emptyMap(),
    ): Compiled {
        val parsed = GestureGuiLayoutFacade.parseHtmlResource(
            resourcePath,
            KantanLayoutBridge::class.java.classLoader,
            environment,
            documentName,
            panel,
        )
        val document = injections.entries.fold(parsed.document) { doc, (containerId, children) ->
            withChildrenInjected(doc, containerId, children)
        }
        val actions = collectActions(document.root) + extraActionIds
        val resolved = GestureGuiLayoutFacade.layout(
            document,
            knownActionIds = actions,
            source = documentName,
        )
        val compiled = GestureGuiLayoutFacade.compile(
            resolved,
            screenId = "kantan-html-bridge",
            actionHandlers = actions.associateWith { onAction },
            customRenderers = customRenderers,
            source = documentName,
        )
        val diagnostics = (parsed.diagnostics + compiled.diagnostics).distinct()
        if (diagnostics.isNotEmpty()) onDiagnostics(diagnostics)
        return Compiled(
            elements = compiled.view.definition.elements,
            visuals = compiled.view.visuals,
            diagnostics = diagnostics,
        )
    }

    /**
     * 文書内の指定 ID を持つ容器へ子ノードを注入した文書を返します。
     *
     * マークアップの静的構造と実行時決定の一覧内容を分離するための移植口です。
     * 注入先が見つからない場合は誤った ID を見逃さないようエラーとします。
     */
    fun withChildrenInjected(
        document: GestureGuiDocument,
        containerId: String,
        children: List<GestureGuiNode>,
    ): GestureGuiDocument {
        var injected = false

        fun withChildren(node: GestureGuiNode, newChildren: List<GestureGuiNode>): GestureGuiNode = when (node) {
            is GestureGuiBox -> node.copy(children = newChildren)
            is GestureGuiColumn -> node.copy(children = newChildren)
            is GestureGuiRow -> node.copy(children = newChildren)
            is GestureGuiGrid -> node.copy(children = newChildren)
            is GestureGuiOverlay -> node.copy(children = newChildren)
            else -> error("ID $containerId は子要素を持てないノードです: ${node::class.simpleName}")
        }

        fun visit(node: GestureGuiNode): GestureGuiNode {
            if (node.id == containerId) {
                injected = true
                return withChildren(node, children)
            }
            return when (node) {
                is GestureGuiBox -> node.copy(children = node.children.map(::visit))
                is GestureGuiColumn -> node.copy(children = node.children.map(::visit))
                is GestureGuiRow -> node.copy(children = node.children.map(::visit))
                is GestureGuiGrid -> node.copy(children = node.children.map(::visit))
                is GestureGuiOverlay -> node.copy(children = node.children.map(::visit))
                is GestureGuiViewport -> node.copy(content = visit(node.content))
                else -> node
            }
        }

        val newRoot = visit(document.root)
        require(injected) { "注入先の容器 ID が文書内に見つかりません: $containerId" }
        return document.copy(root = newRoot)
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
