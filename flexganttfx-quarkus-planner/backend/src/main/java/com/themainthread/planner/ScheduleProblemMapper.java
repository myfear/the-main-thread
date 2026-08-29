package com.themainthread.planner;

import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ScheduleProblemMapper {

    @ServerExceptionMapper
    public Response map(ScheduleConflictException problem) {
        return Response.status(problem.status())
                .entity(problem.problem())
                .build();
    }
}
