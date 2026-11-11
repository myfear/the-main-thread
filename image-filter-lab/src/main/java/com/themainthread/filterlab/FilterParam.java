package com.themainthread.filterlab;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilterParam(
        String name,
        String label,
        String type,
        Double min,
        Double max,
        Double step,
        Object defaultValue,
        String unit,
        List<FilterOption> options,
        String help) {

    public static FilterParam range(String name, String label, double min, double max, double step, double defaultValue,
            String unit, String help) {
        return new FilterParam(name, label, "range", min, max, step, defaultValue, unit, null, help);
    }

    public static FilterParam select(String name, String label, String defaultValue, List<FilterOption> options,
            String help) {
        return new FilterParam(name, label, "select", null, null, null, defaultValue, null, options, help);
    }

    public static FilterParam toggle(String name, String label, boolean defaultValue, String help) {
        return new FilterParam(name, label, "toggle", null, null, null, defaultValue, null, null, help);
    }
}
