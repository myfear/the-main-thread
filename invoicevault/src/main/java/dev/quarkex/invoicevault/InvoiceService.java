package dev.quarkex.invoicevault;

import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

@ApplicationScoped
public class InvoiceService {

    private static final Logger LOG = Logger.getLogger(InvoiceService.class);

    static final String BUCKET = "invoices";
    static final String QUEUE_URL_PARAM = "/invoicevault/sqs-url";

    @Inject
    S3Client s3;

    @Inject
    SqsClient sqs;

    @Inject
    SsmClient ssm;

    @PostConstruct
    void ensureBucket() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
            LOG.infof("Created S3 bucket %s", BUCKET);
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            LOG.debugf("S3 bucket %s already exists", BUCKET);
        }
    }

    public String store(String invoiceId, byte[] pdf, String customerId) {
        int maxSizeMb = readMaxSizeMb(customerId);
        long maxBytes = (long) maxSizeMb * 1024 * 1024;
        if (pdf.length > maxBytes) {
            throw new IllegalArgumentException(
                    "Invoice exceeds max size of " + maxSizeMb + " MB for customer " + customerId);
        }

        String key = customerId + "/" + invoiceId + ".pdf";
        s3.putObject(
                PutObjectRequest.builder().bucket(BUCKET).key(key).contentType("application/pdf").build(),
                RequestBody.fromBytes(pdf));

        String queueUrl = ssm.getParameter(r -> r.name(QUEUE_URL_PARAM)).parameter().value();
        String body = """
                {"invoiceId":"%s","customerId":"%s","key":"%s"}
                """.formatted(invoiceId, customerId, key).strip();
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(body).build());

        return key;
    }

    private int readMaxSizeMb(String customerId) {
        String paramName = "/invoicevault/customers/" + customerId + "/max-size-mb";
        try {
            return Integer.parseInt(ssm.getParameter(r -> r.name(paramName)).parameter().value());
        } catch (ParameterNotFoundException e) {
            throw new IllegalStateException("Missing SSM parameter " + paramName, e);
        }
    }
}