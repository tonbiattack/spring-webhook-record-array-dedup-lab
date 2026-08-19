# 題材企画: Spring Webhookで`record`の`byte[]`により同一配信を重複処理する

## 対象

| 項目 | 内容 |
| --- | --- |
| 対象言語 | Java 21 |
| 対象読者 | Spring MVCでJSONリクエストを受け付け、Javaの`record`をDTO・識別キー・値オブジェクトとして使う中級者 |
| 難易度プロファイル | 実践・上級 |
| 選定理由 | HTTP境界では同じBase64ペイロードでもリクエストごとに別の`byte[]`が生成されるため、見た目だけでは原因を誤りやすい。Javaのrecord自動生成メソッド、配列の参照同一性、`HashSet`の重複判定が交差する一つの原因を、二つの独立した観測で絞り込める。 |
| 実行基盤 | Maven、Spring Boot 3.4.5、Java 21、`spring-boot-starter-web`、`spring-boot-starter-test` |
| フレームワーク非依存性 | 症状はSpring MVCのJSON入力を境界にして示すが、直接原因はJava recordのコンポーネント等価性と`byte[]`の参照同一性である。`new WebhookDelivery("invoice.paid", new byte[]{1, 2})`を二つ作るだけでも同じ失敗を再現でき、SpringのDI・HTTPルーティング・永続化・トランザクションには依存しない。 |

## 学習する契約

> 同一の`eventType`と同一バイト列を持つWebhookを二度POSTした場合、二度目は`409 Conflict`となり、処理済み件数は`1`のままであるべきだが、バグ状態では二度とも`202 Accepted`となり、処理済み件数が`2`になる。

### 対象の直接原因

`byte[]`をコンポーネントに持つrecordの自動生成`equals`／`hashCode`は、配列内容ではなく配列オブジェクトの等価性を使う。JSON復号で毎回別の配列参照になるため、`HashSet<WebhookDelivery>`は同じバイト列の配信を既存要素として認識しない。

### 対象外

このラボはWebhook署名検証、少なくとも一回配送の分散システム設計、並行POSTの原子性、永続ストア、Springのプロキシやトランザクション、HTTPリトライ方針を扱わない。単一スレッドで順番に到着する入力を、プロセス内集合で重複排除する最小契約だけを扱う。

## 再現設計

| 要素 | 決定 |
| --- | --- |
| 公開境界 | `POST /webhooks/deliveries`を`MockMvc`で実行し、`WebhookDeliveryService`の件数も公開のテスト用参照APIで読む。 |
| 入力・初期状態 | 同じJSON文字列`{"eventType":"invoice.paid","payload":"AQID"}`を、空のインメモリ集合に順番に二度POSTする。 |
| Redの観測 | 二度目のHTTPレスポンスに`409 Conflict`を期待するが、バグ状態では`202 Accepted`となる。 |
| 最終観測 | 二度のPOST後に`WebhookDeliveryService.processedCount()`を読み、処理済み件数が`1`であることを独立に検証する。さらに、同じ`eventType`で異なるバイト列は別件として受理されることを確認する。 |
| 決定性 | 時刻、乱数、並行実行、`sleep`を使用しない。固定JSONとインメモリ集合だけを使う。 |
| 固定状態の検証コマンド | `mvn --batch-mode clean test` |
| バグ状態の確認コマンド | `git checkout <bug-commit>`後に`mvn --batch-mode test -Dtest=WebhookDeliveryControllerTest` |

## 仮説

| 仮説 | どう検証または除外するか |
| --- | --- |
| A: Spring MVCが同じPOSTを二度ルーティングまたはシリアライズしている | 同じリクエストから変換された二つの`byte[]`について、配列内容と参照同一性をテストで観測する。内容が同じで参照が異なれば、HTTPルーティングの重複では説明できない。 |
| B: `HashSet`自体が重複排除に失敗している | 文字列だけの`record`を集合に二度追加する対照テストを置く。文字列recordが一件に保たれ、`byte[]`付きrecordだけが二件になるなら集合実装ではない。 |
| C: recordの配列コンポーネントが内容ではなく参照で比較される | 内容が同じで別参照の二つの`WebhookDelivery`について`Arrays.equals(payloadA, payloadB)`、`deliveryA.equals(deliveryB)`、`HashSet`の件数を同時に観測する。 |

## 予定する履歴

| 順序 | コミットの目的 | 期待する状態 |
| --- | --- | --- |
| 1 | Webhook配信の重複排除失敗を再現する | `WebhookDeliveryControllerTest`が、二度目のレスポンス`409`期待に対し実際`202`で失敗する。 |
| 2 | バイト列内容によるWebhook配信同一性を定義する | 同じ検証が成功し、全テストも成功する。 |
