package me.awabi2048.kantancommander.gui

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
) {
    val hasChildren: Boolean
        get() = children.isNotEmpty()

    fun find(nodeId: String): GestureSettingTreeNode? {
        if (id == nodeId) return this
        return children.firstNotNullOfOrNull { it.find(nodeId) }
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
