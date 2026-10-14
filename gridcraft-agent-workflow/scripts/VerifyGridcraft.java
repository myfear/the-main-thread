///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+
//DEPS org.apache.poi:poi-ooxml:5.5.1
//DEPS org.apache.logging.log4j:log4j-core:2.26.1
//DEPS com.fasterxml.jackson.core:jackson-databind:2.19.2
//COMPILE_OPTIONS -proc:none
//JAVA_OPTIONS -Djava.awt.headless=true

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Independent file checks and a small MCP protocol probe for the returns report. */
public class VerifyGridcraft {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path SOURCE = Path.of("build/source.xlsx");
    private static final Path EVALUATION = Path.of("eval/generated");

    public static void main(String... args) throws Exception {
        require(args.length > 0, "Use recipe, verify <report.xlsx>, or probe <gridcraft-cli>.");
        switch (args[0]) {
            case "recipe" -> recipe();
            case "verify" -> verify(Path.of(args[1]));
            case "probe" -> probe(args[1]);
            default -> throw new IllegalArgumentException("Unknown operation: " + args[0]);
        }
    }

    private static void recipe() throws Exception {
        double[] amounts = sourceAmounts();
        String hash = sha256(SOURCE);
        List<Object> rows = List.of(
                List.of("Item", "Baseline", "Scenario"),
                List.of("First return", amounts[0], amounts[0]),
                List.of("Second return", amounts[1], amounts[1]),
                List.of("Third return", amounts[2], 70),
                List.of("Total", "=SUM(B2:B4)", "=SUM(C2:C4)"),
                List.of("", "", ""),
                List.of("Difference", "", "=C5-B5"),
                List.of("Source stored total", 60, ""),
                List.of("Source recalculated total", 80, ""),
                List.of("Source range", "Transactions!D2:D4", ""),
                List.of("Source SHA-256", hash, ""));
        List<Map<String, Object>> steps = List.of(
                step("sheet.rename", Map.of("sheet", 0, "name", "Report")),
                step("range.setValues", Map.of("range", "A1", "values", rows)),
                step("home.bold", Map.of("range", "A1:C1", "on", true)),
                step("home.bold", Map.of("range", "A5:C5", "on", true)),
                step("home.numberFormat", Map.of("range", "B2:C9", "code", "£#,##0.00")),
                step("home.columnWidth", Map.of("cols", "A:C", "chars", 26)),
                step("home.wrapText", Map.of("range", "B11", "on", true)),
                step("home.rowHeight", Map.of("rows", "11:11", "height", 54)),
                step("view.freezeTopRow", Map.of()),
                step("insert.chart", Map.of("range", "A1:C4", "type", "column",
                        "title", "Return scenario", "at", "E2")));
        List<String> lines = new ArrayList<>();
        for (var step : steps) {
            lines.add(JSON.writeValueAsString(step));
        }
        Files.createDirectories(Path.of("build"));
        Files.write(Path.of("build/report.jsonl"), lines, StandardCharsets.UTF_8);
        Files.writeString(Path.of("build/source.sha256"), hash + "\n");
        System.out.println("Prepared build/report.jsonl with source provenance.");
    }

    private static Map<String, Object> step(String command, Map<String, Object> params) {
        return Map.of("command", command, "params", params);
    }

    private static double[] sourceAmounts() throws Exception {
        try (var stream = Files.newInputStream(SOURCE);
                var workbook = new XSSFWorkbook(stream)) {
            require(workbook.getNumberOfSheets() == 3, "Source sheet count changed.");
            require(workbook.isSheetHidden(workbook.getSheetIndex("Rules")), "Rules must be hidden.");
            require(workbook.getAllPictures().size() == 1, "Source image missing.");
            require(workbook.getName("MerchandiseRule") != null, "Source name missing.");
            Cell total = workbook.getSheet("Dashboard").getRow(2).getCell(1);
            require(total.getCellType() == CellType.FORMULA, "Source total must be a formula.");
            require(total.getCellFormula().equals("SUM('Transactions'!D2:D4)"), "Source formula changed.");
            require(total.getNumericCellValue() == 60, "Stored source result must be 60.");
            double[] amounts = new double[3];
            for (int index = 0; index < amounts.length; index++) {
                amounts[index] = workbook.getSheet("Transactions").getRow(index + 1).getCell(3)
                        .getNumericCellValue();
            }
            require(amounts[0] == 10 && amounts[1] == 20 && amounts[2] == 50,
                    "Source inputs must be 10, 20, 50.");
            return amounts;
        }
    }

