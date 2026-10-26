import java.nio.file.Files;
import java.nio.file.Path;

/** Java source launcher: no dependencies, secrets or global configuration edits. */
class ConfigureHuginn {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toRealPath();
        Path target = root.resolve(".bob/mcp.json");
        if (Files.exists(target)) throw new IllegalStateException("Existing .bob/mcp.json; inspect it before changing configuration");
        String project = root.resolve("sample-app").toRealPath().toString().replace("\\", "\\\\").replace("\"", "\\\"");
        String json = Files.readString(root.resolve(".bob/mcp.json.example"))
                .replace("/absolute/path/to/quarkus-vex-investigator/sample-app", project);
        Files.writeString(target, json);
    }
}
