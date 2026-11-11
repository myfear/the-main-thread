package com.themainthread.filterlab;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExifOrientationTest {

    @Test
    void readsTheOrientationTagFromJpegMetadata() {
        assertEquals(6, ExifOrientation.read(Path.of("src/test/resources/images/oriented.jpg")));
    }

    @ParameterizedTest
    @CsvSource({ "1, 3, 2, 123456", "2, 3, 2, 321654", "3, 3, 2, 654321", "4, 3, 2, 456123",
            "5, 2, 3, 142536", "6, 2, 3, 415263", "7, 2, 3, 635241", "8, 2, 3, 362514" })
    void mapsAllOrientationsOnANonSquareCanvas(int orientation, int width, int height, String order) {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 3, 2, new int[] { 1, 2, 3, 4, 5, 6 }, 0, 3);
        BufferedImage result = ExifOrientation.apply(source, orientation);
        assertEquals(width, result.getWidth());
        assertEquals(height, result.getHeight());
        assertArrayEquals(order.chars().map(character -> character - '0').toArray(),
                result.getRGB(0, 0, width, height, null, 0, width));
    }
}
