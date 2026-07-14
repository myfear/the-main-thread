package com.themainthread.progress.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

import com.themainthread.progress.api.ImportRejectedException;
import com.themainthread.progress.config.ProgressConfig;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@ApplicationScoped
public class FileStorage {

    private static final Logger LOG = Logger.getLogger(FileStorage.class);

    private final Path stagingDirectory;

    public FileStorage(ProgressConfig config) {
        this.stagingDirectory = Path.of(config.stagingDirectory()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void createStagingDirectory() {
        try {
            Files.createDirectories(stagingDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create staging directory " + stagingDirectory, e);
        }
    }

    public StagedFile stage(FileUpload upload, UUID id) {
        if (upload == null) {
            throw new ImportRejectedException("Choose a non-empty CSV file");
        }
        long size = upload.size();
        if (size == 0) {
            throw new ImportRejectedException("Choose a non-empty CSV file");
        }

        String originalName = safeFileName(upload.fileName());
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ImportRejectedException("Only CSV files are accepted");
        }

        Path destination = stagingDirectory.resolve(id + ".csv");
        try {
            Files.move(upload.uploadedFile(), destination, StandardCopyOption.REPLACE_EXISTING);
            return new StagedFile(originalName, destination, size);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot move uploaded file into staging", e);
        }
    }

    public void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warnf(e, "Could not delete staged file %s; a cleanup job must remove it", path);
        }
    }

    private String safeFileName(String suppliedName) {
        try {
            Path fileName = Path.of(suppliedName).getFileName();
            if (fileName == null || fileName.toString().isBlank()) {
                throw new ImportRejectedException("The uploaded file needs a name");
            }
            return fileName.toString();
        } catch (RuntimeException e) {
            if (e instanceof ImportRejectedException rejected) {
                throw rejected;
            }
            throw new ImportRejectedException("The uploaded file name is invalid");
        }
    }

    public record StagedFile(String originalName, Path path, long size) {
    }
}
