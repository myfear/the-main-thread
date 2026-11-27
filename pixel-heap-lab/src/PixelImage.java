import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Renders one tile in its own JVM so image buffers never enter a capacity probe. */
public class PixelImage {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: PixelImage <output.png>");
        }
        int width = 1024;
        BufferedImage tile = new BufferedImage(width, width, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < width; x++) {
                tile.setRGB(x, y, PixelProbe.argb(y * width + x));
            }
        }
        ImageIO.write(tile, "png", new File(args[0]));
    }
}
