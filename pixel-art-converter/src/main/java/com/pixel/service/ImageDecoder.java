package com.pixel.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageInputStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class ImageDecoder {
    private final ImageLimits limits;

    public ImageDecoder(ImageLimits limits) {
        this.limits = limits;
    }

    public BufferedImage read(Path file, int blockSize) {
        try (var input = new FileImageInputStream(file.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BadRequestException("Upload a PNG or JPEG image");
            }
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg")) {
                    throw new BadRequestException("Upload a PNG or JPEG image");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > limits.maxPixels()) {
                    throw new WebApplicationException("Image exceeds the decoded pixel limit", 413);
                }
                if (blockSize > Math.min(width, height)) {
                    throw new BadRequestException("blockSize must fit both image dimensions");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadRequestException("Image data could not be decoded");
        }
    }
}
