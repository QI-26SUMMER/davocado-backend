package com.davocado.server.domain.notification.infra;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stands in when no real push provider is configured.
 *
 * <p>Logs what would have been sent instead of making a network call, so the notification
 * scheduler is fully testable before FCM/APNs credentials exist. When a real FCM/APNs adapter
 * is added, mark it {@code @Primary} (or remove this one) so it wins over this default.
 */
@Component
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public void send(String pushToken, Map<String, Object> payload) {
        // Never log the full token; a short prefix is enough to correlate log lines in dev.
        String tokenPrefix = pushToken == null || pushToken.length() < 8
                ? "(short)"
                : pushToken.substring(0, 8) + "...";
        log.info("Would send push to token [{}]: {}", tokenPrefix, payload);
    }
}
