package com.themainthread.images;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class ImageJobs {
    private static final Logger LOG = Logger.getLogger(ImageJobs.class);
    private static final int STEPS = 16;
    private static final int HISTORY_LIMIT = 64;
    private final MfluxImageGenerator generator;
    private final ImageConfig config;
    private final ObjectMapper mapper;
    private final Map<UUID, JobView> jobs = new LinkedHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2), Thread.ofPlatform().name("flux-generation").daemon().factory(),
            new ThreadPoolExecutor.AbortPolicy());
    private volatile boolean completionUncertain;

    public ImageJobs(MfluxImageGenerator generator, ImageConfig config, ObjectMapper mapper) {
        this.generator = generator;
        this.config = config;
        this.mapper = mapper;
    }

    public synchronized JobView submit(GenerateRequest request) {
        if (completionUncertain) {
            throw new WebApplicationException("Stop the uncertain MFLUX process before restarting this application", 503);
        }
        if (jobs.size() >= HISTORY_LIMIT) {
            jobs.entrySet().removeIf(entry -> entry.getValue().terminal());
        }
        var job = new JobView(UUID.randomUUID(), JobView.State.QUEUED, request.prompt().strip(),
                request.width(), request.height(), request.seed(), config.model(), STEPS,
                Instant.now(), null, null, null, null, null);
        jobs.put(job.id(), job);
        try {
            executor.execute(() -> generate(job));
        } catch (RejectedExecutionException e) {
            jobs.remove(job.id());
            throw new WebApplicationException("One image is running and two are queued; try again later", 429);
        }
        return job;
    }

    public synchronized JobView get(UUID id) {
        var job = jobs.get(id);
        if (job == null) {
            throw new NotFoundException("Unknown job");
        }
        return job;
    }

    public Path image(UUID id) {
        if (get(id).state() != JobView.State.SUCCEEDED) {
            throw new WebApplicationException("The image is not available", 409);
        }
        return config.outputDirectory().resolve(id + ".png");
    }

    private void generate(JobView queued) {
        Instant start = Instant.now();
        JobView.State state = JobView.State.FAILED;
        String error = null;
        try {
            if (completionUncertain) {
                throw new IllegalStateException("MFLUX process completion is uncertain; queued request was not started");
            }
            publish(view(queued, JobView.State.RUNNING, start, null, null));
            Files.createDirectories(config.outputDirectory());
            Path output = config.outputDirectory().resolve(queued.id() + ".png");
            generator.generate(queued, output);
            validatePng(output, queued.width(), queued.height());
            state = JobView.State.SUCCEEDED;
        } catch (MfluxImageGenerator.UncertainTerminationException e) {
            completionUncertain = true;
            state = JobView.State.UNKNOWN;
            error = e.getMessage();
            LOG.warnf("Image job %s has an uncertain backend outcome (%s)", queued.id(), e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "Generation interrupted during application shutdown";
        } catch (IOException e) {
            error = e.getMessage();
            LOG.warnf("Image job %s failed (%s)", queued.id(), e.getClass().getSimpleName());
        } catch (Exception e) {
            error = e instanceof IllegalStateException ? e.getMessage() : "Invalid image response or local storage failure";
            LOG.warnf("Image job %s failed (%s)", queued.id(), e.getClass().getSimpleName());
        }
        var finished = view(queued, state, start, Instant.now(), error);
        try {
            Files.createDirectories(config.outputDirectory());
            Path temporary = config.outputDirectory().resolve(queued.id() + ".json.tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), finished);
            Files.move(temporary, config.outputDirectory().resolve(queued.id() + ".json"),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            finished = view(queued, JobView.State.FAILED, start, Instant.now(), "Could not save job metadata");
            LOG.errorf("Could not save metadata for image job %s", queued.id());
        }
        publish(finished);
    }

    private synchronized void publish(JobView job) {
        jobs.put(job.id(), job);
    }

    private static JobView view(JobView job, JobView.State state, Instant start, Instant end, String error) {
        return new JobView(job.id(), state, job.prompt(), job.width(), job.height(), job.seed(), job.model(), job.steps(),
                job.submittedAt(), start, end, end == null ? null : Duration.between(start, end).toMillis(), error,
                state == JobView.State.SUCCEEDED ? "/api/jobs/" + job.id() + "/image" : null);
    }

    private static void validatePng(Path output, int width, int height) throws IOException {
        if (Files.size(output) > 9_000_000) {
            throw new IOException("Image exceeds the demo's response limit");
        }
        byte[] png = Files.readAllBytes(output);
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IOException("Image format not recognized");
            }
            var reader = readers.next();
            try {
                reader.setInput(stream);
                if (!"png".equalsIgnoreCase(reader.getFormatName())
                        || reader.getWidth(0) != width || reader.getHeight(0) != height) {
                    throw new IOException("Expected a PNG with the requested dimensions");
                }
                reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(12, TimeUnit.SECONDS)) {
                LOG.warn("Image worker did not finish shutdown within twelve seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
