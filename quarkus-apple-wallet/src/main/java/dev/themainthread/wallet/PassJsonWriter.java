package dev.themainthread.wallet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.brendamour.jpasskit.PKPass;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;

@ApplicationScoped
public class PassJsonWriter {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public String writePass(PKPass pass) throws IOException {
        ObjectNode document = objectMapper.valueToTree(pass);
        ObjectNode generic = (ObjectNode) document.get("generic");

        // jPasskit 0.5.7 models the legacy style, but not iOS 27's posterGeneric dictionary.
        ObjectNode poster = document.putObject("posterGeneric");
        poster.set("footerFields", generic.get("auxiliaryFields"));
        ArrayNode primary = poster.putArray("primaryFields");
        primary.addAll((ArrayNode) generic.get("primaryFields"));
        primary.addAll((ArrayNode) generic.get("secondaryFields"));
        poster.set("backFields", generic.get("backFields"));
        document.put("suppressHeaderDarkening", true);
        return objectMapper.writeValueAsString(document);
    }
}
