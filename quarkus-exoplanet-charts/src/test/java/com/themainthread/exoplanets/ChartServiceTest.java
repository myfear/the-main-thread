package com.themainthread.exoplanets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ChartServiceTest {
    private final ChartService charts = new ChartService(new ObjectMapper());

    @Test
    void keepsCountsWhenMeasurementsAreMissingAndFillsYearGaps() {
        var catalogue = new Catalogue("fixture", "fixture", List.of(
                planet("A", "Transit", 2000, 4.0, 0, 2.0, 0),
                planet("B", "Radial Velocity", 2002, 40.0, 0, null, null),
                planet("C", "Imaging", 2002, 400.0, 1, 12.0, 0),
                planet("D", "New technique", 2002, 20.0, 0, 1.5, 0),
                planet("E", "Transit", null, 8.0, 0, 3.0, 0),
                planet("F", "Transit", 2020, 8.0, 0, 3.0, 0)));
        var dashboard = charts.build(catalogue, "snapshot", 2000, 2002);

        assertEquals(4, dashboard.selected());
        assertEquals(2, dashboard.plotted());
        assertEquals(2, dashboard.excludedFromScatter());
        assertEquals(1, dashboard.unknownYear());
        assertEquals("2001", dashboard.discoveries().at("/data/labels/1").asText());
        assertEquals(0, dashboard.discoveries().at("/data/datasets/0/data/1").asInt());
        assertEquals(1, dashboard.methods().at("/data/datasets/0/data/4").asInt());
        assertEquals("A", dashboard.sizes().at("/data/datasets/0/data/0/name").asText());
        assertEquals(4.0, dashboard.sizes().at("/data/datasets/0/data/0/x").asDouble());
        assertEquals("logarithmic", dashboard.sizes().at("/options/scales/x/type").asText());
        assertTrue(dashboard.discoveries().at("/options/scales/y/stacked").asBoolean());
        assertTrue(dashboard.sizes().at("/data/datasets/1/data").isArray());
        assertEquals(0, dashboard.sizes().at("/data/datasets/1/data").size());
    }

    @Test
    void rejectsNonPositiveNonFiniteAndBoundedValuesForLogScales() {
        for (Double value : new Double[] { null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY }) {
            assertFalse(planet("A", "Transit", 2000, value, 0, 2.0, 0).hasMeasuredSizeAndPeriod());
            assertFalse(planet("A", "Transit", 2000, 2.0, 0, value, 0).hasMeasuredSizeAndPeriod());
        }
        for (Integer limit : new Integer[] { null, -1, 1 }) {
            assertFalse(planet("A", "Transit", 2000, 2.0, limit, 2.0, 0).hasMeasuredSizeAndPeriod());
            assertFalse(planet("A", "Transit", 2000, 2.0, 0, 2.0, limit).hasMeasuredSizeAndPeriod());
        }
    }

    @Test
    void emitsUsableEmptyChartConfigurations() {
        var dashboard = charts.build(new Catalogue("fixture", "fixture", List.of()), "snapshot", 2000, 2002);
        assertEquals(0, dashboard.selected());
        assertEquals(0, dashboard.plotted());
        assertEquals("doughnut", dashboard.methods().get("type").asText());
        for (var dataset : dashboard.sizes().at("/data/datasets")) {
            assertTrue(dataset.get("data").isArray());
            assertTrue(dataset.get("data").isEmpty());
        }
        assertEquals(3, dashboard.discoveries().at("/data/labels").size());
    }

    @Test
    void countsAnAbsentMethodAsOther() {
        var catalogue = new Catalogue("fixture", "fixture",
                List.of(planet("Unnamed method b", null, 2000, 4.0, 0, 2.0, 0)));
        var dashboard = charts.build(catalogue, "snapshot", 2000, 2000);
        assertEquals(1, dashboard.methods().at("/data/datasets/0/data/4").asInt());
        assertEquals(1, dashboard.plotted());
    }

    private static Planet planet(String name, String method, Integer year, Double period, Integer periodLimit,
            Double radius, Integer radiusLimit) {
        return new Planet(name, method, year, period, periodLimit, radius, radiusLimit);
    }
}
