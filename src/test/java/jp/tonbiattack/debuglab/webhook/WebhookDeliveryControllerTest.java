package jp.tonbiattack.debuglab.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WebhookDeliveryControllerTest {

    private static final String FIRST_DELIVERY = """
            {"eventType":"invoice.paid","payload":"AQID"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookDeliveryService deliveryService;

    @BeforeEach
    void resetProcessedDeliveries() {
        deliveryService.clear();
    }

    @Test
    void identicalWebhookPayload_isAcceptedOnlyOnceAndRecordedOnce() throws Exception {
        mockMvc.perform(post("/webhooks/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FIRST_DELIVERY))
                .andExpect(status().isAccepted());

        MvcResult secondResponse = mockMvc.perform(post("/webhooks/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FIRST_DELIVERY))
                .andReturn();

        assertAll(
                () -> assertThat(secondResponse.getResponse().getStatus())
                        .as("同じ配信の二度目はHTTP 409で拒否される")
                        .isEqualTo(409),
                () -> assertThat(deliveryService.processedCount())
                        .as("同じ配信は一件だけ処理済みとして記録される")
                        .isEqualTo(1)
        );
    }

    @Test
    void differentPayloads_areAcceptedAndRecordedSeparately() throws Exception {
        mockMvc.perform(post("/webhooks/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FIRST_DELIVERY))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/webhooks/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventType":"invoice.paid","payload":"BAUG"}
                                """))
                .andExpect(status().isAccepted());

        assertThat(deliveryService.processedCount())
                .as("ペイロード内容が異なる配信は別件として記録される")
                .isEqualTo(2);
    }
}
