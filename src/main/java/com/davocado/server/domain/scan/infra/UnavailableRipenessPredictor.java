package com.davocado.server.domain.scan.infra;

import com.davocado.server.global.exception.BusinessException;
import com.davocado.server.global.exception.ErrorCode;
import java.math.BigDecimal;

/**
 * Stands in when no AI service is configured.
 *
 * <p>Scanning then fails with a truthful 502 instead of the whole application refusing to start,
 * which keeps every other endpoint usable while the AI service is still being deployed.
 */
public class UnavailableRipenessPredictor implements RipenessPredictor {

    @Override
    public PredictionResult predict(byte[] imageBytes, int targetStage, BigDecimal tempCelsius) {
        throw new BusinessException(ErrorCode.INFERENCE_SERVICE_UNAVAILABLE);
    }
}
