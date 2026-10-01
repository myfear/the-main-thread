import com.fasterxml.jackson.databind.JsonNode;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

class Replay {
    static void render(Path state, boolean escaped) throws Exception {
        List<JsonNode> events = new ArrayList<>();
        for (String line : Files.readAllLines(state.resolve("events.jsonl"))) {
            events.add(AirlockHook.JSON.readTree(line));
        }
        int height = 240 + events.size() * 112;
        var image = new BufferedImage(1100, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = image.createGraphics();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setColor(new Color(13, 25, 37));
        canvas.fillRect(0, 0, 1100, height);
        canvas.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        canvas.setColor(new Color(128, 215, 195));
        canvas.drawString("THE MAIN THREAD   /   JBANG + IBM BOB", 56, 48);
        canvas.setColor(Color.WHITE);
        canvas.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
        canvas.drawString(escaped ? "The airlock is open." : "Still in the airlock.", 56, 108);
        canvas.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        canvas.setColor(new Color(185, 198, 211));
        canvas.drawString("Flight recorder: decisions and results from the Java hooks", 56, 148);
        int y = 190;
        for (int index = 0; index < events.size(); index++) {
            JsonNode event = events.get(index);
            canvas.setColor(new Color(28, 44, 60));
            canvas.fillRoundRect(48, y, 1004, 96, 16, 16);
            canvas.setColor(new Color(255, 207, 119));
            canvas.setFont(new Font(Font.MONOSPACED, Font.BOLD, 17));
            canvas.drawString(String.format("%02d  %s", index + 1, event.path("action").asText()), 72, y + 29);
            canvas.setColor(new Color(227, 234, 241));
            canvas.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 19));
            String detail = event.path("detail").asText().lines().findFirst().orElse("");
            while (canvas.getFontMetrics().stringWidth(detail) > 940) {
                detail = detail.substring(0, detail.length() - 2) + "…";
            }
            canvas.drawString(detail, 72, y + 63);
            y += 112;
        }
        canvas.dispose();
        ImageIO.write(image, "png", state.resolve("replay.png").toFile());
        StringBuilder rows = new StringBuilder();
        for (JsonNode event : events) {
            rows.append("<li><h2>").append(html(event.path("action").asText())).append("</h2><pre>")
                    .append(html(event.path("detail").asText())).append("</pre></li>");
        }
        Files.writeString(state.resolve("replay.html"), """
                <!doctype html><html lang="en"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Airlock flight recorder</title>
                <style>body{max-width:1050px;margin:40px auto;padding:20px;background:#0d1925;color:#e3eaf1;
                font:18px/1.5 system-ui}img{width:100%%}pre{white-space:pre-wrap;overflow-wrap:anywhere}
                li{margin-bottom:24px}h2{font-size:20px}</style>
                <h1>Airlock flight recorder</h1><img src="replay.png" alt="%s">
                <h2>Full event details</h2><ol>%s</ol></html>
                """.formatted(escaped ? "Mission complete; chronological hook decisions follow." : "Mission incomplete.", rows));
    }

    static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
