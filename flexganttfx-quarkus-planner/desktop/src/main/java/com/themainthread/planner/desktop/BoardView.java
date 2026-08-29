package com.themainthread.planner.desktop;

import com.flexganttfx.model.Layer;
import com.flexganttfx.model.layout.GanttLayout;
import com.flexganttfx.view.GanttChart;
import com.flexganttfx.view.graphics.GraphicsBase;
import com.flexganttfx.view.graphics.renderer.ActivityBarRenderer;
import com.themainthread.planner.contract.BoardDto;
import com.themainthread.planner.contract.BookingDto;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

public final class BoardView {

    private final GanttChart<DockDoorRow> ganttChart;
    private final Layer bookingsLayer;
    private final BoardMapper boardMapper = new BoardMapper();
    private final ScheduleProposalCoordinator coordinator;

    public BoardView(BoardClient boardClient, Consumer<String> statusSink) {
        ganttChart = new GanttChart<>(new DockDoorRow("root", "ROOT"));
        bookingsLayer = new Layer("Bookings");
        ganttChart.getLayers().add(bookingsLayer);
        coordinator = new ScheduleProposalCoordinator(bookingsLayer, boardClient, statusSink);

        GraphicsBase<DockDoorRow> graphics = ganttChart.getGraphics();
        graphics.setActivityRenderer(
                BookingActivity.class,
                GanttLayout.class,
                new ActivityBarRenderer<>(graphics, "Booking"));
        graphics.setOnActivityChangeFinished(coordinator::onActivityChangeFinished);
        graphics.showEarliestActivities();
        ganttChart.getTimeline().showTemporalUnit(ChronoUnit.HOURS, 8);
    }

    public GanttChart<DockDoorRow> chart() {
        return ganttChart;
    }

    public void render(BoardDto board) {
        BoardMapper.MappedBoard mapped = boardMapper.map(board);
        ganttChart.getRoot().getChildren().setAll(mapped.root().getChildren());
        boardMapper.attachActivities(mapped, bookingsLayer);
        coordinator.setBoardState(mapped.rowsByDoorId(), mapped.activitiesById());
        if (!board.bookings().isEmpty()) {
            Instant earliest = board.bookings().stream()
                    .map(BookingDto::startsAt)
                    .min(Instant::compareTo)
                    .orElse(Instant.parse("2026-08-20T06:00:00Z"));
            ganttChart.getTimeline().showTime(earliest);
        }
    }
}
