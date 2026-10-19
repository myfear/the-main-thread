package com.themainthread.city;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CityJsonTest {
    @TempDir
    Path temp;

    @Test
    void completeSchemaRejectsVersionUnknownFieldsAndNegativeMetrics() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (var input = CityJson.class.getResourceAsStream("/schemas/software-city-v1.schema.json");
                var repository = new TestRepository(temp.resolve("repository"))) {
            var schema = factory.getSchema(input);
            repository.write("src/main/java/A.java", "class A {}");
            repository.commit();
            var document = new GitSnapshotAnalyzer().analyze(repository.root, "HEAD", temp.resolve("out.json"),
                    List.of(), List.of(), true);
            ObjectNode node = mapper.valueToTree(document);
            assertTrue(schema.validate(node).isEmpty());
            node.put("schemaVersion", "99");
            assertFalse(schema.validate(node).isEmpty());
            node = mapper.valueToTree(document);
            node.put("extra", "unexpected");
            assertFalse(schema.validate(node).isEmpty());
            node = mapper.valueToTree(document);
            ((ObjectNode) node.path("types").get(0).path("metrics").path("head")).put("fieldCount", -1);
            assertFalse(schema.validate(node).isEmpty());
        }
    }

    @Test
    void semanticValidationRejectsDuplicateIdsAndInconsistentSummary() throws Exception {
        var json = new CityJson(new ObjectMapper());
        try (var repository = new TestRepository(temp.resolve("repository"))) {
            repository.write("src/main/java/A.java", "class A {}");
            repository.commit();
            var document = new GitSnapshotAnalyzer().analyze(repository.root, "HEAD", temp.resolve("out.json"),
                    List.of(), List.of(), true);
            var duplicate = new CityDocument("1", "snapshot", document.repository(), document.revisions(),
                    List.of(document.types().getFirst(), document.types().getFirst()), List.of(), document.summary());
            assertThrows(IOException.class, () -> json.serialize(duplicate));
            var incorrect = new CityDocument("1", "snapshot", document.repository(), document.revisions(),
                    document.types(), List.of(), new CityDocument.Summary(0, 999, 0, 0, 0, 999, false));
            assertThrows(IOException.class, () -> json.serialize(incorrect));
        }
    }
}
