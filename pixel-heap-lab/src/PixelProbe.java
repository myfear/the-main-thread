import java.lang.ref.Reference;
import java.util.HexFormat;

public final class PixelProbe {
    record Pixel(int argb) {}

    value record ValuePixel(int argb) {}

    static final int HEAP_EXHAUSTED = 42;
    private static final long SEED = 0xcbf29ce484222325L;

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: PixelProbe <int|record|value|object> <count>");
        }
        int count = Integer.parseInt(args[1]);
        if (count < 0) {
            throw new IllegalArgumentException("Pixel count must be nonnegative");
        }

        long checksum;
        try {
            checksum = measure(args[0], count);
        } catch (OutOfMemoryError failure) {
            String detail = failure.getMessage();
            if ("Java heap space".equals(detail) || (detail != null && detail.startsWith("Java heap space:"))
                    || "GC overhead limit exceeded".equals(detail)) {
                System.exit(HEAP_EXHAUSTED);
                return;
            }
            throw failure;
        }
        System.out.println("FIT " + count + " " + HexFormat.of().toHexDigits(checksum));
    }

    private static long measure(String layout, int count) {
        Object pixels = switch (layout) {
            case "int" -> new int[count];
            case "record" -> new Pixel[count];
            case "value" -> new ValuePixel[count];
            case "object" -> new Object[count];
            default -> throw new IllegalArgumentException("Unknown layout: " + layout);
        };

        long hash = SEED;
        switch (pixels) {
            case int[] packed -> {
                for (int i = 0; i < count; i++) {
                    packed[i] = argb(i);
                }
                for (int pixel : packed) {
                    hash = mix(hash, pixel);
                }
            }
            case Pixel[] records -> {
                for (int i = 0; i < count; i++) {
                    records[i] = new Pixel(argb(i));
                }
                for (Pixel pixel : records) {
                    hash = mix(hash, pixel.argb());
                }
            }
            case ValuePixel[] values -> {
                for (int i = 0; i < count; i++) {
                    values[i] = new ValuePixel(argb(i));
                }
                for (ValuePixel pixel : values) {
                    hash = mix(hash, pixel.argb());
                }
            }
            case Object[] objects -> {
                for (int i = 0; i < count; i++) {
                    objects[i] = new ValuePixel(argb(i));
                }
                for (Object pixel : objects) {
                    hash = mix(hash, ((ValuePixel) pixel).argb());
                }
            }
            default -> throw new AssertionError("Unexpected array type");
        }
        Reference.reachabilityFence(pixels);
        return hash;
    }

    private static long mix(long hash, int argb) {
        return (hash ^ Integer.toUnsignedLong(argb)) * 0x100000001b3L;
    }

    // A 1024 × 1024 tile. Computing each color on demand keeps a source raster off the heap.
    static int argb(int index) {
        int x = index & 1023;
        int y = (index >>> 10) & 1023;
        int sunX = x - 690;
        if (y >= 640) {
            int depth = y - 640;
            int ripple = ridge(y * 5, 32) - 16;
            if (Math.abs(sunX + ripple) < 25 + (depth >> 3) && (y & 15) < 5) {
                return rgb(238, 166 - (depth >> 3), 116);
            }
            int light = (383 - depth) >> 3;
            return rgb(23 + light, 39 + light, 64 + light);
        }
        if (y >= 530 + (ridge(x + 43, 128) >> 1) + (ridge(x + 31, 64) >> 2)) {
            return rgb(49, 46, 77);
        }
        if (y >= 418 + (ridge(x + 80, 256) >> 1) + (ridge(x, 64) >> 2)) {
            return rgb(110, 72, 103);
        }
        int sunY = y - 350;
        if (sunX * sunX + sunY * sunY <= 100 * 100) {
            return rgb(255, 207, 141);
        }
        return rgb(45 + (y >> 2), 32 + (y >> 3), 87 + (y >> 4));
    }

    private static int ridge(int coordinate, int halfWidth) {
        return Math.abs((coordinate & (2 * halfWidth - 1)) - halfWidth);
    }

    private static int rgb(int red, int green, int blue) {
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }
}
