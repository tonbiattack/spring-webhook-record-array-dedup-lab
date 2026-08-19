package jp.tonbiattack.debuglab.webhook;

/**
 * 受信したWebhookを、イベント種別と復号済みペイロードで表す値オブジェクトです。
 *
 * <p>このバグ状態では、recordが自動生成する等価性をそのまま使います。</p>
 */
public record WebhookDelivery(String eventType, byte[] payload) {
}
