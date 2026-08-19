package jp.tonbiattack.debuglab.webhook;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class WebhookDeliveryService {

    private final Set<WebhookDelivery> processedDeliveries = new HashSet<>();

    /**
     * 初めて処理する配信なら登録してtrueを返し、既に処理済みならfalseを返します。
     */
    public boolean registerIfFirstDelivery(WebhookDelivery delivery) {
        return processedDeliveries.add(delivery);
    }

    /**
     * 現在記録されている処理済み配信数を返します。
     */
    public int processedCount() {
        return processedDeliveries.size();
    }

    /**
     * テストごとに決定的な初期状態へ戻します。
     */
    public void clear() {
        processedDeliveries.clear();
    }
}
