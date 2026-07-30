///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS org.apache.poi:poi-ooxml:5.5.1
//DEPS org.apache.logging.log4j:log4j-core:2.26.1
//DEPS com.fasterxml.jackson.core:jackson-databind:2.20.1
//DEPS info.picocli:picocli:4.7.7
//COMPILE_OPTIONS -proc:none

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.Styles;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLFilterImpl;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "excel-xray",
        mixinStandardHelpOptions = true,
        description = "Inspect large XLSX workbooks without placing their contents in an agent context.",
        subcommands = {
                ExcelXray.InventoryCommand.class,
                ExcelXray.RetailAuditCommand.class,
                ExcelXray.SliceCommand.class,
                ExcelXray.ImagesCommand.class,
                ExcelXray.ExtractImageCommand.class
        })
public class ExcelXray implements Runnable {

    static final int DEFAULT_MAX_OUTPUT_CHARACTERS = 12_000;

    public static void main(String... args) {
        CommandLine commandLine = new CommandLine(new ExcelXray());
        commandLine.setExecutionExceptionHandler((exception, parsedCommand, parseResult) -> {
            parsedCommand.getErr().println("ERROR: " + exception.getMessage());
            return 1;
        });
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    @Command(name = "inventory", description = "Create a compact map of workbook sheets, columns, formulas, and images.")
    static final class InventoryCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to an .xlsx workbook.")
        Path workbook;

        @Option(names = "--sample-rows", defaultValue = "2", description = "Data rows to sample from each sheet.")
        int sampleRows;

        @Option(
                names = "--max-output-chars",
                defaultValue = "12000",
                description = "Fail instead of emitting more characters than this limit.")
        int maxOutputCharacters;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> report = ExcelXrayService.inventory(workbook, sampleRows);
            printJson(report, maxOutputCharacters);
            return 0;
        }
    }

    @Command(name = "audit-retail", description = "Answer the bounded audit question for UCI Online Retail II.")
    static final class RetailAuditCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to online_retail_II.xlsx.")
        Path workbook;

        @Option(
                names = "--evidence-lines",
                defaultValue = "3",
                description = "Maximum transaction rows returned for each winning result.")
        int evidenceLines;

        @Option(
                names = "--max-output-chars",
                defaultValue = "12000",
                description = "Fail instead of emitting more characters than this limit.")
        int maxOutputCharacters;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> report = ExcelXrayService.auditRetail(workbook, evidenceLines);
            printJson(report, maxOutputCharacters);
            return 0;
        }
    }

    @Command(name = "slice", description = "Return one bounded cell range with cell references.")
    static final class SliceCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to an .xlsx workbook.")
        Path workbook;

        @Option(names = "--sheet", required = true, description = "Exact sheet name.")
        String sheet;

        @Option(names = "--range", required = true, description = "Excel range such as A1:H20.")
        String range;

        @Option(
                names = "--include-formulas",
                description = "Run a second streaming pass and include formulas beside cached values.")
        boolean includeFormulas;

        @Option(
                names = "--max-output-chars",
                defaultValue = "12000",
                description = "Fail instead of emitting more characters than this limit.")
        int maxOutputCharacters;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> report = ExcelXrayService.slice(workbook, sheet, range, includeFormulas);
            printJson(report, maxOutputCharacters);
            return 0;
        }
    }

    @Command(name = "images", description = "List embedded workbook images without returning their bytes.")
    static final class ImagesCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to an .xlsx workbook.")
        Path workbook;

        @Option(
                names = "--max-output-chars",
                defaultValue = "12000",
                description = "Fail instead of emitting more characters than this limit.")
        int maxOutputCharacters;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> report = ExcelXrayService.images(workbook);
            printJson(report, maxOutputCharacters);
            return 0;
        }
    }

    @Command(name = "extract-image", description = "Extract one embedded image by the one-based index returned by images.")
    static final class ExtractImageCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Path to an .xlsx workbook.")
        Path workbook;

        @Option(names = "--index", required = true, description = "One-based image index.")
        int index;

        @Option(names = "--output", required = true, description = "Output file path.")
        Path output;

        @Override
        public Integer call() throws Exception {
            Map<String, Object> report = ExcelXrayService.extractImage(workbook, index, output);
            printJson(report, DEFAULT_MAX_OUTPUT_CHARACTERS);
            return 0;
        }
    }

    static void printJson(Object value, int maxOutputCharacters) throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(value);
        if (json.length() > maxOutputCharacters) {
            throw new IllegalStateException(
                    "Output would contain " + json.length() + " characters; limit is " + maxOutputCharacters
                            + ". Request a smaller range or raise --max-output-chars explicitly.");
        }
        System.out.println(json);
    }
}

