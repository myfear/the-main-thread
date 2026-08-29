package com.themainthread.planner;

import com.themainthread.planner.contract.BoardDto;
import com.themainthread.planner.contract.BookingDto;
import com.themainthread.planner.contract.ScheduleBookingCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.time.Instant;

@Path("/api")
@ApplicationScoped
public class PlannerResource {

    private final BoardService boardService;

    public PlannerResource(BoardService boardService) {
        this.boardService = boardService;
    }

    @GET
    @Path("/board")
    public BoardDto board(
            @NotNull @QueryParam("from") Instant from,
            @NotNull @QueryParam("to") Instant to) {
        return boardService.loadBoard(from, to);
    }

    @PUT
    @Path("/bookings/{id}/schedule")
    public BookingDto schedule(
            @PathParam("id") String id,
            @NotNull @Valid ScheduleBookingCommand command) {
        return boardService.schedule(id, command);
    }
}
