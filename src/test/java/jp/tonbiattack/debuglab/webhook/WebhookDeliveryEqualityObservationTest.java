package jp.tonbiattack.debuglab.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class WebhookDeliveryEqualityObservationTest {

    @Test
    void equalByteContentsButDifferentArrayReferences_areNotEqualAsBuggyRecordComponents() {
        byte[] firstPayload = {1, 2, 3};
        byte[] secondPayload = {1, 2, 3};
        WebhookDelivery first = new WebhookDelivery("invoice.paid", firstPayload);
        WebhookDelivery second = new WebhookDelivery("invoice.paid", secondPayload);
        Set<WebhookDelivery> deliveries = new HashSet<>(Set.of(first, second));

        assertAll(
                () -> assertThat(firstPayload).isNotSameAs(secondPayload),
                () -> assertThat(Arrays.equals(firstPayload, secondPayload)).isTrue(),
                () -> assertThat(first).isNotEqualTo(second),
                () -> assertThat(deliveries).hasSize(2)
        );
    }

    @Test
    void aRecordWithoutAnArrayComponent_isDeduplicatedNormally() {
        Set<EventTypeOnly> eventTypes = new HashSet<>();

        eventTypes.add(new EventTypeOnly("invoice.paid"));
        eventTypes.add(new EventTypeOnly("invoice.paid"));

        assertThat(eventTypes).hasSize(1);
    }

    private record EventTypeOnly(String eventType) {
    }
}
