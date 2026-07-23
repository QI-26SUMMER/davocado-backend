package com.davocado.server.domain.scan.infra;

import java.math.BigDecimal;

/**
 * Classifies one avocado photo. The only seam between Spring and the AI service.
 *
 * <p>Implementations must throw {@code BusinessException(INFERENCE_SERVICE_UNAVAILABLE)} on any
 * transport failure, so callers never have to distinguish "service down" from "bad response".
 */
public interface RipenessPredictor {

    /**
     * @param targetStage the user's snapshotted preferred stage (1~5), always sent
     * @param tempCelsius ambient temperature, nullable — omitted from the AI request when null
     */
    PredictionResult predict(byte[] imageBytes, int targetStage, BigDecimal tempCelsius);
}
