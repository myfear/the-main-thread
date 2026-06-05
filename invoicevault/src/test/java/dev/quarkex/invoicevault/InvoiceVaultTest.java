package dev.quarkex.invoicevault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTest
class InvoiceVaultTest {

    @Inject
    InvoiceService invoiceService;

    @Inject
    S3Client s3;

    @Inject
    SqsClient sqs;

    @Inject
    SsmClient ssm;

    /**
     * Podman often cannot bind-mount init scripts into Dev Services containers; seed SSM/SQS here instead.
     */
    @BeforeAll
    void seedAwsResources() {
        try {
            ssm.getParameter(r -> r.name(InvoiceService.QUEUE_URL_PARAM));
            return;
        } catch (ParameterNotFoundException ignored) {
            // seed below
        }

        String queueUrl = sqs.createQueue(CreateQueueRequest.builder().queueName("invoices-received").build())
                .queueUrl();

        ssm.putParameter(PutParameterRequest.builder()
                .name("/invoicevault/customers/cust-001/max-size-mb")
                .value("10")
                .type(ParameterType.STRING)
                .overwrite(true)
                .build());

        ssm.putParameter(PutParameterRequest.builder()
                .name(InvoiceService.QUEUE_URL_PARAM)
                .value(queueUrl)
                .type(ParameterType.STRING)
                .overwrite(true)
                .build());
    }

    @Test
    void shouldStoreInvoicePublishEventAndEnforceMaxSize() throws IOException {
        byte[] pdf;
        try (InputStream in = getClass().getResourceAsStream("/sample-invoice.pdf")) {
            assertThat(in).isNotNull();
            pdf = in.readAllBytes();
        }

        String key = invoiceService.store("INV-TEST-001", pdf, "cust-001");
        assertThat(key).isEqualTo("cust-001/INV-TEST-001.pdf");

        try (ResponseInputStream<GetObjectResponse> object = s3.getObject(
                GetObjectRequest.builder().bucket(InvoiceService.BUCKET).key(key).build())) {
            assertThat(object.readAllBytes()).isEqualTo(pdf);
        }

        String queueUrl = ssm.getParameter(r -> r.name(InvoiceService.QUEUE_URL_PARAM))
                .parameter()
                .value();
        List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(2)
                .build())
                .messages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).body()).contains("INV-TEST-001").contains("cust-001").contains(key);

        byte[] tooLarge = new byte[11 * 1024 * 1024];
        assertThatThrownBy(() -> invoiceService.store("INV-TOO-LARGE", tooLarge, "cust-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max size");
    }
}