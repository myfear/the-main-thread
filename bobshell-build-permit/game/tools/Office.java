//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.21.5
//SOURCES ../lib/OfficeSupport.java

import java.nio.file.Path;

public class Office extends OfficeSupport {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: jbang tools/Office.java status|verify|issue");
        }
        Path root = Path.of("").toAbsolutePath();
        var result = switch (args[0]) {
            case "status" -> status(root);
            case "verify" -> verify(root);
            case "issue" -> issue(root);
            default -> throw new IllegalArgumentException("Unknown office command");
        };
        output(result);
        if (result.has("ok") && !result.path("ok").asBoolean()) {
            System.exit(1);
        }
    }
}
