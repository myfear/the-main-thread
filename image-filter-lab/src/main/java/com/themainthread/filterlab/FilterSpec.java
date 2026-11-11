package com.themainthread.filterlab;

import java.util.List;

public record FilterSpec(
        String id,
        String name,
        String group,
        String summary,
        boolean deepDive,
        List<FilterParam> params) {
}
