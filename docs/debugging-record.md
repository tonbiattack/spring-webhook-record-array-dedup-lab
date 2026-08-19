# E001: `record`の`byte[]`で同じWebhook配信を重複処理する

## 目的

Webhook受信処理では、同じ`eventType`と同じペイロード内容を持つ二度目の配信を、新規処理として通してはいけません。このラボでは、同じJSONを順番に二度POSTした場合に、二度目を`409 Conflict`として拒否し、処理済み件数を`1`に保つ契約を検証します。

## 最初に観測した事実

バグ状態はコミット[`7135d89`](../commit/7135d89)です。次のコマンドで、HTTP応答と処理済み件数の二つのアサーションが意図どおり失敗しました。

```bash
git checkout 7135d89
mvn --batch-mode test -Dtest=WebhookDeliveryControllerTest
```

| 観測項目 | 期待 | 実際 | 根拠 |
| --- | --- | --- | --- |
| 最初のPOST | `202 Accepted` | `202 Accepted` | `WebhookDeliveryControllerTest` |
| 二度目のPOST | `409 Conflict` | `202 Accepted` | `WebhookDeliveryControllerTest` |
| 最終状態 | 処理済み件数`1` | 処理済み件数`2` | `WebhookDeliveryService.processedCount()` |
| 配列内容 | `byte[] {1, 2, 3}`同士は同じ内容 | `Arrays.equals`は`true` | `WebhookDeliveryEqualityObservationTest` |
| recordの等価性 | 内容が同じなら同一配信 | `WebhookDelivery.equals`は`false`、集合件数`2` | `WebhookDeliveryEqualityObservationTest` |

```text
expected: 409
 but was: 202

expected: 1
 but was: 2
```

完全な出力は[`evidence/01-bug-controller-test-output.txt`](../evidence/01-bug-controller-test-output.txt)に保存しています。ログに出たHTTP `202`だけではなく、`processedCount()`の最終状態も別に確認したため、応答だけが誤って見えた可能性は除外できます。

## 実行環境と再現境界

入口には`MockMvc`を選びました。`MockMvc`は、起動済みアプリケーションの実HTTPポートを必要とせず、Spring MVCのリクエスト処理を模擬リクエスト・レスポンスで実行できます。[4] この境界によって、JSONのBase64文字列が`byte[]`へ変換され、controller、service、集合への登録を通る経路を検証できます。

ただし、HTTPステータスだけでは「二度目の処理が本当に記録されたか」は分かりません。そのため、二度のPOST後に`WebhookDeliveryService.processedCount()`を独立に読み、処理済み件数を最終状態として検証しました。また、原因をHTTP層から切り離すため、別テストで同じ内容・別参照の`byte[]`から作った二つのrecordを比較しています。

## 競合仮説と検証

| 仮説 | 確認方法 | 結果 |
| --- | --- | --- |
| Spring MVCのルーティングまたはレスポンス生成が二度目のPOSTを誤処理している | 二つの`MockMvc.perform`を明示的に実行し、各POST後の応答と最終件数を確認する | 二つの意図的なPOSTはいずれも正常にhandlerへ到達する。失敗はルーティングではなく、二度目を既存配信と判定できないことにあるため棄却。 |
| `HashSet`が重複排除を正しく実装していない | `String`だけを持つrecordを二度追加する対照テストを実行する | 集合件数は`1`になった。`HashSet.add`は等しい既存要素がある場合に要素を追加しない契約であり、集合自体の問題ではない。[3] |
| `byte[]`コンポーネントを持つrecordの等価性が内容ではなく参照に依存する | 同じ内容で別参照の配列について、`Arrays.equals`、recordの`equals`、集合件数を同時に観測する | `Arrays.equals`は`true`、recordの`equals`は`false`、集合件数は`2`。採用。 |

## 確定した原因

`WebhookDelivery`は、バグ状態で次のように`byte[]`をそのままコンポーネントとして持つrecordでした。

