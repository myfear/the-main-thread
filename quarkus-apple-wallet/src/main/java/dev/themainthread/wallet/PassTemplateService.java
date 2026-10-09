package dev.themainthread.wallet;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
public class PassTemplateService {

    private static final String BUNDLE = "TheMainThread.pkpasstemplate/";
    private static final List<String> IMAGES = List.of(
            "icon.png", "icon@2x.png", "icon@3x.png",
            "logo.png", "logo@2x.png", "logo@3x.png",
            "primaryLogo@2x.png", "primaryLogo@3x.png",
            "artwork@2x.png", "artwork@3x.png");

    private final PassBuilderService passBuilderService;

    PassTemplateService(PassBuilderService passBuilderService) {
        this.passBuilderService = passBuilderService;
    }

    public byte[] createTemplate(String memberId, String memberName, String tier) throws IOException {
        String passJson = passBuilderService.buildPassJson(memberId, memberName, tier);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(BUNDLE + "pass.json"));
            zip.write(passJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            for (String image : IMAGES) {
                try (InputStream input = getClass().getClassLoader().getResourceAsStream("pass-template/" + image)) {
                    if (input == null) {
                        throw new FileNotFoundException("Missing pass image: " + image);
                    }
                    zip.putNextEntry(new ZipEntry(BUNDLE + image));
                    input.transferTo(zip);
                    zip.closeEntry();
                }
            }
        }
        return output.toByteArray();
    }
}
