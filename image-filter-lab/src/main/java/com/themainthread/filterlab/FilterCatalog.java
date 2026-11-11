package com.themainthread.filterlab;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FilterCatalog {

    private static final List<FilterOption> DITHER_MATRICES = List.of(
            new FilterOption("magic4", "Magic 4 x 4"),
            new FilterOption("ordered4", "Ordered 4 x 4"),
            new FilterOption("lines4", "Lines 4 x 4"),
            new FilterOption("halftone6", "Halftone 6 x 6"),
            new FilterOption("cluster3", "Clustered 6 x 6"),
            new FilterOption("ordered6", "Ordered 8 x 8"),
            new FilterOption("cluster8", "Clustered 16 x 16"));

    private static final List<FilterSpec> FILTERS = List.of(
            new FilterSpec("gaussian", "Gaussian Blur", "Blur and sharpening",
                    "A normal distribution spread across neighbouring pixels. Softens detail and is the base for unsharp masking.",
                    true,
                    List.of(FilterParam.range("radius", "Radius", 0, 20, 0.5, 4, "px",
                            "The library turns the radius into sigma and a one-dimensional kernel."))),
            new FilterSpec("dither", "Dither", "Colour reduction",
                    "Ordered threshold matrices trade smooth gradients for patterns. The same idea sits behind halftone screens.",
                    true,
                    List.of(
                            FilterParam.select("matrix", "Threshold matrix", "magic4", DITHER_MATRICES,
                                    "The matrix tiles across the image and shifts the rounding threshold at each pixel."),
                            FilterParam.range("levels", "Levels per channel", 2, 8, 1, 3, "",
                                    "With colour disabled, two levels give black and white. With colour enabled, two levels per channel allow eight RGB combinations."),
                            FilterParam.toggle("colorDither", "Dither colour channels", true,
                                    "Turn this off to average the three channels and produce a monochrome pattern."))),
            new FilterSpec("quantize", "Quantize", "Colour reduction",
                    "Builds an image-specific palette with an octree. Error diffusion is disabled for the M2 library release.",
                    false,
                    List.of(
                            FilterParam.range("colors", "Maximum palette colours", 8, 64, 1, 16, "",
                                    "The library supports a minimum budget of eight colours; the result may use fewer."))),
            new FilterSpec("unsharp", "Unsharp Mask", "Blur and sharpening",
                    "Subtracts a blurred copy from the original to lift local contrast. The darkroom method, in code.",
                    false,
                    List.of(
                            FilterParam.range("radius", "Blur radius", 0.5, 10, 0.5, 2, "px",
                                    "Sets the size of the detail that gets the contrast boost."),
                            FilterParam.range("amount", "Amount", 0.1, 3, 0.1, 0.5, "",
                                    "Scales the difference between the original and the blurred copy."),
                            FilterParam.range("threshold", "Threshold", 0, 64, 1, 1, "",
                                    "Differences below this value are left alone, which keeps smooth areas quiet."))),
            new FilterSpec("posterize", "Posterize", "Tone",
                    "Maps every channel onto a smaller set of evenly spaced values.",
                    false, List.of(FilterParam.range("levels", "Levels", 2, 16, 1, 5, "", ""))),
            new FilterSpec("sepia", "Sepia", "Tone",
                    "A fixed 3 x 3 colour transform that warms the image without a separate grayscale step.",
                    false, List.of()),
            new FilterSpec("grayscale", "Grayscale", "Tone",
                    "Uses NTSC luma weights, so green contributes more than red and blue.",
                    false, List.of()),
            new FilterSpec("solarize", "Solarize", "Tone",
                    "Maps shadows and highlights toward white and the midpoint toward black.",
                    false, List.of()),
            new FilterSpec("contrast", "Contrast", "Tone",
                    "Pushes values away from the midpoint with a simple transfer function.",
                    false, List.of(FilterParam.range("contrast", "Contrast", 0.5, 2, 0.05, 1.25, "", ""))),
            new FilterSpec("invert", "Invert", "Tone",
                    "Subtracts each colour channel from 255.",
                    false, List.of()));

    public List<FilterSpec> all() {
        return FILTERS;
    }
}
