package dev.themainthread.wallet;

import de.brendamour.jpasskit.PKBarcode;
import de.brendamour.jpasskit.PKField;
import de.brendamour.jpasskit.PKPass;
import de.brendamour.jpasskit.enums.PKBarcodeFormat;
import de.brendamour.jpasskit.passes.PKGenericPass;
import jakarta.enterprise.context.ApplicationScoped;
import java.awt.Color;
import java.io.IOException;
import java.nio.charset.Charset;

@ApplicationScoped
public class PassBuilderService {

    private final PassConfig passConfig;
    private final PassJsonWriter passJsonWriter;

    PassBuilderService(PassConfig passConfig, PassJsonWriter passJsonWriter) {
        this.passConfig = passConfig;
        this.passJsonWriter = passJsonWriter;
    }

    public String buildPassJson(String memberId, String memberName, String tier) throws IOException {
        PKPass pass = PKPass.builder()
                .pass(PKGenericPass.builder()
                        .primaryFieldBuilder(PKField.builder()
                                .key("member-name")
                                .label("MEMBER")
                                .value(memberName))
                        .secondaryFieldBuilder(PKField.builder()
                                .key("tier")
                                .label("TIER")
                                .value(tier))
                        .auxiliaryFieldBuilder(PKField.builder()
                                .key("member-id")
                                .label("MEMBER ID")
                                .value(memberId))
                        .backFieldBuilder(PKField.builder()
                                .key("website")
                                .label("THE MAIN THREAD")
                                .value("https://www.the-main-thread.com/"))
                        .backFieldBuilder(PKField.builder()
                                .key("about")
                                .label("A HIGHER STANDARD FOR BUILDERS")
                                .value("Ideas. People. Better software. Ideas into progress."))
                        .backFieldBuilder(PKField.builder()
                                .key("demo")
                                .label("ABOUT THIS CARD")
                                .value("Tutorial demonstration. Not proof of a paid subscription or access entitlement.")))
                .barcodeBuilder(PKBarcode.builder()
                        .format(PKBarcodeFormat.PKBarcodeFormatQR)
                        .message(memberId)
                        .altText(memberId)
                        .messageEncoding(Charset.forName("utf-8")))
                .formatVersion(1)
                .passTypeIdentifier(passConfig.typeIdentifier())
                .serialNumber(memberId)
                .teamIdentifier(passConfig.teamIdentifier())
                .organizationName(passConfig.orgName())
                .description("The Main Thread Membership Card")
                .backgroundColor(Color.decode("#22272B"))
                .foregroundColor("rgb(245, 242, 235)")
                .labelColor("rgb(214, 173, 112)")
                .build();

        return passJsonWriter.writePass(pass);
    }
}
