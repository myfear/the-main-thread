package com.themainthread.filterlab;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.NodeList;

final class ExifOrientation {

    private ExifOrientation() {
    }

    static int read(Path path) {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                return 1;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return 1;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, false);
                return read(reader);
            }
            finally {
                reader.dispose();
            }
        }
        catch (IOException | RuntimeException exception) {
            return 1;
        }
    }

    static int read(ImageReader reader) {
        try {
            IIOMetadata metadata = reader.getImageMetadata(0);
            if (metadata == null) {
                return 1;
            }
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_jpeg_image_1.0");
            NodeList markers = root.getElementsByTagName("unknown");
            for (int i = 0; i < markers.getLength(); i++) {
                IIOMetadataNode marker = (IIOMetadataNode) markers.item(i);
                if (!"225".equals(marker.getAttribute("MarkerTag"))) {
                    continue;
                }
                Object userObject = marker.getUserObject();
                if (userObject instanceof byte[] bytes) {
                    int orientation = parse(bytes);
                    if (orientation != 1) {
                        return orientation;
                    }
                }
            }
            return 1;
        }
        catch (IOException | RuntimeException exception) {
            return 1;
        }
    }

    private static int parse(byte[] bytes) {
        if (bytes.length < 14 || bytes[0] != 'E' || bytes[1] != 'x' || bytes[2] != 'i' || bytes[3] != 'f') {
            return 1;
        }
        int tiff = 6;
        boolean littleEndian = bytes[tiff] == 'I' && bytes[tiff + 1] == 'I';
        int ifdOffset = tiff + readInt(bytes, tiff + 4, littleEndian);
        if (ifdOffset < 0 || ifdOffset + 2 > bytes.length) {
            return 1;
        }
        int entries = readShort(bytes, ifdOffset, littleEndian);
        for (int i = 0; i < entries; i++) {
            int entry = ifdOffset + 2 + i * 12;
            if (entry + 12 > bytes.length) {
                return 1;
            }
            int tag = readShort(bytes, entry, littleEndian);
            if (tag != 0x0112) {
                continue;
            }
            int type = readShort(bytes, entry + 2, littleEndian);
            int count = readInt(bytes, entry + 4, littleEndian);
            if (type != 3 || count < 1) {
                return 1;
            }
            int value = readShort(bytes, entry + 8, littleEndian);
            return value >= 1 && value <= 8 ? value : 1;
        }
        return 1;
    }

    static BufferedImage apply(BufferedImage source, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swap = orientation >= 5;
        int outWidth = swap ? height : width;
        int outHeight = swap ? width : height;
        int[] input = source.getRGB(0, 0, width, height, null, 0, width);
        int[] output = new int[outWidth * outHeight];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int targetX;
                int targetY;
                switch (orientation) {
                    case 2 -> {
                        targetX = width - 1 - x;
                        targetY = y;
                    }
                    case 3 -> {
                        targetX = width - 1 - x;
                        targetY = height - 1 - y;
                    }
                    case 4 -> {
                        targetX = x;
                        targetY = height - 1 - y;
                    }
                    case 5 -> {
                        targetX = y;
                        targetY = x;
                    }
                    case 6 -> {
                        targetX = height - 1 - y;
                        targetY = x;
                    }
                    case 7 -> {
                        targetX = height - 1 - y;
                        targetY = width - 1 - x;
                    }
                    case 8 -> {
                        targetX = y;
                        targetY = width - 1 - x;
                    }
                    default -> {
                        targetX = x;
                        targetY = y;
                    }
                }
                output[targetY * outWidth + targetX] = input[y * width + x];
            }
        }

        BufferedImage result = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, outWidth, outHeight, output, 0, outWidth);
        return result;
    }

    private static int readShort(byte[] bytes, int offset, boolean littleEndian) {
        if (littleEndian) {
            return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
        }
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static int readInt(byte[] bytes, int offset, boolean littleEndian) {
        if (littleEndian) {
            return (bytes[offset] & 0xff)
                    | ((bytes[offset + 1] & 0xff) << 8)
                    | ((bytes[offset + 2] & 0xff) << 16)
                    | ((bytes[offset + 3] & 0xff) << 24);
        }
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }
}
