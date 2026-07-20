package com.davocado.server.domain.scan.infra;

/**
 * Classifies one avocado photo. The only seam between Spring and the AI service.
 *
 * <p>Implementations must throw {@code BusinessException(INFERENCE_SERVICE_UNAVAILABLE)} on any
 * transport failure, so callers never have to distinguish "service down" from "bad response".
 */
public interface RipenessPredictor {

    PredictionResult predict(byte[] imageBytes);
}
