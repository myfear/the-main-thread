package com.themainthread.planner.desktop;

import java.time.Instant;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public final class PlannerApp extends Application {

    private static final Instant DEFAULT_FROM = Instant.parse("2026-08-20T06:00:00Z");
    private static final Instant DEFAULT_TO = Instant.parse("2026-08-20T12:00:00Z");

    @Override
    public void start(Stage stage) {
        String baseUrl = System.getenv().getOrDefault("PLANNER_API_BASE_URL", "http://localhost:8080");
        BoardClient boardClient = new BoardClient(baseUrl);
        Label statusLabel = new Label("Loading board...");
        BoardView boardView = new BoardView(boardClient, message -> Platform.runLater(() -> statusLabel.setText(message)));

        BorderPane layout = new BorderPane();
        layout.setCenter(boardView.chart());
        layout.setBottom(statusLabel);

        Scene scene = new Scene(layout, 1280, 720);
        scene.getStylesheets().add(PlannerApp.class.getResource("planner.css").toExternalForm());
        stage.setTitle("Dock planner");
        stage.setScene(scene);
        stage.show();

        Thread.startVirtualThread(() -> {
            try {
                var board = boardClient.fetchBoard(DEFAULT_FROM, DEFAULT_TO);
                Platform.runLater(() -> {
                    boardView.render(board);
                    statusLabel.setText("Drag a booking to propose a new schedule.");
                });
            } catch (Exception exception) {
                Platform.runLater(() -> statusLabel.setText("Failed to load board: " + exception.getMessage()));
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