    private static void verify(Path output) throws Exception {
        sourceAmounts();
        String sourceHash = sha256(SOURCE);
        require(Files.readString(Path.of("build/source.sha256")).strip().equals(sourceHash),
                "The source file was modified.");
        try (var stream = Files.newInputStream(output);
                var workbook = new XSSFWorkbook(stream)) {
            require(workbook.getNumberOfSheets() == 1, "Report must contain one sheet.");
            var report = workbook.getSheet("Report");
            require(report != null, "Report sheet missing.");
            for (int column = 0; column < 3; column++) {
                require(report.getColumnWidth(column) >= 24 * 256 && report.getColumnWidth(column) <= 28 * 256,
                        "Columns A:C must be about 26 characters wide; column " + (column + 1)
                                + " is " + report.getColumnWidth(column) / 256.0 + " characters.");
            }
            require(report.getRow(9).getCell(1).getStringCellValue().equals("Transactions!D2:D4"),
                    "Source range missing.");
            require(report.getRow(10).getCell(1).getStringCellValue().equals(sourceHash), "Source hash missing.");
            require(report.getRow(10).getCell(1).getCellStyle().getWrapText(), "Source hash must wrap.");
            // GridCraft's 54 screen points are saved as 40.5 typographic points in XLSX.
            require(report.getRow(10).getHeightInPoints() >= 36, "Source hash row is too short.");
            require(report.getRow(7).getCell(1).getNumericCellValue() == 60, "Stored total missing.");
            require(report.getRow(8).getCell(1).getNumericCellValue() == 80, "Recalculated total missing.");
            require(report.getRow(3).getCell(1).getNumericCellValue() == 50, "Baseline was overwritten.");
            require(report.getRow(3).getCell(2).getNumericCellValue() == 70, "Scenario input missing.");
            var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            String[] addresses = { "B5", "C5", "C7" };
            String[] formulas = { "SUM(B2:B4)", "SUM(C2:C4)", "C5-B5" };
            double[] expected = { 80, 100, 20 };
            for (int index = 0; index < addresses.length; index++) {
                var reference = new org.apache.poi.ss.util.CellReference(addresses[index]);
                Cell cell = report.getRow(reference.getRow()).getCell(reference.getCol());
                require(cell.getCellType() == CellType.FORMULA, addresses[index] + " lost its formula.");
                require(cell.getCellFormula().equals(formulas[index]), addresses[index] + " has the wrong formula.");
                require(cell.getNumericCellValue() == expected[index], addresses[index] + " has a wrong saved value.");
                require(evaluator.evaluate(cell).getNumberValue() == expected[index],
                        addresses[index] + " failed independent POI calculation.");
            }
            var drawing = report.getDrawingPatriarch();
            require(drawing != null && drawing.getCharts().size() == 1, "One chart is required.");
            require(drawing.getCharts().getFirst().getChartSeries().getFirst().getSeriesCount() == 2,
                    "Chart needs two series.");
            require(report.getPaneInformation() != null && report.getPaneInformation().isFreezePane(),
                    "Header must be frozen.");
            var evidence = Map.of("sourceUnchanged", true, "sourceStoredTotal", 60, "baseline", 80,
                    "scenario", 100, "difference", 20, "formulaCellsChecked", 3,
                    "chartCount", 1, "independentCalculation", "Apache POI 5.5.1");
            String result = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(evidence);
            Files.createDirectories(EVALUATION);
            Files.writeString(EVALUATION.resolve("verification.json"), result + "\n");
            System.out.println(result);
        }
    }