final class ExcelXrayService {

    private static final long DEFAULT_MAX_FILE_BYTES = 100L * 1024 * 1024;
    private static final long DEFAULT_MAX_EXPANDED_BYTES = 1024L * 1024 * 1024;
    private static final int DEFAULT_MAX_ZIP_ENTRIES = 1_000;
    private static final Pattern XLSX_EXTENSION = Pattern.compile("(?i).*\\.xlsx");
    private static final DataFormatter DATA_FORMATTER = new DataFormatter(Locale.UK, true);

    private ExcelXrayService() {
    }

    static Map<String, Object> inventory(Path workbook, int sampleRows) throws Exception {
        WorkbookEnvelope envelope = inspectEnvelope(workbook);
        List<Map<String, Object>> sheets = new ArrayList<>();
        List<ImageAsset> imageAssets = new ArrayList<>();
        long totalCells = 0;
        long serializedCellCharacters = 0;
        long totalFormulas = 0;

        configurePoiGuards();
        try (OPCPackage pkg = OPCPackage.open(workbook.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg, true);
            Styles styles = reader.getStylesTable();
            SharedStrings sharedStrings = reader.getSharedStringsTable();
            Map<String, String> visibility = readSheetVisibility(reader);
            XSSFReader.SheetIterator iterator = reader.getSheetIterator();

            while (iterator.hasNext()) {
                SheetInventoryHandler sheetHandler = new SheetInventoryHandler(sampleRows);
                FormulaTrackingFilter formulaFilter;
                try (InputStream sheetStream = iterator.next()) {
                    formulaFilter = parseSheet(styles, sharedStrings, sheetStream, sheetHandler, false, null);
                }

                String sheetName = iterator.getSheetName();
                Map<String, Object> sheet = sheetHandler.toMap(sheetName, visibility.getOrDefault(sheetName, "visible"));
                sheet.put("formulaCells", formulaFilter.formulaCount);
                sheets.add(sheet);

                totalCells += sheetHandler.cellCount;
                serializedCellCharacters += sheetHandler.serializedCharacters;
                totalFormulas += formulaFilter.formulaCount;
            }
        }
        imageAssets.addAll(scanImages(workbook));
        for (Map<String, Object> sheet : sheets) {
            String sheetName = (String) sheet.get("name");
            sheet.put(
                    "images",
                    imageAssets.stream()
                            .filter(image -> image.sheet().equals(sheetName))
                            .map(ImageAsset::metadata)
                            .toList());
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("command", "inventory");
        report.put("workbook", envelope.toMap());
        report.put("sheets", sheets);
        report.put("summary", orderedMap(
                "sheetCount", sheets.size(),
                "cellCount", totalCells,
                "formulaCellCount", totalFormulas,
                "imageCount", imageAssets.size()));
        report.put("contextBudget", orderedMap(
                "serializedCellCharactersIfDumped", serializedCellCharacters,
                "note", "This is a character count for formatted cell text, not a model token count."));
        return report;
    }

    static Map<String, Object> auditRetail(Path workbook, int evidenceLines) throws Exception {
        WorkbookEnvelope envelope = inspectEnvelope(workbook);
        configurePoiGuards();

        RetailAccumulator accumulator = new RetailAccumulator();
        try (OPCPackage pkg = OPCPackage.open(workbook.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg, true);
            streamRetailSheets(reader, new RetailAuditHandlerFactory(accumulator));
        }

        String topCustomer = accumulator.topCustomer();
        String topReturnedProduct = accumulator.topReturnedProduct();
        String naiveTopReturnedCode = accumulator.naiveTopReturnedCode();
        if (topCustomer == null || topReturnedProduct == null) {
            throw new IllegalStateException("The workbook did not contain enough valid retail rows to answer the audit.");
        }

        RetailEvidenceCollector evidenceCollector =
                new RetailEvidenceCollector(topCustomer, topReturnedProduct, evidenceLines);
        try (OPCPackage pkg = OPCPackage.open(workbook.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg, true);
            streamRetailSheets(reader, new RetailEvidenceHandlerFactory(evidenceCollector));
        }

        BigDecimal customerRevenue = accumulator.customerNetRevenue.get(topCustomer);
        BigDecimal returnedValue = accumulator.productReturnedValue.get(topReturnedProduct);

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("topNonUkCustomer", orderedMap(
                "customerId", topCustomer,
                "country", accumulator.customerCountry.get(topCustomer),
                "netRevenueGbp", money(customerRevenue),
                "evidence", evidenceCollector.customerEvidence));
        answer.put("largestReturnedProduct", orderedMap(
                "stockCode", topReturnedProduct,
                "description", accumulator.productDescription.get(topReturnedProduct),
                "returnedValueGbp", money(returnedValue),
                "evidence", evidenceCollector.productEvidence));
        answer.put("dataQualityTrap", orderedMap(
                "naiveLargestReturnCode", naiveTopReturnedCode,
                "description", accumulator.productDescription.get(naiveTopReturnedCode),
                "returnedValueGbp", money(accumulator.allReturnedCodeValue.get(naiveTopReturnedCode)),
                "excludedBecause",
                "The stock code does not match the five-digit merchandise code shape and represents an adjustment."));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("command", "audit-retail");
        report.put("question",
                "Across both workbook years, which customer outside the United Kingdom produced the most net revenue, "
                        + "and which merchandise product produced the largest value of returned goods? "
                        + "Explain why a naive product ranking is wrong.");
        report.put("workbook", envelope.toMap());
        report.put("scan", orderedMap(
                "passes", 2,
                "dataRows", accumulator.dataRows,
                "validRows", accumulator.validRows,
                "invalidRows", accumulator.invalidRows,
                "missingCustomerRows", accumulator.missingCustomerRows,
                "cancellationRows", accumulator.cancellationRows));
        report.put("totalsGbp", orderedMap(
                "grossSales", money(accumulator.grossSales),
                "returnedGoods", money(accumulator.returnedGoods),
                "netRevenue", money(accumulator.netRevenue)));
        report.put("answer", answer);
        report.put("contextBudget", orderedMap(
                "serializedCellCharactersIfDumped", accumulator.serializedCharacters,
                "rowsReturnedAsEvidence",
                evidenceCollector.customerEvidence.size() + evidenceCollector.productEvidence.size(),
                "note", "The workbook stayed on disk. Only this aggregate and bounded evidence enter agent context."));
        report.put("warnings", List.of(
                "Net revenue is Quantity multiplied by Price, so negative quantities reduce the customer total.",
                "Rows without Customer ID contribute to workbook totals but cannot contribute to the customer ranking.",
                "Returned value includes rows with negative Quantity and positive Price.",
                "Merchandise stock codes must match five digits followed by an optional letter."));
        return report;
    }

    static Map<String, Object> slice(
            Path workbook,
            String requestedSheet,
            String requestedRange,
            boolean includeFormulas)
            throws Exception {
        inspectEnvelope(workbook);
        CellRangeAddress range = CellRangeAddress.valueOf(requestedRange);
        SliceCollector values = new SliceCollector(range);

        configurePoiGuards();
        try (OPCPackage pkg = OPCPackage.open(workbook.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg, true);
            parseSelectedSheet(reader, requestedSheet, values, false, range);
        }

        if (!values.sheetFound) {
            throw new IllegalArgumentException("Sheet not found: " + requestedSheet);
        }

        Map<String, String> formulas = Map.of();
        if (includeFormulas && values.formulaCount > 0) {
            SliceCollector formulaCollector = new SliceCollector(range);
            try (OPCPackage pkg = OPCPackage.open(workbook.toFile(), PackageAccess.READ)) {
                XSSFReader reader = new XSSFReader(pkg, true);
                parseSelectedSheet(reader, requestedSheet, formulaCollector, true, range);
            }
            formulas = formulaCollector.formulasOnly();
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("command", "slice");
        report.put("workbook", workbook.toAbsolutePath().normalize().toString());
        report.put("sheet", requestedSheet);
        report.put("range", requestedRange.toUpperCase(Locale.ROOT));
        report.put("formulaCellsInSheet", values.formulaCount);
        report.put("rows", values.rows(formulas));
        report.put("contextBudget", orderedMap(
                "cellsReturned", values.values.size(),
                "note", "The command fails before printing if the JSON exceeds --max-output-chars."));
        return report;
    }

    static Map<String, Object> images(Path workbook) throws Exception {
        inspectEnvelope(workbook);
        List<ImageAsset> assets = scanImages(workbook);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("command", "images");
        report.put("workbook", workbook.toAbsolutePath().normalize().toString());
        report.put("imageCount", assets.size());
        report.put("images", assets.stream().map(ImageAsset::metadata).toList());
        report.put("note", "Image bytes remain outside context until extract-image is called for one index.");
        return report;
    }

    static Map<String, Object> extractImage(Path workbook, int requestedIndex, Path output) throws Exception {
        if (requestedIndex < 1) {
            throw new IllegalArgumentException("--index must be one or greater.");
        }
        inspectEnvelope(workbook);
        List<ImageAsset> assets = scanImages(workbook);
        if (requestedIndex > assets.size()) {
            throw new IllegalArgumentException(
                    "Image index " + requestedIndex + " does not exist; workbook contains " + assets.size() + " images.");
        }

        ImageAsset asset = assets.get(requestedIndex - 1);
        Path normalizedOutput = output.toAbsolutePath().normalize();
        if (normalizedOutput.getParent() != null) {
            Files.createDirectories(normalizedOutput.getParent());
        }
        Files.write(normalizedOutput, asset.bytes);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("command", "extract-image");
        report.put("image", asset.metadata());
        report.put("output", normalizedOutput.toString());
        return report;
    }

    private static void streamRetailSheets(XSSFReader reader, RetailHandlerFactory handlerFactory) throws Exception {
        Styles styles = reader.getStylesTable();
        SharedStrings sharedStrings = reader.getSharedStringsTable();
        XSSFReader.SheetIterator iterator = reader.getSheetIterator();
        while (iterator.hasNext()) {
            try (InputStream sheetStream = iterator.next()) {
                XSSFSheetXMLHandler.SheetContentsHandler handler = handlerFactory.create(iterator.getSheetName());
                parseSheet(styles, sharedStrings, sheetStream, handler, false, null);
            }
        }
    }

    private static void parseSelectedSheet(
            XSSFReader reader,
            String requestedSheet,
            SliceCollector collector,
            boolean formulasNotResults,
            CellRangeAddress range)
            throws Exception {
        Styles styles = reader.getStylesTable();
        SharedStrings sharedStrings = reader.getSharedStringsTable();
        XSSFReader.SheetIterator iterator = reader.getSheetIterator();
        while (iterator.hasNext()) {
            try (InputStream sheetStream = iterator.next()) {
                if (requestedSheet.equals(iterator.getSheetName())) {
                    collector.sheetFound = true;
                    FormulaTrackingFilter filter =
                            parseSheet(styles, sharedStrings, sheetStream, collector, formulasNotResults, range);
                    collector.formulaCount = filter.formulaCount;
                    collector.formulaReferences.addAll(filter.formulaReferences);
                    return;
                }
            }
        }
    }

    private static FormulaTrackingFilter parseSheet(
            Styles styles,
            SharedStrings sharedStrings,
            InputStream sheetStream,
            XSSFSheetXMLHandler.SheetContentsHandler contentsHandler,
            boolean formulasNotResults,
            CellRangeAddress retainedFormulaRange)
            throws SAXException, IOException, ParserConfigurationException {
        XMLReader parser = XMLHelper.newXMLReader();
        FormulaTrackingFilter filter = new FormulaTrackingFilter(parser, retainedFormulaRange);
        XSSFSheetXMLHandler sheetHandler = new XSSFSheetXMLHandler(
                styles,
                sharedStrings,
                contentsHandler,
                DATA_FORMATTER,
                formulasNotResults);
        filter.setContentHandler(sheetHandler);
        filter.parse(new InputSource(sheetStream));
        return filter;
    }

    private static Map<String, String> readSheetVisibility(XSSFReader reader) throws Exception {
        Map<String, String> visibility = new LinkedHashMap<>();
        try (InputStream workbookData = reader.getWorkbookData()) {
            XMLReader parser = XMLHelper.newXMLReader();
            parser.setContentHandler(new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qualifiedName, Attributes attributes) {
                    if ("sheet".equals(localName) || "sheet".equals(qualifiedName)) {
                        String name = attributes.getValue("name");
                        String state = attributes.getValue("state");
                        visibility.put(name, state == null ? "visible" : state);
                    }
                }
            });
            parser.parse(new InputSource(workbookData));
        }
        return visibility;
    }

