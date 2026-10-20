package com.themainthread.time;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class CalendarResource {
    private final CalendarTime calendarTime;
    private final LessonCatalog catalog;
    private final Experiments experiments;
    private final DatabaseExperiments database;

    public CalendarResource(CalendarTime calendarTime, LessonCatalog catalog, Experiments experiments,
            DatabaseExperiments database) {
        this.calendarTime = calendarTime;
        this.catalog = catalog;
        this.experiments = experiments;
        this.database = database;
    }

    @GET
    @Path("/calendar")
    public CalendarView calendar(@QueryParam("at") String at, @QueryParam("zone") String zone,
            @QueryParam("mode") String mode) {
        var time = calendarTime.snapshot(at, zone, mode);
        var doors = catalog.all().stream()
                .map(lesson -> new Door(lesson.id(), lesson.title(), lesson.deep(), lesson.database(),
                        time.available(lesson.id()), time.unlockAt(lesson.id())))
                .toList();
        return new CalendarView(time, CalendarTime.ZONES, doors);
    }

    @POST
    @Path("/clock/advance")
    @Consumes(MediaType.APPLICATION_JSON)
    public CalendarTime.Snapshot advance(RunRequest request) {
        if (request == null) {
            throw new BadRequestException("Send the current calendar settings as JSON.");
        }
        var current = calendarTime.snapshot(request.at(), request.zone(), request.mode());
        Instant next = current.instant().atZone(java.time.ZoneId.of(current.zone())).plusDays(1).toInstant();
        return calendarTime.snapshot(next.toString(), current.zone(), current.mode());
    }

    @POST
    @Path("/doors/{id}/run")
    @Consumes(MediaType.APPLICATION_JSON)
    public RunView run(@PathParam("id") int id, RunRequest request) {
        if (request == null) {
            throw new BadRequestException("Send a JSON object with the clock and calendar settings.");
        }
        var lesson = catalog.get(id);
        var time = calendarTime.snapshot(request.at(), request.zone(), request.mode());
        if (!time.available(id)) {
            throw new ForbiddenException("Door " + id + " opens at " + time.unlockAt(id)
                    + ". Advance the simulated clock or switch to Explore mode.");
        }
        String variant = request.variant() == null ? "default" : request.variant();
        if (!variant.equals("default") && !(variant.equals("alternative") && lesson.alternativeLabel() != null)) {
            throw new BadRequestException("Choose a scenario offered by this door.");
        }
        return new RunView(time, lesson, variant, experiments.run(id, variant, time));
    }

    @GET
    @Path("/jobs/{id}")
    public JobView job(@PathParam("id") String id) {
        try {
            UUID jobId = UUID.fromString(id);
            return new JobView(jobId, database.effectCount(jobId));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Use the job UUID returned by door 24.");
        }
    }

    public record RunRequest(String at, String zone, String mode, String variant) {
    }

    public record Door(int id, String title, boolean deep, boolean database, boolean available, Instant unlockAt) {
    }

    public record CalendarView(CalendarTime.Snapshot clock, List<String> zones, List<Door> doors) {
    }

    public record RunView(CalendarTime.Snapshot clock, LessonCatalog.Lesson lesson, String variant,
            ExperimentResult result) {
    }

    public record JobView(UUID id, int committedEffects) {
    }
}
