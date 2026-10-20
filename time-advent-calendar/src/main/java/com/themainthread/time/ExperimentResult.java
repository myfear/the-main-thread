package com.themainthread.time;

import java.util.List;

public record ExperimentResult(List<Observation> observations, String takeaway) {
    public record Observation(String label, String value) {
    }

    public static ExperimentResult of(String takeaway, Object... pairs) {
        var observations = new java.util.ArrayList<Observation>();
        for (int index = 0; index < pairs.length; index += 2) {
            observations.add(new Observation(pairs[index].toString(), String.valueOf(pairs[index + 1])));
        }
        return new ExperimentResult(List.copyOf(observations), takeaway);
    }
}
