package jp.tonbiattack.debuglab.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/deliveries")
public class WebhookDeliveryController {

    private final WebhookDeliveryService deliveryService;

    public WebhookDeliveryController(WebhookDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody WebhookDelivery delivery) {
        if (deliveryService.registerIfFirstDelivery(delivery)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
