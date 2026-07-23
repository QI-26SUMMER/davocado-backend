package com.davocado.server.domain.notification.infra;

import java.util.Map;

/**
 * Delivers one push notification to a device. The only seam between Spring and the push provider.
 *
 * <p>The real FCM/APNs adapter is added once push infra is chosen; until then {@link
 * LoggingPushSender} stands in, mirroring how {@code RipenessPredictor} and {@code ImageStorage}
 * gate their real implementations behind configuration.
 */
public interface PushSender {

    void send(String pushToken, Map<String, Object> payload);
}
