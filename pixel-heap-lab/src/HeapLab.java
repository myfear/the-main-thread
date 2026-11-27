import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class HeapLab {
    private static final List<String> MODES = List.of("int", "record", "value", "object");
    private static final int MEBIBYTE = 1024 * 1024;
    private static final int STEP = 65_536;
    private static final int CHECK_PIXELS = 1024 * 1024;
    private static final String CHECK_IMAGE_HASH = "9add7cc805989d47";

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 1 && args[0].equals("check")) {
            check();
            return;
        }
        if (args.length == 1 && args[0].equals("measure")) {
            for (String mode : MODES) {
                measure(mode);
            }
            return;
        }
        if (args.length == 2 && args[0].equals("measure") && MODES.contains(args[1])) {
            measure(args[1]);
            return;
        }
        if (args.length == 3 && args[0].equals("try") && MODES.contains(args[1])) {
            int count = parseCount(args[2]);
            if (count > 0) {
                Optional<String> checksum = probe(args[1], count);
                if (checksum.isPresent()) {
                    System.out.printf("FIT %s %d %s%n", args[1], count, checksum.get());
                } else {
                    System.out.printf("NO_FIT %s %d%n", args[1], count);
                    System.exit(PixelProbe.HEAP_EXHAUSTED);
                }
                return;
            }
        }
        System.err.println("Usage: HeapLab check | measure [mode] | try <mode> <positive-count>");
        System.err.println("Modes: int, record, value, object");
        System.exit(2);
    }

    private static int parseCount(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void check() throws IOException, InterruptedException {
        for (String mode : MODES) {
            String checksum = probe(mode, CHECK_PIXELS)
                    .orElseThrow(() -> new IllegalStateException(mode + " could not store the check image"));
            System.out.printf("%s pixels=%d checksum=%s%n", mode, CHECK_PIXELS, checksum);
            if (!CHECK_IMAGE_HASH.equals(checksum)) {
                throw new IllegalStateException(mode + " disagrees with the 1024 × 1024 reference image");
            }
        }
    }

    private static void measure(String mode) throws IOException, InterruptedException {
        int fit = 1;
        // Every representation needs at least four bytes per pixel. Verify both endpoints.
        int exhausted = (64 * MEBIBYTE / Integer.BYTES) / STEP + 1;
        if (probe(mode, fit * STEP).isEmpty() || probe(mode, exhausted * STEP).isPresent()) {
            throw new IllegalStateException("Could not bracket heap capacity for " + mode);
        }
        while (exhausted - fit > 1) {
            int middle = (fit + exhausted) / 2;
            if (probe(mode, middle * STEP).isPresent()) {
                fit = middle;
            } else {
                exhausted = middle;
            }
        }
        System.out.printf("%s fit=%d next_failed=%d step=%d%n", mode, fit * STEP, exhausted * STEP, STEP);
    }

    private static Optional<String> probe(String mode, int count) throws IOException, InterruptedException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "--enable-preview", "-Xms64m", "-Xmx64m",
                "-XX:+UseG1GC", "-XX:ActiveProcessorCount=2", "-cp", System.getProperty("java.class.path"),
                "PixelProbe", mode, Integer.toString(count));
        // The measuring JVM should receive the same heap options on every launch.
        builder.environment().keySet().removeAll(List.of("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS"));
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process child = builder.start();
        String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        int status = child.waitFor();
        if (status == PixelProbe.HEAP_EXHAUSTED) {
            return Optional.empty();
        }
        String prefix = "FIT " + count + " ";
        if (status != 0 || !output.startsWith(prefix) || !output.substring(prefix.length()).matches("[0-9a-f]{16}")) {
            throw new IOException("Unexpected probe result for " + mode + " at " + count
                    + " pixels (exit " + status + "): " + output);
        }
        return Optional.of(output.substring(prefix.length()));
    }
}
