package me.awabi2048.kantancommander.data

/**
 * issue #16 の保存 lifecycle 方針の単一正本です。
 *
 * 計測・index・履歴・orphan・cache・DB判断の結論をここに集約し、
 * 各 store はこの方針に従います。
 *
 * ## 計測結論
 * 300 program 規模の再現負荷試験（[ScriptStoreLoadTest]）では、一覧・読込・
 * 保存のいずれも十分に高速であり、metadata index は不要と判断しました。
 * 一覧は cache 命中時は JSON 解析を行わず、関係解決は対象 ID のみを読みます。
 * program 数が数千を超える運用が見えた場合は再計測し、そのときに index を
 * 検討します。
 *
 * ## 履歴・関係・orphan
 * - 履歴は Player ごとに [MAX_HISTORY_PER_PLAYER] 件まで保持し、超過分は
 *   古い方から外します。外れた記録の正本は、他関係・配置の参照がなければ
 *   その場で解放します（orphan の蓄積を防ぎます）。
 * - 関係（library／history）に正本ファイルのない ID が残っていた場合は、
 *   起動時の読込で除外して自己修復します。
 * - どの関係・配置からも参照されない正本ファイルは orphan とし、
 *   [ScriptStore.pruneOrphans]・asset 側の未参照除去で明示的に回収します。
 *   自動削除は行いません。実行中 script・編集中 script を GC しないためです。
 *
 * ## cache
 * - 正本 cache は LRU で [MAX_CACHED_PROGRAMS] 件まで保持します。追い出しは
 *   メモリ複写を捨てるだけであり、disk が正本のため revision 意味は壊れません。
 * - 隔離失敗ファイルの負の cache は更新検知（更新時刻・サイズ）で再読込します。
 *
 * ## DB判断
 * SQLite 等への移行は行いません。計測上、directory 走査・関係更新・一覧取得の
 * いずれも支配的でなく、移行の必要性は確認されませんでした。program の
 * structured format の export 互換性は維持します。
 *
 * ## 新 asset の統合
 * Structure（`.nbt`）・Entity Group（JSON 単一正本）はいずれも参照キー
 * （建造物名・群名）で script から参照されます。未参照 asset の検出は
 * [AssetReferenceScanner] で script 群を走査し、各 store の除去関数で回収します。
 * BlockEntity の独自変換・物理挙動などの asset 固有方針は各 store に残します。
 */
object StorageLifecycle {
    /** Player ごとの履歴保持上限です。上限超過は古い方から外します。 */
    const val MAX_HISTORY_PER_PLAYER = 100

    /** 正本 cache の LRU 上限です。運用規模（数百 program）を十分に超える値です。 */
    const val MAX_CACHED_PROGRAMS = 512
}
