//JAVA 21+
//DEPS com.fasterxml.jackson.core:jackson-databind:2.21.5

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class Reception {
    public static void main(String[] args) throws IOException {
        byte[] input = System.in.readNBytes(1024 * 1024 + 1);
        if (input.length > 1024 * 1024) {
            throw new IOException("Hook input exceeds 1 MiB");
        }
        var json = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        var event = json.readTree(input);
        if (event == null || !event.isObject()) {
            throw new IOException("Expected an event object");
        }
        if (!event.path("prompt").asText().stripLeading()
                .startsWith("CASE: BUILD-001")) {
            System.err.println(
                    "Reception requires CASE: BUILD-001 at the start of your prompt.");
            System.exit(2);
        }
    }
}
