package com.themainthread.filterlab;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.jhlabs.image.ContrastFilter;
import com.jhlabs.image.DitherFilter;
import com.jhlabs.image.GaussianFilter;
import com.jhlabs.image.GrayscaleFilter;
import com.jhlabs.image.InvertFilter;
import com.jhlabs.image.PosterizeFilter;
import com.jhlabs.image.QuantizeFilter;
import com.jhlabs.image.SepiaFilter;
import com.jhlabs.image.SolarizeFilter;
import com.jhlabs.image.UnsharpFilter;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ImageFilterService {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    public BufferedImage apply(BufferedImage source, String filter, Map<String, Object> settings) {
        BufferedImage input = toArgb(source);
        return switch (filter) {
            case "gaussian" -> new GaussianFilter((float) clamp(number(settings, "radius", 4), 0, 20)).filter(input, null);
            case "dither" -> dither(input, settings);
            case "quantize" -> quantize(input, settings);
            case "unsharp" -> unsharp(input, settings);
            case "posterize" -> {
                PosterizeFilter posterize = new PosterizeFilter();
                posterize.setNumLevels(integer(settings, "levels", 5, 2, 16));
                yield posterize.filter(input, null);
            }
            case "sepia" -> new SepiaFilter().filter(input, null);
            case "grayscale" -> new GrayscaleFilter().filter(input, null);
            case "solarize" -> new SolarizeFilter().filter(input, null);
            case "contrast" -> {
                ContrastFilter contrast = new ContrastFilter();
                contrast.setContrast((float) clamp(number(settings, "contrast", 1.25), 0.5, 2));
                yield contrast.filter(input, null);
            }
            case "invert" -> new InvertFilter().filter(input, null);
            default -> throw new IllegalArgumentException("Unknown filter: " + filter);
        };
    }

    private BufferedImage dither(BufferedImage input, Map<String, Object> settings) {
        DitherPresets.Preset preset = DitherPresets.byKey(string(settings, "matrix", "magic4"));
        DitherFilter dither = new DitherFilter();
        dither.setMatrix(preset.matrix());
        dither.setLevels(integer(settings, "levels", 3, 2, 8));
        dither.setColorDither(bool(settings, "colorDither", true));
        return dither.filter(input, null);
    }

    private BufferedImage quantize(BufferedImage input, Map<String, Object> settings) {
        QuantizeFilter quantize = new QuantizeFilter();
        quantize.setNumColors(integer(settings, "colors", 16, 8, 64));
        // M2's diffusion loop omits the row offset; expose palette reduction only.
        quantize.setDither(false);
        return quantize.filter(input, null);
    }

    private BufferedImage unsharp(BufferedImage input, Map<String, Object> settings) {
        UnsharpFilter unsharp = new UnsharpFilter();
        unsharp.setRadius((float) clamp(number(settings, "radius", 2), 0.5, 10));
        unsharp.setAmount((float) clamp(number(settings, "amount", 0.5), 0.1, 3));
        unsharp.setThreshold(integer(settings, "threshold", 1, 0, 64));
        return unsharp.filter(input, null);
    }

    public GaussianTheory gaussianTheory(double radius) {
        double safeRadius = clamp(radius, 0, 20);
        if (safeRadius == 0) {
            return new GaussianTheory(safeRadius, 0, 1, List.of(1.0));
        }
        float[] kernel = GaussianFilter.makeKernel((float) safeRadius).getKernelData(null);
        List<Double> weights = new ArrayList<>(kernel.length);
        for (float value : kernel) {
            weights.add(round(value));
        }
        return new GaussianTheory(safeRadius, safeRadius / 3.0, kernel.length, weights);
    }

    public byte[] png(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("No PNG writer available");
            }
            return output.toByteArray();
        }
        catch (IOException exception) {
            throw new IllegalStateException("Unable to encode the filtered image", exception);
        }
    }

    private static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        }
        finally {
            graphics.dispose();
        }
        return converted;
    }

    private static double number(Map<String, Object> settings, String key, double fallback) {
        Object value = settings.get(key);
        if (value == null && !settings.containsKey(key)) {
            return fallback;
        }
        double parsed;
        if (value instanceof Number number) {
            parsed = number.doubleValue();
        }
        else if (value instanceof String text && !text.isBlank()) {
            try {
                parsed = Double.parseDouble(text);
            }
            catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Setting " + key + " must be a finite number", exception);
            }
        }
        else {
            throw new IllegalArgumentException("Setting " + key + " must be a finite number");
        }
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException("Setting " + key + " must be a finite number");
        }
        return parsed;
    }

    private static int integer(Map<String, Object> settings, String key, int fallback, int min, int max) {
        return (int) Math.round(clamp(number(settings, key, fallback), min, max));
    }

    private static boolean bool(Map<String, Object> settings, String key, boolean fallback) {
        Object value = settings.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null && !settings.containsKey(key)) {
            return fallback;
        }
        throw new IllegalArgumentException("Setting " + key + " must be true or false");
    }

    private static String string(Map<String, Object> settings, String key, String fallback) {
        Object value = settings.get(key);
        return value == null ? fallback : value.toString();
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("The value must be a finite number");
        }
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}
