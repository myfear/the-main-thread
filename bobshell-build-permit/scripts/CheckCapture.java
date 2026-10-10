//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.21.5
//SOURCES Hunt.java ../game/lib/OfficeSupport.java

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** Links the native screenshot to a denied tool call and real resumed compaction. */
public class CheckCapture extends OfficeSupport {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: CheckCapture.java SESSION_ID images/bobshell-stale-stamp.png");
        }
        Path kit = Hunt.kit();
        Path root = kit.resolve("workspace");
        Path screenshot = kit.resolve(args[1]);
        var image = ImageIO.read(screenshot.toFile());
        if (image == null || image.getWidth() < 1200 || image.getHeight() < 600) {
            throw new IllegalStateException("Capture must be a readable high-resolution image");
        }
        var entries = events(root).stream().filter(row -> row.path("session").asText().equals(args[0])).toList();
        var denial = entries.stream().filter(row -> row.path("event").asText().equals("PreToolUse")
                && row.path("decision").asText().equals("deny")
                && row.path("reason").asText().contains("earlier version"))
                .reduce((first, second) -> second).orElseThrow();
        boolean neverExecuted = entries.stream().noneMatch(row -> row.path("event").asText().equals("PostToolUse")
                && row.path("toolUseId").asText().equals(denial.path("toolUseId").asText()));
        int pre = -1;
        int post = -1;
        int start = -1;
        for (int index = 0; index < entries.size(); index++) {
            String event = entries.get(index).path("event").asText();
            if (event.equals("PreCompact")) {
                pre = index;
            }
            if (pre >= 0 && event.equals("PostCompact")) {
                post = index;
            }
            if (post >= 0 && event.equals("SessionStart")) {
                start = index;
            }
        }
        boolean compaction = pre >= 0 && post > pre && start > post;
        boolean sameCase = runId(root).equals(read(root.resolve(".office/checkpoint.json")).path("runId").asText());
        boolean stillStale = refusal(root) != null && refusal(root).contains("earlier version");
        boolean ok = neverExecuted && compaction && sameCase && stillStale;
        var receipt = record().put("ok", ok).put("bobshell", "2.0.5").put("session", args[0])
                .put("runId", runId(root)).put("nativeScreenshot", args[1])
                .put("width", image.getWidth()).put("height", image.getHeight())
                .put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(screenshot))))
                .put("deniedCallNeverExecuted", neverExecuted).put("compactionSequencePassed", compaction)
                .put("checkpointPreservedRun", sameCase).put("stampStillStaleAfterCompaction", stillStale);
        receipt.set("deniedCall", denial);
        receipt.set("compactionEvents", JSON.valueToTree(entries.stream()
                .filter(row -> java.util.List.of("PreCompact", "PostCompact", "SessionStart")
                        .contains(row.path("event").asText())).toList()));
        write(kit.resolve("evidence/interactive-capture.json"), receipt);
        output(receipt);
        if (!ok) {
            throw new IllegalStateException("Interactive capture/compaction checks failed");
        }
    }
}
