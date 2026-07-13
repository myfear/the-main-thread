package com.themainthread.ledgerlock;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.jboss.logging.Logger;

import io.quarkus.vault.VaultTOTPSecretEngine;
import io.quarkus.vault.client.VaultClientException;
import io.quarkus.vault.secrets.totp.CreateKeyParameters;
import io.quarkus.vault.secrets.totp.KeyDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class TotpService {

    private static final Logger LOG = Logger.getLogger(TotpService.class);

    private final VaultTOTPSecretEngine totpEngine;

    public TotpService(VaultTOTPSecretEngine totpEngine) {
        this.totpEngine = totpEngine;
    }

    @Transactional
    public EnrollmentResponse enroll(String subject) {
        if (TotpEnrollment.findById(subject) != null) {
            throw new ClientErrorException("TOTP is already enrolled", Response.Status.CONFLICT);
        }

        String vaultKey = "user-" + UUID.randomUUID();
        CreateKeyParameters parameters = new CreateKeyParameters("LedgerLock", subject);
        parameters.setExported(true);
        parameters.setPeriod("30s");
        parameters.setAlgorithm("SHA1");
        parameters.setDigits(6);
        parameters.setSkew(1);
        parameters.setQrSize(240);

        Optional<KeyDefinition> created = totpEngine.createKey(vaultKey, parameters);
        KeyDefinition definition = created.orElseThrow(
                () -> new IllegalStateException("Vault did not export the TOTP enrollment data"));

        TotpEnrollment enrollment = new TotpEnrollment();
        enrollment.subject = subject;
        enrollment.vaultKey = vaultKey;
        enrollment.createdAt = Instant.now();

        try {
            enrollment.persistAndFlush();
        } catch (PersistenceException failure) {
            deleteCompensatingKey(vaultKey);
            throw new ClientErrorException("TOTP enrollment already exists", Response.Status.CONFLICT, failure);
        }

        return new EnrollmentResponse(
                "data:image/png;base64," + definition.getBarcode(),
                definition.getUrl());
    }

    public boolean validate(String subject, String code) {
        TotpEnrollment enrollment = TotpEnrollment.findById(subject);
        if (enrollment == null) {
            return false;
        }

        try {
            return totpEngine.validateCode(enrollment.vaultKey, code);
        } catch (VaultClientException failure) {
            if (failure.getStatus() == 400 && failure.hasErrorContaining("code already used")) {
                return false;
            }
            throw failure;
        }
    }

    private void deleteCompensatingKey(String vaultKey) {
        try {
            totpEngine.deleteKey(vaultKey);
        } catch (RuntimeException cleanupFailure) {
            LOG.warnf(cleanupFailure, "Could not remove orphaned Vault TOTP key %s", vaultKey);
        }
    }

    public record EnrollmentResponse(String qrCodeDataUrl, String manualEntryUri) {
    }
}
