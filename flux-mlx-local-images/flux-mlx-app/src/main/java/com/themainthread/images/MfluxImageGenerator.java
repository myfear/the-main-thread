package com.themainthread.images;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MfluxImageGenerator {
    private final ImageConfig config;

    public MfluxImageGenerator(ImageConfig config) {
        this.config = config;
    }

    public void generate(JobView job, Path output) throws IOException, InterruptedException {
        var command = new ArrayList<>(config.command());
        command.addAll(List.of("--model", job.model(), "--prompt=" + job.prompt(),
                "--width", Integer.toString(job.width()), "--height", Integer.toString(job.height()),
                "--seed", Long.toString(job.seed()), "--steps", Integer.toString(job.steps()),
                "--output", output.toAbsolutePath().toString()));
        Path log = output.resolveSibling(job.id() + ".log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("HF_HUB_OFFLINE", "1");
        builder.environment().remove("HF_TOKEN");
        Process process = builder.start();
        try {
            if (!process.waitFor(config.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("MFLUX generation timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("MFLUX generation exited with code " + process.exitValue());
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    throw new UncertainTerminationException();
                }
            }
        }
    }

    public static class UncertainTerminationException extends IOException {
        public UncertainTerminationException() {
            super("Could not confirm MFLUX process termination; stop it before restarting this application");
        }
    }
}
