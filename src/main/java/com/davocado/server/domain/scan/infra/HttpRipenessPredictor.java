package com.davocado.server.domain.scan.infra;

import com.davocado.server.global.exception.BusinessException;
import com.davocado.server.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

/**
 * Calls the AI service over HTTP.
 *
 * <p>Speaks the Vertex custom-container envelope the serving app implements:
 * request {@code {"instances":[{"b64": "..."}]}}, response {@code {"predictions":[{...}]}}.
 *
 * <p>Every failure mode collapses into {@code INFERENCE_SERVICE_UNAVAILABLE} (502) — the client
 * cannot act on the difference between a timeout, a 500, and a malformed body.
 */
public class HttpRipenessPredictor implements RipenessPredictor {

    private static final Logger log = LoggerFactory.getLogger(HttpRipenessPredictor.class);

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String predictPath;

    public HttpRipenessPredictor(RestClient restClient, String predictPath) {
        this.restClient = restClient;
        this.predictPath = predictPath;
    }

    @Override
    public PredictionResult predict(byte[] imageBytes) {
        String encoded = Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> body = Map.of("instances", List.of(Map.of("b64", encoded)));

        Map<String, Object> response;
        try {
            response = restClient.post().uri(predictPath).body(body).retrieve().body(RESPONSE_TYPE);
        } catch (Exception ex) {
            log.error("Inference call failed", ex);
            throw new BusinessException(ErrorCode.INFERENCE_SERVICE_UNAVAILABLE);
        }
        return toResult(response);
    }

    private PredictionResult toResult(Map<String, Object> response) {
        Object predictions = response == null ? null : response.get("predictions");
        if (!(predictions instanceof List<?> list) || list.isEmpty()) {
            log.error("Inference response had no predictions: {}", response);
            throw new BusinessException(ErrorCode.INFERENCE_SERVICE_UNAVAILABLE);
        }
        if (!(list.get(0) instanceof Map<?, ?> prediction)) {
            log.error("Unexpected prediction entry: {}", list.get(0));
            throw new BusinessException(ErrorCode.INFERENCE_SERVICE_UNAVAILABLE);
        }
        // The service reports a per-image failure inline rather than with an HTTP error.
        if (prediction.get("error") != null) {
            log.warn("Inference rejected the image: {}", prediction.get("error"));
            throw new BusinessException(ErrorCode.NO_AVOCADO_DETECTED);
        }

        Integer predictedStage = asInt(prediction.get("predicted_stage"));
        String modelVersion = (String) prediction.get("model_version");
        if (predictedStage == null || modelVersion == null) {
            log.error("Prediction missing required fields: {}", prediction);
            throw new BusinessException(ErrorCode.INFERENCE_SERVICE_UNAVAILABLE);
        }

        return new PredictionResult(
                predictedStage,
                asDecimal(prediction.get("confidence")),
                asProbs(prediction.get("stage_probs")),
                modelVersion,
                // Not returned yet — the days-until-target formula is still open on the ML side.
                asDecimal(prediction.get("days_to_target")),
                asDate(prediction.get("estimated_peak_date")));
    }

    private Integer asInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private BigDecimal asDecimal(Object value) {
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : null;
    }

    private List<Double> asProbs(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        return list.stream().map(v -> v instanceof Number n ? n.doubleValue() : null).toList();
    }

    private LocalDate asDate(Object value) {
        return value instanceof String text && !text.isBlank() ? LocalDate.parse(text) : null;
    }
}
