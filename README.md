# Spring Webhookで`record`の`byte[]`により同一配信を重複処理する

Java/Spring MVCのWebhook受信処理を題材に、**同じJSONペイロードを二度送っても重複排除されない**不具合を、失敗するHTTPテスト、原因の直接観測、最小修正、回帰テストの順に追うデバッグ教材です。既定ブランチの`main`は常に成功状態であり、意図的に失敗する状態はGit履歴に独立して保存しています。

> 同じ`eventType`と同じバイト列を持つWebhookを二度POSTしたとき、二度目を`409 Conflict`にし、処理済み件数を`1`に保つことがこの教材の契約です。

## この題材で守る契約

| 段階 | 実施内容 | 確認すること |
| --- | --- | --- |
| 再現 | 同じJSONを二度POSTする | 二度目のHTTP応答が`409`ではなく`202`となり、件数が`2`になる |
| 観測 | 同じ内容・別参照の`byte[]`をrecordへ渡す | `Arrays.equals`は真でも、バグ状態のrecord同士は等しくない |
| 修正 | `equals`と`hashCode`をバイト列内容で明示する | `HashSet`が同じ配信を既存要素として扱う |
| 回帰防止 | 同じHTTPテストと値オブジェクトテストを再実行する | 二度目は`409`、件数は`1`、異なるバイト列は別配信のまま |

## 収録済みの教材

| ID | テーマ | 失敗時の観測 | 修正後に守る契約 |
| --- | --- | --- | --- |
| E001 | Spring Webhookで`record`の`byte[]`が同一配信を重複処理する | 二度目のPOSTが`202 Accepted`、処理済み件数が`2` | 二度目は`409 Conflict`、処理済み件数は`1` |

## 必要な環境

| 項目 | バージョン |
| --- | --- |
| JDK | 21 |
| Maven | 3.8以上 |
| Spring Boot | 3.4.5 |
| テスト境界 | Spring Boot Test / MockMvc |

## 最短の開始手順

```bash
mvn --batch-mode clean test
```

検証済みの`main`では、4テストがすべて成功します。`MockMvc`は実サーバーを起動せずにSpring MVCのリクエスト処理を通せるため、この教材ではHTTP応答を実境界として確認します。[4]

## バグを再現する

```bash
git checkout 7135d89
mvn --batch-mode test -Dtest=WebhookDeliveryControllerTest
# 二度目の応答: expected 409, but was 202
# 処理済み件数: expected 1, but was 2

git checkout main
mvn --batch-mode clean test
# Tests run: 4, Failures: 0, Errors: 0
```

バグコミットでは設定・コンパイルの失敗ではなく、同じPOSTを二度実行したときのアサーション差分だけがRedになります。実行結果は[`evidence/01-bug-controller-test-output.txt`](evidence/01-bug-controller-test-output.txt)に保存しています。

## 原因の要点

recordは各コンポーネントの値に基づく`equals`と`hashCode`を自動生成します。[1] しかし、`byte[]`は内容を自動比較する値型ではありません。そのため、同じ`AQID`をJSONから二度復号して別々の配列参照として受け取ると、バグ状態の`WebhookDelivery`は同一配信として認識されません。

`HashSet.add`は、等しい既存要素がない場合に要素を追加します。[3] バイト列の内容を比較したい場合、`Arrays.equals(byte[], byte[])`と`Arrays.hashCode(byte[])`で、ドメイン上の同一性を明示する必要があります。[2]

## プロジェクト構成

```text
.
├── docs/
│   ├── debugging-record.md      # 観測・仮説・原因・修正・適用範囲
│   ├── novelty-report.md        # 既存記事との四軸比較
│   └── topic-brief.md           # 実装前に固定した契約と再現境界
├── evidence/
│   ├── 01-bug-controller-test-output.txt
│   ├── 02-bug-equality-observation-output.txt
│   └── 03-fixed-full-test-output.txt
├── src/main/java/.../webhook/
│   ├── WebhookDelivery.java
│   ├── WebhookDeliveryController.java
│   └── WebhookDeliveryService.java
└── src/test/java/.../webhook/
    ├── WebhookDeliveryControllerTest.java
    └── WebhookDeliveryEqualityObservationTest.java
```

詳細な調査の流れは[デバッグ記録](docs/debugging-record.md)、既存コンテンツとの差分は[題材重複調査レポート](docs/novelty-report.md)を参照してください。

## 適用範囲

この教材は、単一プロセス・単一スレッドのインメモリ集合におけるJavaの等価性だけを扱います。実運用のWebhookでは、署名検証、冪等キーの設計、永続化、分散実行、並行リクエストに対する原子性を別途設計してください。

## References

[1] [Oracle: Record Classes](https://docs.oracle.com/en/java/javase/17/language/records.html)

[2] [Oracle: `java.util.Arrays`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Arrays.html)

[3] [Oracle: `java.util.HashSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html)

[4] [Spring Framework Reference: MockMvc](https://docs.spring.io/spring-framework/reference/testing/mockmvc.html)