    private static List<ImageAsset> scanImages(Path workbook) throws Exception {
        configurePoiGuards();
        List<ImageAsset> assets = new ArrayList<>();
        Set<String> seenBindings = new LinkedHashSet<>();
        try (OPCPackage pkg = OPCPackage.open(workbook.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg, true);
            XSSFReader.SheetIterator iterator = reader.getSheetIterator();
            int index = 1;
            while (iterator.hasNext()) {
                try (InputStream ignored = iterator.next()) {
                    String sheetName = iterator.getSheetName();
                    PackagePart sheetPart = iterator.getSheetPart();
                    for (PackageRelationship drawingRelationship :
                            sheetPart.getRelationshipsByType(XSSFRelation.DRAWINGS.getRelation())) {
                        PackagePart drawingPart = sheetPart.getRelatedPart(drawingRelationship);
                        for (PackageRelationship imageRelationship :
                                drawingPart.getRelationshipsByType(XSSFRelation.IMAGES.getRelation())) {
                            PackagePart imagePart = drawingPart.getRelatedPart(imageRelationship);
                            String binding = sheetName + "|" + imagePart.getPartName().getName();
                            if (seenBindings.add(binding)) {
                                assets.add(ImageAsset.from(index++, sheetName, imagePart));
                            }
                        }
                    }
                }
            }
        }
        return assets;
    }

