package com.davocado.server.domain.scan.infra;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Attaches a Google-signed ID token to each AI request so a private ({@code --no-allow-unauthenticated})
 * Cloud Run service accepts it.
 *
 * <p>Credentials come from Application Default Credentials: a service-account key locally
 * ({@code GOOGLE_APPLICATION_CREDENTIALS}) or the metadata server on GCP — both mint ID tokens, so
 * the same code works in both places. The audience must be the Cloud Run service URL.
 */
public class IdTokenAuthInterceptor implements ClientHttpRequestInterceptor {

    private final IdTokenCredentials credentials;

    public IdTokenAuthInterceptor(String audience) throws IOException {
        GoogleCredentials adc = GoogleCredentials.getApplicationDefault();
        if (!(adc instanceof IdTokenProvider provider)) {
            throw new IOException("Application Default Credentials do not support ID tokens (got "
                    + adc.getClass().getSimpleName() + ")");
        }
        this.credentials = IdTokenCredentials.newBuilder()
                .setIdTokenProvider(provider)
                .setTargetAudience(audience)
                .build();
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        credentials.refreshIfExpired();
        request.getHeaders().setBearerAuth(credentials.getIdToken().getTokenValue());
        return execution.execute(request, body);
    }
}
