package jp.tonbiattack.debuglab.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebhookDedupLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookDedupLabApplication.class, args);
    }
}
