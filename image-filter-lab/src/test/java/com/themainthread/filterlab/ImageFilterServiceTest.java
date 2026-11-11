package com.themainthread.filterlab;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class ImageFilterServiceTest {

    private final ImageFilterService filters = new ImageFilterService();
    private final SampleImage sample = new SampleImage();

    @Test
    void gaussianKernelIsNormalisedAndSymmetric() {
        GaussianTheory theory = filters.gaussianTheory(6);
        assertEquals(13, theory.taps());
        assertEquals(2.0, theory.sigma(), 0.0001);
        double sum = theory.weights().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 0.0001);
        assertEquals(theory.weights().get(0), theory.weights().get(theory.weights().size() - 1), 0.000001);
    }

    @Test
    void gaussianChangesPixelsAndKeepsTheCanvas() {
        BufferedImage source = sample.create();
        BufferedImage result = filters.apply(source, "gaussian", Map.of("radius", 8));
        assertEquals(source.getWidth(), result.getWidth());
        assertEquals(source.getHeight(), result.getHeight());
        assertNotEquals(source.getRGB(640, 420), result.getRGB(640, 420));
    }

    @Test
    void monochromeDitherUsesOnlyTheChosenLevels() {
        BufferedImage result = filters.apply(sample.create(), "dither",
                Map.of("matrix", "halftone6", "levels", 2, "colorDither", false));
        Set<Integer> values = new HashSet<>();
        for (int y = 0; y < result.getHeight(); y += 7) {
            for (int x = 0; x < result.getWidth(); x += 7) {
                int rgb = result.getRGB(x, y);
                values.add(rgb & 0xff);
                assertEquals(rgb & 0xff, (rgb >> 8) & 0xff);
                assertEquals(rgb & 0xff, (rgb >> 16) & 0xff);
            }
        }
        assertEquals(Set.of(0, 255), values);
    }

    @Test
    void settingsAreClampedToSupportedRanges() {
        BufferedImage result = filters.apply(sample.create(), "dither",
                Map.of("matrix", "magic4", "levels", 1, "colorDither", false));
        Set<Integer> values = new HashSet<>();
        for (int y = 0; y < result.getHeight(); y += 7) {
            for (int x = 0; x < result.getWidth(); x += 7) {
                values.add(result.getRGB(x, y) & 0xff);
            }
        }
        assertEquals(Set.of(0, 255), values);
    }

    @Test
    void quantizeKeepsThePaletteSmall() {
        BufferedImage result = filters.apply(sample.create(), "quantize",
                Map.of("colors", 8));
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                colours.add(result.getRGB(x, y) & 0xffffff);
            }
        }
        assertTrue(colours.size() <= 8, "expected at most 8 colours, found " + colours.size());
    }

    @Test
    void sampleImageEncodingMatchesItsCanvas() throws Exception {
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(sample.png()));
        assertEquals(1280, decoded.getWidth());
        assertEquals(800, decoded.getHeight());
    }

    @Test
    void zeroRadiusCopiesPixelsIncludingAlpha() {
        BufferedImage source = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x80402010);
        source.setRGB(1, 0, 0xffef9a30);
        BufferedImage result = filters.apply(source, "gaussian", Map.of("radius", 0));
        assertArrayEquals(source.getRGB(0, 0, 2, 1, null, 0, 2), result.getRGB(0, 0, 2, 1, null, 0, 2));
        assertEquals(List.of(1.0), filters.gaussianTheory(0).weights());
    }

    @Test
    void fractionalRadiusTheoryMatchesTheLibraryKernel() {
        GaussianTheory theory = filters.gaussianTheory(0.25);
        assertEquals(3, theory.taps());
        assertEquals(0.25 / 3, theory.sigma(), 0.000001);
        assertEquals(List.of(0.0, 1.0, 0.0), theory.weights());
    }

    @Test
    void largeIntegerSettingsClampBeforeNarrowing() {
        BufferedImage source = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xff395b7d);
        source.setRGB(1, 0, 0xffa7c5e3);
        source.setRGB(2, 0, 0xff517395);
        assertArrayEquals(filters.png(filters.apply(source, "posterize", Map.of("levels", 16))),
                filters.png(filters.apply(source, "posterize", Map.of("levels", 1e30))));
    }

    @Test
    void nonFiniteSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> filters.gaussianTheory(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> filters.apply(sample.create(), "gaussian", Map.of("radius", "Infinity")));
    }
}
