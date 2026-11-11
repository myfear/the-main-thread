package com.themainthread.filterlab;

import java.util.List;

import com.jhlabs.image.DitherFilter;

public final class DitherPresets {

    public record Preset(String key, String label, String note, int size, int[] matrix) {
    }

    private static final List<Preset> PRESETS = List.of(
            preset("magic4", "Magic 4 x 4", "The default matrix. A small, evenly spread threshold tile.",
                    DitherFilter.ditherMagic4x4Matrix),
            preset("ordered4", "Ordered 4 x 4", "A classic ordered pattern with a finer distribution.",
                    DitherFilter.ditherOrdered4x4Matrix),
            preset("lines4", "Lines 4 x 4", "A linear ramp through the tile. The thresholds march row by row.",
                    DitherFilter.ditherLines4x4Matrix),
            preset("halftone6", "Halftone 6 x 6", "Clustered thresholds that imitate a rotated halftone screen.",
                    DitherFilter.dither90Halftone6x6Matrix),
            preset("cluster3", "Clustered 6 x 6", "Clustered dots with a small repeating cell.",
                    DitherFilter.ditherCluster3Matrix),
            preset("ordered6", "Ordered 8 x 8", "A larger ordered tile. The pattern becomes less obvious at normal viewing size.",
                    DitherFilter.ditherOrdered6x6Matrix),
            preset("cluster8", "Clustered 16 x 16", "A large clustered tile. Good for showing the dot structure behind the tone.",
                    DitherFilter.ditherCluster8Matrix));

    private DitherPresets() {
    }

    private static Preset preset(String key, String label, String note, int[] matrix) {
        int size = (int) Math.sqrt(matrix.length);
        return new Preset(key, label, note, size, matrix);
    }

    public static List<Preset> all() {
        return PRESETS;
    }

    public static Preset byKey(String key) {
        return PRESETS.stream()
                .filter(preset -> preset.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown dither matrix: " + key));
    }
}
