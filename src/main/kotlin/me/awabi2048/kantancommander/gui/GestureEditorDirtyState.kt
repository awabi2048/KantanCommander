package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandGraph

/**
 * グラフ差分と再描画範囲の分類です。
 *
 * issue #15 の差分更新基盤であり、保存・検証・描画の要否判定を一箇所へ集約します。
 * 値の比較は純粋関数に限定し、BukkitやCC-Systemへ依存しません。
 */
enum class GraphChangeKind {
    /** 完全に同一です。再計算・再描画は不要です。 */
    NONE,

    /** 明示設定フラグのみの差です。検証・レイアウトへ影響しません。 */
    METADATA_ONLY,

    /** 値・仕様の差ですが接続構造は同一です。レイアウト再利用が可能です。 */
    FIELD_VALUE,

    /** 接続構造の差です。全量の再レイアウトと再検証が必要です。 */
    STRUCTURE,
}

/**
 * 画面単位の再描画計画です。
 *
 * 上部はグラフ配置・未設定色・テスト表示、下部は設定値・候補・確認表示を担います。
 * どちらも再構築は画面全体の作り直しになるため、呼び出し側で不要な方を抑止します。
 */
data class RenderPlan(
    val updateUpper: Boolean,
    val updateLower: Boolean,
) {
    val isEmpty: Boolean get() = !updateUpper && !updateLower

    companion object {
        val NONE = RenderPlan(false, false)
        val FULL = RenderPlan(true, true)
        val LOWER_ONLY = RenderPlan(false, true)
        val UPPER_ONLY = RenderPlan(true, false)
    }
}

/**
 * グラフ構造の安定署名です。
 *
 * レイアウト結果のキャッシュキーに使い、値変更だけでは署名を変えません。
 * ノードID順へ正規化するため、LinkedHashMapの挿入順へ依存しません。
 */
object GraphStructureFingerprint {
    fun fingerprint(graph: CommandGraph): String {
        val entry = graph.entryNodeId?.toString().orEmpty()
        val body = graph.nodes.keys
            .sortedBy(java.util.UUID::toString)
            .joinToString("|") { id ->
                val node = graph.nodes.getValue(id)
                val folded = node.foldedGraph?.let { "<folded:${fingerprint(it)}>" } ?: "-"
                val snapshot = if (node.snapshot != null) "<snapshot:${fingerprint(node.snapshot!!)}>" else "-"
                // 接続形状だけを署名へ含めます。paramsや各種Specは値差分であり、
                // 同一配置のまま値を変えてもレイアウトを使い回せるようにします。
                "${node.id}:${node.type}:${node.next}:${node.trueNext}:${node.falseNext}:${node.pairedNodeId}:$folded:$snapshot"
            }
        return "entry=$entry#$body"
    }
}

/**
 * 保存前後のグラフ差を分類します。
 *
 * STRUCTURE判定はレイアウト・構造検証の再実行条件、FIELD_VALUE判定は
 * レイアウト再利用＋項目検証のみの条件、METADATA_ONLY判定は下部のみ更新の
 * 条件として使います。snapshot／foldedGraph内も再帰的に分類します。
 */
object GraphChangeClassifier {
    fun classify(old: CommandGraph, new: CommandGraph): GraphChangeKind {
        if (old.entryNodeId != new.entryNodeId) return GraphChangeKind.STRUCTURE
        if (old.nodes.keys != new.nodes.keys) return GraphChangeKind.STRUCTURE
        var sawFieldValue = false
        var sawMetadataOnly = false
        for (id in old.nodes.keys) {
            val before = old.nodes.getValue(id)
            val after = new.nodes.getValue(id)
            // 種別・接続・内包構造の有無は配置を変えるため構造差分とします。
            if (before.type != after.type) return GraphChangeKind.STRUCTURE
            if (before.next != after.next) return GraphChangeKind.STRUCTURE
            if (before.trueNext != after.trueNext) return GraphChangeKind.STRUCTURE
            if (before.falseNext != after.falseNext) return GraphChangeKind.STRUCTURE
            if (before.pairedNodeId != after.pairedNodeId) return GraphChangeKind.STRUCTURE
            if ((before.snapshot == null) != (after.snapshot == null)) return GraphChangeKind.STRUCTURE
            if ((before.foldedGraph == null) != (after.foldedGraph == null)) return GraphChangeKind.STRUCTURE
            if (before.snapshot != null && after.snapshot != null) {
                when (classify(before.snapshot!!, after.snapshot!!)) {
                    GraphChangeKind.STRUCTURE -> return GraphChangeKind.STRUCTURE
                    GraphChangeKind.FIELD_VALUE -> sawFieldValue = true
                    GraphChangeKind.METADATA_ONLY -> sawMetadataOnly = true
                    GraphChangeKind.NONE -> Unit
                }
            }
            if (before.foldedGraph != null && after.foldedGraph != null) {
                when (classify(before.foldedGraph!!, after.foldedGraph!!)) {
                    GraphChangeKind.STRUCTURE -> return GraphChangeKind.STRUCTURE
                    GraphChangeKind.FIELD_VALUE -> sawFieldValue = true
                    GraphChangeKind.METADATA_ONLY -> sawMetadataOnly = true
                    GraphChangeKind.NONE -> Unit
                }
            }
            // 値・仕様の差は配置を変えないため項目差分とします。
            if (!hasSameFieldValues(before, after)) {
                sawFieldValue = true
                continue
            }
            // 明示設定フラグだけの差は表示上の既定値扱いに影響するため下部更新に留めます。
            if (before.configuredFields != after.configuredFields) {
                sawMetadataOnly = true
            }
        }
        return when {
            sawFieldValue -> GraphChangeKind.FIELD_VALUE
            sawMetadataOnly -> GraphChangeKind.METADATA_ONLY
            else -> GraphChangeKind.NONE
        }
    }

