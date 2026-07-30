///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS org.apache.poi:poi-ooxml:5.5.1
//DEPS org.apache.logging.log4j:log4j-core:2.26.1
//COMPILE_OPTIONS -proc:none
//JAVA_OPTIONS -Djava.awt.headless=true

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.FontUnderline;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class CreateFixture {

    public static void main(String... args) throws Exception {
        Path output = args.length == 0
                ? Path.of("build", "visual-fixture.xlsx")
                : Path.of(args[0]);
        create(output);
        System.out.println(output.toAbsolutePath().normalize());
    }

    static void create(Path output) throws IOException {
        Path normalized = output.toAbsolutePath().normalize();
        if (normalized.getParent() != null) {
            Files.createDirectories(normalized.getParent());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle heading = headingStyle(workbook);
            CellStyle money = moneyStyle(workbook);
            Sheet dashboard = workbook.createSheet("Dashboard");
            Sheet transactions = workbook.createSheet("Transactions");
            Sheet rules = workbook.createSheet("Rules");

            dashboard.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
            Cell title = dashboard.createRow(0).createCell(0);
            title.setCellValue("Returns investigation");
            title.setCellStyle(heading);

            dashboard.createRow(2).createCell(0).setCellValue("Cached returned value");
            Cell cachedTotal = dashboard.getRow(2).createCell(1);
            cachedTotal.setCellFormula("SUM('Transactions'!D2:D4)");
            cachedTotal.setCellStyle(money);

            Row transactionHeader = transactions.createRow(0);
            String[] headers = { "Invoice", "StockCode", "Description", "ReturnedValue" };
            for (int index = 0; index < headers.length; index++) {
                Cell cell = transactionHeader.createCell(index);
                cell.setCellValue(headers[index]);
                cell.setCellStyle(heading);
            }
            addTransaction(transactions, money, 1, "C100001", "23843", "PAPER CRAFT , LITTLE BIRDIE", 10);
            addTransaction(transactions, money, 2, "C100002", "23843", "PAPER CRAFT , LITTLE BIRDIE", 20);
            addTransaction(transactions, money, 3, "C100003", "23843", "PAPER CRAFT , LITTLE BIRDIE", 30);

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateFormulaCell(cachedTotal);
            transactions.getRow(3).getCell(3).setCellValue(50);

            rules.createRow(0).createCell(0).setCellValue("Rule");
            rules.getRow(0).getCell(0).setCellStyle(heading);
            rules.createRow(1).createCell(0).setCellValue("Merchandise codes match [0-9]{5}[A-Z]?");
            workbook.setSheetHidden(workbook.getSheetIndex(rules), true);

            Name merchandiseRule = workbook.createName();
            merchandiseRule.setNameName("MerchandiseRule");
            merchandiseRule.setRefersToFormula("Rules!$A$2");

            byte[] evidenceImage = createEvidenceImage();
            int pictureIndex = workbook.addPicture(evidenceImage, Workbook.PICTURE_TYPE_PNG);
            CreationHelper helper = workbook.getCreationHelper();
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(1);
            anchor.setRow1(5);
            anchor.setCol2(6);
            anchor.setRow2(18);
            Picture picture = dashboard.createDrawingPatriarch().createPicture(anchor, pictureIndex);
            picture.resize(0.72);

            transactions.setColumnWidth(0, 14 * 256);
            transactions.setColumnWidth(1, 13 * 256);
            transactions.setColumnWidth(2, 34 * 256);
            transactions.setColumnWidth(3, 18 * 256);
            dashboard.setColumnWidth(0, 32 * 256);
            dashboard.setColumnWidth(1, 20 * 256);

            try (OutputStream outputStream = Files.newOutputStream(normalized)) {
                workbook.write(outputStream);
            }
        }
    }

    private static void addTransaction(
            Sheet sheet,
            CellStyle money,
            int rowIndex,
            String invoice,
            String stockCode,
            String description,
            double returnedValue) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(invoice);
        row.createCell(1).setCellValue(stockCode);
        row.createCell(2).setCellValue(description);
        Cell returnedValueCell = row.createCell(3);
        returnedValueCell.setCellValue(returnedValue);
        returnedValueCell.setCellStyle(money);
    }

    private static CellStyle headingStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);

        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setUnderline(FontUnderline.NONE.getByteValue());
        style.setFont(font);
        return style;
    }

    private static CellStyle moneyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("£#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private static byte[] createEvidenceImage() throws IOException {
        BufferedImage image = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(248, 247, 243));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());

            graphics.setColor(new Color(45, 39, 134));
            graphics.fillRoundRect(50, 50, 540, 260, 28, 28);
            graphics.setColor(new Color(230, 30, 115));
            graphics.fillRoundRect(86, 90, 110, 110, 18, 18);

            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            graphics.drawString("RETURN REVIEW", 230, 128);
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 22));
            graphics.drawString("C100003  |  23843", 230, 172);
            graphics.drawString("expected: GBP 50.00", 230, 210);

            graphics.setStroke(new BasicStroke(5));
            graphics.drawLine(108, 145, 137, 174);
            graphics.drawLine(137, 174, 178, 118);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        }
    }
}
