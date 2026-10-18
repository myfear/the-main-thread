package com.themainthread.images;

import java.awt.image.BufferedImage;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.Set;

import javax.imageio.ImageIO;

/** A Java child process that exercises the MFLUX command boundary without model weights. */
public class RendererProcess {
    public static void main(String[] args) throws Exception {
        if (!"1".equals(System.getenv("HF_HUB_OFFLINE")) || System.getenv("HF_TOKEN") != null) {
            System.exit(2);
        }
        var options = new Properties();
        var accepted = Set.of("--model", "--prompt", "--width", "--height", "--seed", "--steps", "--output");
        for (int index = 0; index < args.length;) {
            String option = args[index++];
            int separator = option.indexOf('=');
            String value;
            if (separator >= 0) {
                value = option.substring(separator + 1);
                option = option.substring(0, separator);
            } else {
                value = args[index++];
            }
            if (!accepted.contains(option)) {
                System.exit(2);
            }
            options.setProperty(option, value);
        }
        Path output = Path.of(options.getProperty("--output"));
        Path directory = output.getParent();
        try (var channel = FileChannel.open(directory.resolve("active.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var lock = channel.tryLock()) {
            if (lock == null) {
                Files.writeString(directory.resolve("overlap"), "Two renderer processes overlapped");
                System.exit(3);
            }
            options.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
            try (var stream = Files.newOutputStream(directory.resolve("last-request.properties"))) {
                options.store(stream, "Renderer command arguments");
            }
            String prompt = options.getProperty("--prompt");
            if (prompt.equals("hold-image")) {
                Files.createFile(directory.resolve("entered"));
                while (!Files.exists(directory.resolve("release"))) {
                    Thread.sleep(20);
                }
            }
            if (prompt.equals("timeout-image")) {
                Thread.sleep(30_000);
            }
            if (prompt.equals("fail-backend")) {
                System.exit(7);
            }
            if (prompt.equals("bad-image")) {
                Files.writeString(output, "not a PNG");
                return;
            }
            ImageIO.write(new BufferedImage(Integer.parseInt(options.getProperty("--width")),
                    Integer.parseInt(options.getProperty("--height")), BufferedImage.TYPE_INT_RGB), "png", output.toFile());
        }
    }
}