    /**
     * 保存前後の差分に含まれるノードID集合を求めます。
     *
     * issue #15 の差分同期境界として、複数editorへの配布時に stale な
     * 一時previewの破棄対象を特定します。追加・削除・値・接続のいずれかが
     * 変わったノードを含み、明示設定フラグだけの差は含みません。
     * 内部グラフ（snapshot／folded）の差は外側ノードIDへ対応付けます。
     */
    fun changedNodeIds(old: CommandGraph, new: CommandGraph): Set<java.util.UUID> {
        if (old.entryNodeId != new.entryNodeId) return old.nodes.keys + new.nodes.keys
        val changed = mutableSetOf<java.util.UUID>()
        for (id in old.nodes.keys + new.nodes.keys) {
            val before = old.nodes[id]
            val after = new.nodes[id]
            if (before == null || after == null) {
                changed += id
                continue
            }
            if (before.type != after.type ||
                before.next != after.next ||
                before.trueNext != after.trueNext ||
                before.falseNext != after.falseNext ||
                before.pairedNodeId != after.pairedNodeId ||
                (before.snapshot == null) != (after.snapshot == null) ||
                (before.foldedGraph == null) != (after.foldedGraph == null)
            ) {
                changed += id
                continue
            }
            if (before.snapshot != null && after.snapshot != null &&
                classify(before.snapshot!!, after.snapshot!!) != GraphChangeKind.NONE
            ) {
                changed += id
                continue
            }
            if (before.foldedGraph != null && after.foldedGraph != null &&
                classify(before.foldedGraph!!, after.foldedGraph!!) != GraphChangeKind.NONE
            ) {
                changed += id
                continue
            }
            if (!hasSameFieldValues(before, after)) changed += id
        }
        return changed
    }

    /**
     * 配置へ影響しない値・仕様が同一かを判定します。
     *
     * 新しいSpec項目を追加した場合はここへ追加し、FIELD_VALUEへ分類されるようにします。
     * 追加し忘れるとNONEへ誤分類され、検証・表示更新が抑止されるため注意します。
     */
    private fun hasSameFieldValues(
        before: me.awabi2048.kantancommander.model.CommandNode,
        after: me.awabi2048.kantancommander.model.CommandNode,
    ): Boolean {
        if (before.params != after.params) return false
        if (before.targetSpec != after.targetSpec) return false
        if (before.secondaryTargetSpec != after.secondaryTargetSpec) return false
        if (before.destinationSpec != after.destinationSpec) return false
        if (before.destinationTargetSpec != after.destinationTargetSpec) return false
        if (before.destinationFacingSpec != after.destinationFacingSpec) return false
        if (before.conditionPositionSpec != after.conditionPositionSpec) return false
        if (before.controlBlockStates != after.controlBlockStates) return false
        if (before.blockPositionSpec != after.blockPositionSpec) return false
        if (before.blockFromSpec != after.blockFromSpec) return false
        if (before.blockToSpec != after.blockToSpec) return false
        if (before.soundPositionSpec != after.soundPositionSpec) return false
        if (before.particlePositionSpec != after.particlePositionSpec) return false
        if (before.particleEndPositionSpec != after.particleEndPositionSpec) return false
        if (before.fireworkPositionSpec != after.fireworkPositionSpec) return false
        if (before.summonPositionSpec != after.summonPositionSpec) return false
        // issue #26: コマンド単位の実行位置も値差分です。追加し忘れるとNONEへ
        // 誤分類され、検証・表示更新が抑止されるため注意します。
        if (before.executionPositionSpec != after.executionPositionSpec) return false
        if (before.temporaryEntityTargetSpec != after.temporaryEntityTargetSpec) return false
        if (before.temporaryLocationPositionSpec != after.temporaryLocationPositionSpec) return false
        if (before.temporaryLocationFacingSpec != after.temporaryLocationFacingSpec) return false
        if (before.itemTempRef != after.itemTempRef) return false
        if (before.blockTempRef != after.blockTempRef) return false
        if (before.soundTempRef != after.soundTempRef) return false
        if (before.effectTempRef != after.effectTempRef) return false
        return true
    }
}

/**
 * 差分種別と画面状態から再描画計画を求めます。
 *
 * field値変更でも未設定色のために上部内容は更新しますが、レイアウト自体は
 * 呼び出し側でキャッシュ再利用します。ここでは画面作り直しの要否だけを決め、
 * レイアウト計算の抑止は [GraphLayoutCache] 側で行います。
 */
object EditorRenderPlanner {
    fun planFor(
        change: GraphChangeKind,
        selectionChanged: Boolean = false,
        viewportChanged: Boolean = false,
        chromeChanged: Boolean = false,
    ): RenderPlan {
        val upperForState = selectionChanged || viewportChanged || chromeChanged
        return when (change) {
            GraphChangeKind.STRUCTURE -> RenderPlan.FULL
            GraphChangeKind.FIELD_VALUE -> RenderPlan.FULL
            GraphChangeKind.METADATA_ONLY -> if (upperForState) RenderPlan.FULL else RenderPlan.LOWER_ONLY
            GraphChangeKind.NONE -> RenderPlan(upperForState, chromeChanged)
        }
    }

    /**
     * 保存前の配置検証が必要かを判定します。
     *
     * 配置は構造署名だけから決まるため、署名が変わらない値変更では
     * 検証の意味がありません。構造変更時だけ全量レイアウトします。
     */
    fun needsPreSaveLayout(beforeSignature: String, afterSignature: String): Boolean =
        beforeSignature != afterSignature
}