    private static WorkbookEnvelope inspectEnvelope(Path workbook) throws IOException {
        Path normalized = workbook.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Workbook does not exist: " + normalized);
        }
        if (!XLSX_EXTENSION.matcher(normalized.getFileName().toString()).matches()) {
            throw new IllegalArgumentException("Only .xlsx workbooks are supported in this version: " + normalized);
        }

        long fileBytes = Files.size(normalized);
        if (fileBytes > DEFAULT_MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "Workbook is " + fileBytes + " bytes; maximum is " + DEFAULT_MAX_FILE_BYTES + ".");
        }

        long expandedBytes = 0;
        int entries = 0;
        try (ZipFile zipFile = new ZipFile(normalized.toFile())) {
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                entries++;
                if (entries > DEFAULT_MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException(
                            "Workbook contains more than " + DEFAULT_MAX_ZIP_ENTRIES + " ZIP entries.");
                }
                long size = entry.getSize();
                if (size > 0) {
                    expandedBytes = Math.addExact(expandedBytes, size);
                }
                if (expandedBytes > DEFAULT_MAX_EXPANDED_BYTES) {
                    throw new IllegalArgumentException(
                            "Expanded workbook exceeds " + DEFAULT_MAX_EXPANDED_BYTES + " bytes.");
                }
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Expanded workbook size overflowed the safety counter.", exception);
        }

        return new WorkbookEnvelope(normalized, fileBytes, expandedBytes, entries);
    }

    private static void configurePoiGuards() {
        ZipSecureFile.setMaxFileCount(DEFAULT_MAX_ZIP_ENTRIES);
        ZipSecureFile.setMaxEntrySize(DEFAULT_MAX_EXPANDED_BYTES);
        ZipSecureFile.setMaxTextSize(DEFAULT_MAX_EXPANDED_BYTES);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static Map<String, Object> orderedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    private record WorkbookEnvelope(Path path, long fileBytes, long expandedBytes, int zipEntries) {
        Map<String, Object> toMap() {
            return orderedMap(
                    "path", path.toString(),
                    "fileBytes", fileBytes,
                    "expandedBytes", expandedBytes,
                    "zipEntries", zipEntries);
        }
    }

    private static final class SheetInventoryHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final int sampleLimit;
        private final List<Map<String, String>> samples = new ArrayList<>();
        private final List<Map<String, Object>> columns = new ArrayList<>();
        private Map<String, String> currentRow;
        private int currentRowNumber;
        private long rowCount;
        private long cellCount;
        private long serializedCharacters;
        private int maxColumn;

        private SheetInventoryHandler(int sampleLimit) {
            this.sampleLimit = Math.max(0, sampleLimit);
        }

        @Override
        public void startRow(int rowNumber) {
            currentRowNumber = rowNumber;
            currentRow = new LinkedHashMap<>();
            rowCount++;
        }

        @Override
        public void endRow(int rowNumber) {
            if (rowNumber > 0 && samples.size() < sampleLimit) {
                samples.add(currentRow);
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, org.apache.poi.xssf.usermodel.XSSFComment comment) {
            CellReference reference = new CellReference(cellReference);
            int column = reference.getCol();
            maxColumn = Math.max(maxColumn, column + 1);
            cellCount++;
            serializedCharacters += formattedValue.length() + 1L;

            if (currentRowNumber == 0) {
                columns.add(orderedColumn(column, cellReference, formattedValue));
            } else if (samples.size() < sampleLimit) {
                currentRow.put(cellReference, formattedValue);
            }
        }

        Map<String, Object> toMap(String name, String visibility) {
            return orderedMap(
                    "name", name,
                    "visibility", visibility,
                    "rowsIncludingHeader", rowCount,
                    "dataRows", Math.max(0, rowCount - 1),
                    "nonEmptyCells", cellCount,
                    "maxObservedColumn", maxColumn,
                    "columns", columns,
                    "sampleRows", samples);
        }

        private static Map<String, Object> orderedColumn(int index, String cellReference, String name) {
            return orderedMap(
                    "index", index,
                    "cell", cellReference,
                    "name", name);
        }
    }

    private static final class FormulaTrackingFilter extends XMLFilterImpl {

        private final CellRangeAddress retainedRange;
        private final Set<String> formulaReferences = new LinkedHashSet<>();
        private String currentCellReference;
        private long formulaCount;

        private FormulaTrackingFilter(XMLReader parent, CellRangeAddress retainedRange) {
            super(parent);
            this.retainedRange = retainedRange;
        }

        @Override
        public void startElement(String uri, String localName, String qualifiedName, Attributes attributes)
                throws SAXException {
            if ("c".equals(localName) || "c".equals(qualifiedName)) {
                currentCellReference = attributes.getValue("r");
            } else if ("f".equals(localName) || "f".equals(qualifiedName)) {
                formulaCount++;
                if (currentCellReference != null && isRetained(currentCellReference)) {
                    formulaReferences.add(currentCellReference);
                }
            }
            super.startElement(uri, localName, qualifiedName, attributes);
        }

        private boolean isRetained(String cellReference) {
            if (retainedRange == null) {
                return false;
            }
            CellReference reference = new CellReference(cellReference);
            return retainedRange.isInRange(reference.getRow(), reference.getCol());
        }
    }

    private static final class SliceCollector implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final CellRangeAddress range;
        private final Map<String, String> values = new LinkedHashMap<>();
        private final Set<String> formulaReferences = new LinkedHashSet<>();
        private boolean sheetFound;
        private long formulaCount;

        private SliceCollector(CellRangeAddress range) {
            this.range = range;
        }

        @Override
        public void startRow(int rowNumber) {
        }

        @Override
        public void endRow(int rowNumber) {
        }

        @Override
        public void cell(String cellReference, String formattedValue, org.apache.poi.xssf.usermodel.XSSFComment comment) {
            CellReference reference = new CellReference(cellReference);
            if (range.isInRange(reference.getRow(), reference.getCol())) {
                values.put(cellReference, formattedValue);
            }
        }

        Map<String, String> formulasOnly() {
            Map<String, String> formulas = new LinkedHashMap<>();
            for (String reference : formulaReferences) {
                if (values.containsKey(reference)) {
                    formulas.put(reference, values.get(reference));
                }
            }
            return formulas;
        }

        List<Map<String, Object>> rows(Map<String, String> formulas) {
            Map<Integer, List<Map<String, Object>>> byRow = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                CellReference reference = new CellReference(entry.getKey());
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("cell", entry.getKey());
                cell.put("value", entry.getValue());
                if (formulas.containsKey(entry.getKey())) {
                    cell.put("formula", formulas.get(entry.getKey()));
                }
                byRow.computeIfAbsent(reference.getRow() + 1, ignored -> new ArrayList<>()).add(cell);
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map.Entry<Integer, List<Map<String, Object>>> entry : byRow.entrySet()) {
                entry.getValue().sort(Comparator.comparingInt(cell -> new CellReference((String) cell.get("cell")).getCol()));
                rows.add(orderedMap("row", entry.getKey(), "cells", entry.getValue()));
            }
            return rows;
        }
    }

    private interface RetailHandlerFactory {
        XSSFSheetXMLHandler.SheetContentsHandler create(String sheetName);
    }

    private record RetailAuditHandlerFactory(RetailAccumulator accumulator) implements RetailHandlerFactory {
        @Override
        public XSSFSheetXMLHandler.SheetContentsHandler create(String sheetName) {
            return new RetailRowHandler(sheetName, accumulator::accept);
        }
    }

    private record RetailEvidenceHandlerFactory(RetailEvidenceCollector collector) implements RetailHandlerFactory {
        @Override
        public XSSFSheetXMLHandler.SheetContentsHandler create(String sheetName) {
            return new RetailRowHandler(sheetName, collector::accept);
        }
    }

    @FunctionalInterface
    private interface RetailRowConsumer {
        void accept(RetailRow row);
    }

    private static final class RetailRowHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final String sheetName;
        private final RetailRowConsumer consumer;
        private final Map<Integer, String> values = new HashMap<>();
        private Map<String, Integer> header;
        private int rowNumber;
        private long currentRowCharacters;

        private RetailRowHandler(String sheetName, RetailRowConsumer consumer) {
            this.sheetName = sheetName;
            this.consumer = consumer;
        }

        @Override
        public void startRow(int rowNumber) {
            this.rowNumber = rowNumber;
            values.clear();
            currentRowCharacters = 0;
        }

        @Override
        public void endRow(int rowNumber) {
            if (rowNumber == 0) {
                header = new HashMap<>();
                for (Map.Entry<Integer, String> entry : values.entrySet()) {
                    header.put(normalizeHeader(entry.getValue()), entry.getKey());
                }
                return;
            }
            if (header == null) {
                return;
            }

            RetailRow row = new RetailRow(
                    sheetName,
                    rowNumber + 1,
                    value("invoice", "invoiceno"),
                    value("stockcode"),
                    value("description"),
                    decimal(value("quantity")),
                    value("invoicedate"),
                    decimal(value("price", "unitprice")),
                    normalizeCustomer(value("customerid")),
                    value("country"),
                    currentRowCharacters);
            consumer.accept(row);
        }

        @Override
        public void cell(String cellReference, String formattedValue, org.apache.poi.xssf.usermodel.XSSFComment comment) {
            int column = new CellReference(cellReference).getCol();
            values.put(column, formattedValue);
            currentRowCharacters += formattedValue.length() + 1L;
        }

        private String value(String... aliases) {
            for (String alias : aliases) {
                Integer column = header.get(alias);
                if (column != null) {
                    return values.get(column);
                }
            }
            return null;
        }

        private static String normalizeHeader(String value) {
            return value == null
                    ? ""
                    : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }

        private static String normalizeCustomer(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
        }

        private static BigDecimal decimal(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return new BigDecimal(value.replace(",", ""));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private record RetailRow(
            String sheet,
            int rowNumber,
            String invoice,
            String stockCode,
            String description,
            BigDecimal quantity,
            String invoiceDate,
            BigDecimal price,
            String customerId,
            String country,
            long serializedCharacters) {

        boolean valid() {
            return invoice != null
                    && stockCode != null
                    && quantity != null
                    && price != null
                    && country != null;
        }

        BigDecimal amount() {
            return quantity.multiply(price);
        }

        boolean isReturn() {
            return quantity.signum() < 0 && price.signum() > 0;
        }

        boolean isCancellation() {
            return invoice.toUpperCase(Locale.ROOT).startsWith("C");
        }

        boolean isMerchandise() {
            return stockCode.matches("[0-9]{5}[A-Z]?");
        }

        Map<String, Object> evidence() {
            return orderedMap(
                    "sheet", sheet,
                    "range", "A" + rowNumber + ":H" + rowNumber,
                    "invoice", invoice,
                    "stockCode", stockCode,
                    "description", description,
                    "quantity", quantity,
                    "priceGbp", price,
                    "lineAmountGbp", money(amount()),
                    "invoiceDate", invoiceDate);
        }
    }

    private static final class RetailAccumulator {

        private final Map<String, BigDecimal> customerNetRevenue = new HashMap<>();
        private final Map<String, String> customerCountry = new HashMap<>();
        private final Map<String, BigDecimal> productReturnedValue = new HashMap<>();
        private final Map<String, BigDecimal> allReturnedCodeValue = new HashMap<>();
        private final Map<String, String> productDescription = new HashMap<>();
        private long dataRows;
        private long validRows;
        private long invalidRows;
        private long missingCustomerRows;
        private long cancellationRows;
        private long serializedCharacters;
        private BigDecimal grossSales = BigDecimal.ZERO;
        private BigDecimal returnedGoods = BigDecimal.ZERO;
        private BigDecimal netRevenue = BigDecimal.ZERO;

        void accept(RetailRow row) {
            dataRows++;
            serializedCharacters += row.serializedCharacters();
            if (!row.valid()) {
                invalidRows++;
                return;
            }

            validRows++;
            BigDecimal amount = row.amount();
            netRevenue = netRevenue.add(amount);
            if (row.quantity().signum() > 0 && row.price().signum() > 0) {
                grossSales = grossSales.add(amount);
            }
            if (row.isReturn()) {
                BigDecimal returned = amount.abs();
                returnedGoods = returnedGoods.add(returned);
                allReturnedCodeValue.merge(row.stockCode(), returned, BigDecimal::add);
                if (row.isMerchandise()) {
                    productReturnedValue.merge(row.stockCode(), returned, BigDecimal::add);
                }
                if (row.description() != null && !row.description().isBlank()) {
                    productDescription.putIfAbsent(row.stockCode(), row.description());
                }
            }
            if (row.isCancellation()) {
                cancellationRows++;
            }
            if (row.customerId() == null) {
                missingCustomerRows++;
            } else if (!"United Kingdom".equalsIgnoreCase(row.country())) {
                customerNetRevenue.merge(row.customerId(), amount, BigDecimal::add);
                customerCountry.putIfAbsent(row.customerId(), row.country());
            }
        }

        String topCustomer() {
            return topKey(customerNetRevenue);
        }

        String topReturnedProduct() {
            return topKey(productReturnedValue);
        }

        String naiveTopReturnedCode() {
            return topKey(allReturnedCodeValue);
        }

        private static String topKey(Map<String, BigDecimal> values) {
            return values.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }
    }

    private static final class RetailEvidenceCollector {

        private final String customerId;
        private final String stockCode;
        private final int limit;
        private final List<Map<String, Object>> customerEvidence = new ArrayList<>();
        private final List<Map<String, Object>> productEvidence = new ArrayList<>();

        private RetailEvidenceCollector(String customerId, String stockCode, int limit) {
            this.customerId = customerId;
            this.stockCode = stockCode;
            this.limit = Math.max(1, limit);
        }

        void accept(RetailRow row) {
            if (!row.valid()) {
                return;
            }
            if (customerId.equals(row.customerId())) {
                addBounded(customerEvidence, row.evidence());
            }
            if (stockCode.equals(row.stockCode()) && row.isReturn()) {
                addBounded(productEvidence, row.evidence());
            }
        }

        private void addBounded(List<Map<String, Object>> evidence, Map<String, Object> candidate) {
            evidence.add(candidate);
            evidence.sort(Comparator.comparing(
                    item -> ((BigDecimal) item.get("lineAmountGbp")).abs(),
                    Comparator.reverseOrder()));
            if (evidence.size() > limit) {
                evidence.remove(evidence.size() - 1);
            }
        }
    }

    private record ImageAsset(
            int index,
            String sheet,
            String range,
            String contentType,
            String extension,
            int bytesLength,
            String sha256,
            byte[] bytes) {

        static ImageAsset from(int index, String sheet, PackagePart imagePart) throws IOException {
            byte[] bytes;
            try (InputStream input = imagePart.getInputStream()) {
                bytes = input.readAllBytes();
            }
            String partName = imagePart.getPartName().getName();
            int dot = partName.lastIndexOf('.');
            String extension = dot < 0 ? "bin" : partName.substring(dot + 1).toLowerCase(Locale.ROOT);
            return new ImageAsset(
                    index,
                    sheet,
                    "not-resolved",
                    imagePart.getContentType(),
                    extension,
                    bytes.length,
                    hexSha256(bytes),
                    bytes);
        }

        Map<String, Object> metadata() {
            return orderedMap(
                    "index", index,
                    "sheet", sheet,
                    "anchorRange", range,
                    "contentType", contentType,
                    "extension", extension,
                    "bytes", bytesLength,
                    "sha256", sha256);
        }

        private static String hexSha256(byte[] bytes) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
                StringBuilder hex = new StringBuilder(digest.length * 2);
                for (byte value : digest) {
                    hex.append(String.format("%02x", value));
                }
                return hex.toString();
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable.", exception);
            }
        }
    }
}
