package com.davocado.server.domain.scan.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Presentation values derived from {@code daysToTarget}. See API spec v1.0 section 3.1.
 *
 * <p>The DB spec (section 2.2) fixes the rule: the "D-N" label is the rounded {@code daysToTarget}.
 * {@code status} follows the same rounded value — positive means still ripening, zero means eat it
 * now, negative means overripe.
 *
 * <p>{@code stage_label} is deliberately not returned; the client derives it from
 * {@code predictedStage} so wording changes do not require a server deploy.
 */
public record ScanDisplay(String ddayText, String status) {

    /** Returns null when the AI service did not supply a D-day, so callers can omit the block. */
    public static ScanDisplay from(BigDecimal daysToTarget) {
        if (daysToTarget == null) {
            return null;
        }
        int days = daysToTarget.setScale(0, RoundingMode.HALF_UP).intValue();
        if (days > 0) {
            return new ScanDisplay("D-" + days, "ripening");
        }
        if (days == 0) {
            return new ScanDisplay("D-Day", "eat_now");
        }
        return new ScanDisplay("D+" + -days, "overripe");
    }
}
