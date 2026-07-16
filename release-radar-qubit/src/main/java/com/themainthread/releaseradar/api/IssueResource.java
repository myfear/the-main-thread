package com.themainthread.releaseradar.api;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.themainthread.releaseradar.domain.IssueSeverity;
import com.themainthread.releaseradar.persistence.IssueRepository;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/issues")
@Produces(MediaType.APPLICATION_JSON)
public class IssueResource {

    private static final List<IssueSeverity> DEFAULT_SEVERITIES = List.of(
            IssueSeverity.CRITICAL,
            IssueSeverity.HIGH);

    private final IssueRepository issueRepository;

    public IssueResource(IssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    @GET
    @Path("/blockers")
    public List<BlockerView> blockers(
            @QueryParam("asOf") String asOf,
            @QueryParam("olderThanHours") @DefaultValue("24") int olderThanHours,
            @QueryParam("severity") List<IssueSeverity> severities,
            @QueryParam("limit") @DefaultValue("20") int limit) {
        if (olderThanHours < 1 || olderThanHours > 8_760) {
            throw new BadRequestException("olderThanHours must be between 1 and 8760");
        }
        if (limit < 1 || limit > 100) {
            throw new BadRequestException("limit must be between 1 and 100");
        }

        LocalDateTime cutoff = parseAsOf(asOf).minusHours(olderThanHours);
        List<IssueSeverity> selectedSeverities = severities == null || severities.isEmpty()
                ? DEFAULT_SEVERITIES
                : List.copyOf(severities);

        return issueRepository.findBlockers(cutoff, selectedSeverities, limit);
    }

    @GET
    @Path("/hotspots")
    public List<ServiceHotspot> hotspots(
            @QueryParam("minimumOpen") @DefaultValue("2") long minimumOpen) {
        if (minimumOpen < 1 || minimumOpen > 1_000) {
            throw new BadRequestException("minimumOpen must be between 1 and 1000");
        }
        return issueRepository.findHotspots(minimumOpen);
    }

    @GET
    @Path("/outliers")
    public List<ImpactOutlier> outliers() {
        return issueRepository.findImpactOutliers();
    }

    private static LocalDateTime parseAsOf(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException("asOf must use ISO-8601 local date-time format", exception);
        }
    }
}
