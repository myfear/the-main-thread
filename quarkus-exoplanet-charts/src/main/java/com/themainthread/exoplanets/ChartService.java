package com.themainthread.exoplanets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import software.xdev.chartjs.model.charts.BarChart;
import software.xdev.chartjs.model.charts.Chart;
import software.xdev.chartjs.model.charts.DoughnutChart;
import software.xdev.chartjs.model.charts.ScatterChart;
import software.xdev.chartjs.model.data.BarData;
import software.xdev.chartjs.model.data.DoughnutData;
import software.xdev.chartjs.model.data.ScatterData;
import software.xdev.chartjs.model.datapoint.ScatterDataPoint;
import software.xdev.chartjs.model.dataset.BarDataset;
import software.xdev.chartjs.model.dataset.DoughnutDataset;
import software.xdev.chartjs.model.dataset.ScatterDataset;
import software.xdev.chartjs.model.options.BarOptions;
import software.xdev.chartjs.model.options.DoughnutOptions;
import software.xdev.chartjs.model.options.LineOptions;
import software.xdev.chartjs.model.options.scale.Scales;
import software.xdev.chartjs.model.options.scale.cartesian.AbstractCartesianScaleOptions.Title;
import software.xdev.chartjs.model.options.scale.cartesian.category.CategoryScaleOptions;
import software.xdev.chartjs.model.options.scale.cartesian.linear.LinearScaleOptions;
import software.xdev.chartjs.model.options.scale.cartesian.logarithmic.LogarithmicScaleOptions;

@ApplicationScoped
public class ChartService {
    private static final List<String> METHODS = List.of("Transit", "Radial Velocity", "Microlensing", "Imaging", "Other");
    private static final List<String> COLORS = List.of("#5eead4", "#fbbf24", "#a78bfa", "#fb7185", "#94a3b8");
    private final ObjectMapper mapper;

    public ChartService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Dashboard build(Catalogue catalogue, String source, int from, int to) {
        int[][] counts = new int[METHODS.size()][to - from + 1];
        List<List<Planet>> measured = new ArrayList<>();
        for (String ignored : METHODS) {
            measured.add(new ArrayList<>());
        }
        int selected = 0;
        int unknownYear = 0;
        for (Planet planet : catalogue.planets()) {
            if (planet.year() == null) {
                unknownYear++;
                continue;
            }
            if (planet.year() < from || planet.year() > to) {
                continue;
            }
            selected++;
            int group = planet.method() == null ? -1 : METHODS.indexOf(planet.method());
            if (group < 0) {
                group = METHODS.size() - 1;
            }
            counts[group][planet.year() - from]++;
            if (planet.hasMeasuredSizeAndPeriod()) {
                measured.get(group).add(planet);
            }
        }

        var annual = new BarData();
        for (int year = from; year <= to; year++) {
            annual.addLabel(Integer.toString(year));
        }
        var methods = new DoughnutData();
        var methodDataset = new DoughnutDataset().setLabel("Planets");
        var scatter = new ScatterData();
        int plotted = 0;
        for (int group = 0; group < METHODS.size(); group++) {
            String label = METHODS.get(group);
            String color = COLORS.get(group);
            var yearlyDataset = new BarDataset().setLabel(label).setBackgroundColor(color);
            int total = 0;
            for (int count : counts[group]) {
                yearlyDataset.addData(count);
                total += count;
            }
            annual.addDataset(yearlyDataset);
            methods.addLabel(label);
            methodDataset.addData(total).addBackgroundColor(color);

            var points = new ScatterDataset().setLabel(label).setBackgroundColor(color)
                    .setShowLine(false).setPointRadius(List.of(3));
            for (Planet planet : measured.get(group)) {
                points.addData(new NamedPoint(planet));
                plotted++;
            }
            scatter.addDataset(points);
        }
        methods.addDataset(methodDataset);

        var annualOptions = new BarOptions().setResponsive(true).setMaintainAspectRatio(false).setAnimation(false)
                .setScales(new Scales()
                        .addScale("x", new CategoryScaleOptions().setStacked(true).setTitle(title("Discovery year")))
                        .addScale("y", new LinearScaleOptions().setStacked(true).setBeginAtZero(true)
                                .setTitle(title("Planets"))));
        var scatterOptions = new LineOptions().setResponsive(true).setMaintainAspectRatio(false).setAnimation(false)
                .setScales(new Scales()
                        .addScale("x", new LogarithmicScaleOptions().setTitle(title("Orbital period (days, log scale)")))
                        .addScale("y", new LogarithmicScaleOptions().setTitle(title("Radius (Earth radii, log scale)"))));
        var methodOptions = new DoughnutOptions().setResponsive(true).setMaintainAspectRatio(false).setAnimation(false);

        return new Dashboard(source, catalogue.retrievedAt(), from, to, catalogue.planets().size(),
                selected, plotted, selected - plotted, unknownYear,
                json(new BarChart(annual, annualOptions)),
                json(new DoughnutChart(methods, methodOptions)),
                json(new ScatterChart(scatter, scatterOptions)));
    }

    private JsonNode json(Chart<?, ?, ?> chart) {
        try {
            // Preserve the library's Jackson 3 serializers, then embed an object in our Jackson response.
            JsonNode node = mapper.readTree(chart.toJson());
            // The library omits empty collections. Chart.js expects each dataset to have a data array.
            for (JsonNode dataset : node.path("data").path("datasets")) {
                if (!dataset.has("data")) {
                    ((ObjectNode) dataset).putArray("data");
                }
            }
            return node;
        } catch (IOException exception) {
            throw new IllegalStateException("Chart serialization did not produce JSON", exception);
        }
    }

    private static Title title(String text) {
        return new Title().setDisplay(true).setText(text);
    }

    public static class NamedPoint extends ScatterDataPoint {
        private final String name;

        public NamedPoint(Planet planet) {
            super(planet.period(), planet.radius());
            name = planet.name();
        }
    }

    public record Dashboard(String source, String retrievedAt, int from, int to, int catalogueTotal,
            int selected, int plotted, int excludedFromScatter, int unknownYear,
            JsonNode discoveries, JsonNode methods, JsonNode sizes) {
    }
}