```java
public record WebhookDelivery(String eventType, byte[] payload) {
}
```

recordはコンポーネントの値に基づく`equals`と`hashCode`を自動生成します。[1] しかし、`byte[]`は配列内容を自動で値比較する型ではありません。内容同一性が必要な場面では、`Arrays.equals(byte[], byte[])`および`Arrays.hashCode(byte[])`を使うAPIが提供されています。[2]

したがって、同じBase64文字列`AQID`から二度作られた別々の配列は、内容が同じでもrecordの自動生成等価性では同じ値になりません。`HashSet`は等価な要素がない場合に追加するため、このrecordをキーにした重複排除が破綻しました。[3] Spring MVCは症状を見せるHTTP境界ですが、直接原因はJavaの配列とrecordの等価性です。

## 最小修正

修正コミットは[`23423ae`](../commit/23423ae)です。`WebhookDelivery`だけに、バイト列内容を使う`equals`と、それに整合する`hashCode`を加えました。

```java
@Override
public boolean equals(Object other) {
    if (this == other) {
        return true;
    }
    if (!(other instanceof WebhookDelivery that)) {
        return false;
    }
    return eventType.equals(that.eventType)
            && Arrays.equals(payload, that.payload);
}

@Override
public int hashCode() {
    int result = eventType.hashCode();
    result = 31 * result + Arrays.hashCode(payload);
    return result;
}
```

この修正は、業務契約を「イベント種別とペイロード内容が同じなら同一配信」と明示します。`Arrays.equals`だけを加えて`hashCode`を自動生成のままにすると、同じ配信が必ず同じハッシュ値を持つ保証を失うため採用しませんでした。なお、配列の防御的コピーや並行安全な集合への置換は、今回観測した直接原因を超える変更なので含めていません。

## 回帰保証

修正後は、最初に失敗した`identicalWebhookPayload_isAcceptedOnlyOnceAndRecordedOnce`をそのまま残しています。このテストは二度目のHTTP応答が`409`であることと、処理済み件数が`1`であることを別々に検証します。さらに、次の二つのテストを維持します。

| テスト | 回帰として守る契約 |
| --- | --- |
| `differentPayloads_areAcceptedAndRecordedSeparately` | 同じイベント種別でもバイト列内容が異なる配信は二件として受理する。 |
| `equalByteContentsButDifferentArrayReferences_areEqualAsWebhookDeliveries` | 同じ内容・別参照の配列を持つrecordは等しく、集合に一件だけ残る。 |

修正後に実行した`mvn --batch-mode clean test`では、4テストがすべて成功しました。完全な出力は[`evidence/03-fixed-full-test-output.txt`](../evidence/03-fixed-full-test-output.txt)に保存しています。

## 再現手順

```bash
git checkout 7135d89
mvn --batch-mode test -Dtest=WebhookDeliveryControllerTest
# expected: 409, but was: 202
# expected: 1, but was: 2

git checkout main
mvn --batch-mode clean test
# Tests run: 4, Failures: 0, Errors: 0
```

## スコープと注意点

この修正は、ペイロード内容を識別子として扱うという明確な業務契約に対して有効です。ペイロードに署名用タイムスタンプや可変メタデータが含まれる場合、そのままの全バイト列を冪等性キーにしてよいとは限りません。

また、このラボの集合は単一スレッドのインメモリ状態です。複数リクエストの同時到着、複数インスタンス、プロセス再起動を含む実運用では、永続的な冪等キー、原子的な登録、署名検証、配送のリトライ方針を別に設計してください。ここでの修正は、可変配列を集合へ入れた後に変更しないことも前提にします。

## References

[1] [Oracle: Record Classes](https://docs.oracle.com/en/java/javase/17/language/records.html)

[2] [Oracle: `java.util.Arrays`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Arrays.html)

[3] [Oracle: `java.util.HashSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html)

[4] [Spring Framework Reference: MockMvc](https://docs.spring.io/spring-framework/reference/testing/mockmvc.html)
