package jp.tonbiattack.debuglab.webhook;

import java.util.Arrays;

/**
 * 受信したWebhookを、イベント種別と復号済みペイロードで表す値オブジェクトです。
 */
public record WebhookDelivery(String eventType, byte[] payload) {

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
}
