# 2026年10月27日 実装監査の反映

## DISK_CALL

呼出し設定の引数契約を現在の正本と比較します。順序の変更だけを許可し、引数の追加・削除・改名・型変更は事前検証および実行直前に拒否します。待機中の編集による契約変更も呼出し枠や変数表を変更する前に止めます。

## 構造検証

先行検証で循環を検出したグラフをFOR所属検証へ渡しません。所属検証にも同じFORへの再入判定とノード数に応じた探索予算を持たせ、破損JSONの起動時読込を終了させます。

## 画面の参照元比較

参照元は既存のparent-screen、child-screen、setting-choices-child、script-settings、test-screenです。素材、寸法、背景、表示位置、情報順、状態、操作ID、戻る経路は維持します。変更するのは領域座標の取得元とページ計算です。KotlinのRegionFramesへleft/topを複写する方式を廃止し、CC-SystemのScaffold APIでHTMLの領域を解決します。既存カードの画面内配置は維持するため、個別内容の旧座標からの変換は引き続き残っています。固定件数一覧と候補・PICKERのページ数は共通Pagination APIで計算します。

## 回帰確認

GUIと共通の保存経路で呼出先へ必須引数を追加した直後のキャッシュ検証、引数契約の変更種類、循環FORの検証終了、破損JSONの起動時隔離、既存画面の座標・操作IDを確認します。

## 検証結果

- 2026年10月27日: Gradle build成功、自動テスト1320件成功（失敗・エラー・スキップ0件）。
- dev-envとfull-envで両プラグイン26.1027.1の起動完了・RCON応答・JARハッシュ一致を確認。smokeは両環境ともPASS、失敗0件。
- 設定項目の変更はなく、両環境のCC-System/config.ymlとKantanCommander/config.ymlのSHA-256は配置前後で一致。
- GUI操作の実機目視確認は未実施。座標・操作ID・状態分岐は既存の画面構築テストで確認。
- 既存DSL保存データ3件の読込警告が残っています。dev-envでは更新前ログも3件であることを確認しています。
- サーバー起動時にWindowsのOSHI性能カウンタ取得エラーも出ていますが、起動は完了しRCONとsmokeは正常です。

### dev-env

- CC-System: `173F76CD1BE8C9DA309C875465B8A762F1F49A527AE01077F3C6C045B1A87C4E`
- KantanCommander: `1147ADFADE337B6B3992D2D27DEC4F3BB1EAF465C626EA6C0BDDEB6671669650`

### full-env

- CC-System: `173F76CD1BE8C9DA309C875465B8A762F1F49A527AE01077F3C6C045B1A87C4E`
- KantanCommander: `1147ADFADE337B6B3992D2D27DEC4F3BB1EAF465C626EA6C0BDDEB6671669650`