    private static void probe(String executable) throws Exception {
        sourceAmounts();
        Files.createDirectories(EVALUATION);
        try (var mcp = new Mcp(executable)) {
            mcp.request("initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "gridcraft-article-probe", "version", "1")));
            mcp.notifyInitialized();
            JsonNode tools = mcp.request("tools/list", Map.of());
            int toolCount = tools.path("result").path("tools").size();
            JsonNode computed = mcp.call("read_range", Map.of("sheet", "Dashboard", "range", "B3"));
            require(computed.path("values").get(0).get(0).asDouble() == 80, "GridCraft must recalculate to 80.");
            JsonNode formula = mcp.call("read_range", Map.of("sheet", "Dashboard", "range", "B3", "formulas", true));
            require(formula.path("values").get(0).get(0).asText().equals("=SUM(Transactions!D2:D4)"),
                    "Formula read differs.");
            JsonNode implicit = mcp.call("read_range", Map.of("sheet", "Transactions"));
            require(implicit.path("range").asText().equals("A1:D4"), "Omitted range must use the used range.");
            JsonNode oversized = mcp.request("tools/call", Map.of("name", "read_range", "arguments",
                    Map.of("sheet", "Transactions", "range", "A1:B500001")));
            require(oversized.path("result").path("isError").asBoolean(), "Million-cell guard did not reject.");
            JsonNode screenshot = mcp.request("tools/call", Map.of("name", "screenshot", "arguments",
                    Map.of("path", EVALUATION.resolve("headless.png").toString())));
            require(screenshot.path("result").path("isError").asBoolean(), "Headless screenshots must fail.");
            var evidence = Map.of("toolCount", toolCount, "toolsListUtf8Bytes",
                    JSON.writeValueAsBytes(tools).length, "computedTotal", 80,
                    "defaultReadRange", implicit.path("range").asText(),
                    "millionCellGuard", "rejected", "headlessScreenshot", "rejected");
            String result = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(evidence);
            Files.writeString(EVALUATION.resolve("mcp-probe.json"), result + "\n");
            System.out.println(result);
        }
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static class Mcp implements AutoCloseable {
        private final Process process;
        private final BufferedReader input;
        private final BufferedWriter output;
        private int id;

        Mcp(String executable) throws Exception {
            process = new ProcessBuilder(executable, "mcp", "--in", SOURCE.toAbsolutePath().toString())
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
            input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        }

        JsonNode request(String method, Map<String, Object> params) throws Exception {
            int requestId = ++id;
            output.write(JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", requestId,
                    "method", method, "params", params)));
            output.newLine();
            output.flush();
            // This is a fixture probe, not an agent gateway. Bound its own replies and wait time.
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var reply = executor.submit(() -> {
                    StringBuilder line = new StringBuilder();
                    int value;
                    while ((value = input.read()) != -1 && value != '\n') {
                        require(line.length() < 100_000, "Probe response exceeded 100,000 characters.");
                        line.append((char) value);
                    }
                    require(!line.isEmpty(), "MCP closed without a response.");
                    return JSON.readTree(line.toString());
                });
                try {
                    JsonNode response = reply.get(15, TimeUnit.SECONDS);
                    require(response.path("id").asInt() == requestId, "MCP response id differs.");
                    require(!response.has("error"), "MCP protocol error: " + response.path("error"));
                    return response;
                } catch (Exception failure) {
                    process.destroyForcibly();
                    reply.cancel(true);
                    throw failure;
                }
            }
        }

        JsonNode call(String tool, Map<String, Object> args) throws Exception {
            JsonNode response = request("tools/call", Map.of("name", tool, "arguments", args));
            require(!response.path("result").path("isError").asBoolean(), "Tool failed: " + tool + " " + response);
            return JSON.readTree(response.path("result").path("content").get(0).path("text").asText());
        }

        void notifyInitialized() throws Exception {
            output.write("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n");
            output.flush();
        }

        @Override
        public void close() throws Exception {
            output.close();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            input.close();
        }
    }
}
