package com.themainthread.exoplanets;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Planet(
        @JsonProperty("pl_name") String name,
        @JsonProperty("discoverymethod") String method,
        @JsonProperty("disc_year") Integer year,
        @JsonProperty("pl_orbper") Double period,
        @JsonProperty("pl_orbperlim") Integer periodLimit,
        @JsonProperty("pl_rade") Double radius,
        @JsonProperty("pl_radelim") Integer radiusLimit) {

    public boolean hasMeasuredSizeAndPeriod() {
        return positive(period) && positive(radius)
                && Integer.valueOf(0).equals(periodLimit)
                && Integer.valueOf(0).equals(radiusLimit);
    }

    private static boolean positive(Double value) {
        return value != null && Double.isFinite(value) && value > 0;
    }
}
