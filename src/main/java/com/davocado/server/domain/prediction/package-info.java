/**
 * Prediction domain: ripening-stage predictions and their source images.
 *
 * <p>Inference itself runs in the Python sidecar; this domain persists results and drives
 * D-day calculation. Sub-packages follow the convention:
 * {@code controller / service / repository / entity / dto}. Added in a later step.
 */
package com.davocado.server.domain.prediction;
