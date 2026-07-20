package com.davocado.server.domain.scan.infra;

import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the real AI client when {@code ai.base-url} is set, and a failing stand-in when it is not.
 *
 * <p>Timeouts are generous by default: the service is expected on Cloud Run, which scales to zero,
 * so the first request after an idle period pays a cold start.
 */
@Configuration
public class RipenessPredictorConfig {

    @Bean
    @ConditionalOnExpression("!'${ai.base-url:}'.isEmpty()")
    public RipenessPredictor httpRipenessPredictor(
            @Value("${ai.base-url}") String baseUrl,
            @Value("${ai.predict-path:/predict}") String predictPath,
            @Value("${ai.connect-timeout-seconds:10}") long connectTimeoutSeconds,
            @Value("${ai.read-timeout-seconds:60}") long readTimeoutSeconds,
            @Value("${ai.use-id-token:true}") boolean useIdToken,
            @Value("${ai.audience:}") String audience) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);
        if (useIdToken) {
            // The AI service is a private Cloud Run service; it only accepts callers presenting a
            // Google-signed ID token whose audience is the service URL.
            String tokenAudience = audience.isEmpty() ? baseUrl : audience;
            try {
                builder.requestInterceptor(new IdTokenAuthInterceptor(tokenAudience));
            } catch (IOException e) {
                throw new IllegalStateException(
                        "ai.use-id-token is enabled but ID-token credentials could not be initialized", e);
            }
        }
        return new HttpRipenessPredictor(builder.build(), predictPath);
    }

    @Bean
    @ConditionalOnMissingBean(RipenessPredictor.class)
    public RipenessPredictor unavailableRipenessPredictor() {
        return new UnavailableRipenessPredictor();
    }
}
