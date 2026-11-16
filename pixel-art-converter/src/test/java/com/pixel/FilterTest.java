package com.pixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.pixel.filter.impl.DownsampleFilter;
import com.pixel.filter.impl.FloydSteinbergFilter;
import com.pixel.filter.impl.UpsampleFilter;

class FilterTest {
    @Test
    void samplesTheTopLeftPixelAndRepeatsItDuringUpscaling() {
        var input = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        input.setRGB(0, 0, 0x123456);
        input.setRGB(2, 2, 0xABCDEF);
        var small = new DownsampleFilter(2).apply(input);
        assertEquals(2, small.getWidth());
        assertEquals(0xFF123456, small.getRGB(0, 0));
        var large = new UpsampleFilter(2).apply(small);
        assertEquals(4, large.getWidth());
        assertEquals(0xFFABCDEF, large.getRGB(3, 3));
    }

    @Test
    void dithersWithoutChangingTheInputAndUsesTheDocumentedPalette() {
        var input = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                input.setRGB(x, y, 0x8C8C8C);
            }
        }
        var result = new FloydSteinbergFilter().apply(input);
        Set<Integer> shades = new HashSet<>();
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                int shade = result.getRGB(x, y) & 255;
                shades.add(shade);
                assertTrue(shade % 32 == 0 || shade == 255);
                assertEquals(0xFF8C8C8C, input.getRGB(x, y));
            }
        }
        assertEquals(Set.of(128, 160), shades);
    }
}
