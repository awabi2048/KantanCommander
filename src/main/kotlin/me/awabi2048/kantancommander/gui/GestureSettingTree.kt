package me.awabi2048.kantancommander.gui

import org.bukkit.Material

/**
 * ジェスチャー設定を意味上の木として表す選択肢です。
 *
 * 画面IDや親画面IDを持たせず、選択肢が子要素を持つかどうかだけを
 * 「詳細設定が存在する」という事実として表現します。これにより、
 * 対象・位置・条件・コンテキストを同じ描画／クリック規則へ載せられます。
 */
data class GestureSettingTreeNode(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    val description: String = "",
    val children: List<GestureSettingTreeNode> = emptyList(),
    /** 選択肢の意味。択一と複数選択可能な設定を同じ木で表現します。 */
    val selectionMode: GestureSettingSelectionMode = GestureSettingSelectionMode.EXCLUSIVE,
    /** 現在値が初期値のままか、明示設定済みかを表します。 */
    val valueState: GestureSettingValueState = GestureSettingValueState.INITIAL,
) {
    val hasChildren: Boolean
        get() = children.isNotEmpty()

    fun find(nodeId: String): GestureSettingTreeNode? {
        if (id == nodeId) return this
        return children.firstNotNullOfOrNull { it.find(nodeId) }
    }
}

/** 設定ドメインが許す選択方式。表示色だけでなく入力契約の基準にもします。 */
enum class GestureSettingSelectionMode {
    EXCLUSIVE,
    MULTIPLE,
}

/** 設定値の状態。初期値と明示設定済みを色・文言へ同時に投影します。 */
enum class GestureSettingValueState {
    INITIAL,
    CONFIGURED,
}

/**
 * 設定カードの表示規則を一箇所へ集約します。
 *
 * concrete / terracotta の明暗だけに意味を詰め込まず、選択方式は色相、値状態は
 * 明暗、現在選択中はコンクリートという三つの軸へ分けます。設定タブと詳細選択肢が
 * 同じ規則を使うため、画面ごとの色分岐が増えません。
 */
internal object GestureSettingVisualPolicy {
    fun material(
        selectionMode: GestureSettingSelectionMode,
        valueState: GestureSettingValueState,
        selected: Boolean,
    ): Material = when (selectionMode) {
        GestureSettingSelectionMode.EXCLUSIVE -> when (valueState) {
            GestureSettingValueState.CONFIGURED -> if (selected) Material.CYAN_CONCRETE else Material.CYAN_TERRACOTTA
            GestureSettingValueState.INITIAL -> if (selected) Material.LIGHT_BLUE_CONCRETE else Material.LIGHT_BLUE_TERRACOTTA
        }
        GestureSettingSelectionMode.MULTIPLE -> when (valueState) {
            GestureSettingValueState.CONFIGURED -> if (selected) Material.PURPLE_CONCRETE else Material.PURPLE_TERRACOTTA
            GestureSettingValueState.INITIAL -> if (selected) Material.MAGENTA_CONCRETE else Material.MAGENTA_TERRACOTTA
        }
    }
}

/** 木構造上の現在位置。表示状態と戻る経路を同じ値から復元します。 */
data class GestureSettingTreePath(
    val fieldKey: String,
    val role: CommandSettingRole?,
    val nodeIds: List<String> = emptyList(),
) {
    /** 現在のフレームの選択だけを置き換え、別枝の履歴を混ぜません。 */
    fun selectAtDepth(depth: Int, nodeId: String): GestureSettingTreePath {
        val safeDepth = depth.coerceAtLeast(0)
        val ids = nodeIds.take(safeDepth).toMutableList()
        ids += nodeId
        return copy(nodeIds = ids)
    }

    /** 子フレームへ進む経路を追加します。既に選択済みなら重複させません。 */
    fun enterChild(nodeId: String): GestureSettingTreePath =
        if (nodeIds.lastOrNull() == nodeId) this else copy(nodeIds = nodeIds + nodeId)

    /** 親フレームへ戻るため、末尾の選択を一つ取り除きます。 */
    fun leaveChild(): GestureSettingTreePath = copy(nodeIds = nodeIds.dropLast(1))
}

/**
 * 設定木の直下項目を押した後の共通遷移です。
 *
 * 一回目のクリックは選択状態の更新だけを行い、子を持つ同じ項目を再クリック
 * した場合だけ子フレームへ進みます。葉を選んだ後も現在フレームに留めるため、
 * 兄弟項目の選択可能性を画面種類ごとに変えません。
 */
internal enum class GestureSettingSelectionAction {
    STAY_ON_FRAME,
    ENTER_CHILD,
}

internal fun settingSelectionAction(
    wasSelected: Boolean,
    hasChildren: Boolean,
): GestureSettingSelectionAction =
    if (wasSelected && hasChildren) {
        GestureSettingSelectionAction.ENTER_CHILD
    } else {
        GestureSettingSelectionAction.STAY_ON_FRAME
    }

/** 詳細子画面をまたぐための設定木上の1フレームです。 */
data class GestureSettingFrame(
    val context: CommandSettingContext,
    val fieldKey: String,
    val screen: GestureSettingScreen,
)

/** 構造化設定エディターとジェスチャー画面の意味画面を結ぶ共通変換です。 */
internal fun gestureSettingScreenFor(editor: CommandSettingEditor): GestureSettingScreen? = when (editor) {
    CommandSettingEditor.TARGET -> GestureSettingScreen.TARGET
    CommandSettingEditor.POSITION -> GestureSettingScreen.POSITION
    CommandSettingEditor.FACING -> GestureSettingScreen.FACING
    CommandSettingEditor.CONDITION_KIND -> GestureSettingScreen.CONDITION_KIND
    CommandSettingEditor.CONDITION_DETAIL -> GestureSettingScreen.CONDITION_DETAIL
    CommandSettingEditor.DISPLAY_MODE -> GestureSettingScreen.DISPLAY_MODE
    CommandSettingEditor.ENTITY_ACTION -> GestureSettingScreen.ENTITY_ACTION
    CommandSettingEditor.VARIABLE_SCOPE -> GestureSettingScreen.VARIABLE_SCOPE
    CommandSettingEditor.VARIABLE_TYPE -> GestureSettingScreen.VARIABLE_TYPE
    CommandSettingEditor.VARIABLE_OPERATION -> GestureSettingScreen.VARIABLE_OPERATION
    CommandSettingEditor.VARIABLE_VALUE -> GestureSettingScreen.VARIABLE_VALUE
    CommandSettingEditor.FOR_SOURCE -> GestureSettingScreen.FOR_SOURCE
    CommandSettingEditor.INCLUSIVE_END -> GestureSettingScreen.INCLUSIVE_END
    CommandSettingEditor.CONTEXT -> GestureSettingScreen.CONTEXT_OVERRIDE
    CommandSettingEditor.TEXT -> null
}
