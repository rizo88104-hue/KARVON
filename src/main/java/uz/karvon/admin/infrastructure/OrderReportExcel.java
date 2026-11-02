package uz.karvon.admin.infrastructure;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import uz.karvon.admin.api.dto.OrderReportResponse;
import uz.karvon.admin.api.dto.OrderReportRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * The orders report as an .xlsx file (Apache POI): a header row, one row per order and a "Jami" (total) row.
 * Money cells are numbers in so'm, so Excel can sum and filter them.
 */
@Component
public class OrderReportExcel {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final List<String> HEADERS = List.of("Raqam", "Sana", "Mijoz telefoni", "Holat", "To'langan (so'm)",
            "Tovar tannarxi (so'm)", "Yetkazish xarajati (so'm)", "Xizmat haqi (so'm)", "Foyda (so'm)");

    public byte[] write(OrderReportResponse report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CreationHelper helper = workbook.getCreationHelper();
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle header = workbook.createCellStyle();
            header.setFont(bold);
            CellStyle date = workbook.createCellStyle();
            date.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
            CellStyle money = workbook.createCellStyle();
            money.setDataFormat(helper.createDataFormat().getFormat("#,##0"));
            CellStyle totalMoney = workbook.createCellStyle();
            totalMoney.cloneStyleFrom(money);
            totalMoney.setFont(bold);

            // A sheet name may have at most 31 characters; the period is in the file name.
            Sheet sheet = workbook.createSheet("Buyurtmalar");
            Row head = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(HEADERS.get(i));
                cell.setCellStyle(header);
            }
            int r = 1;
            for (OrderReportRow order : report.rows()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(order.number());
                Cell created = row.createCell(1);
                created.setCellValue(order.date());
                created.setCellStyle(date);
                row.createCell(2).setCellValue(order.customerPhone());
                row.createCell(3).setCellValue(order.status().name());
                money(row, 4, order.totalPaidUzs(), money);
                money(row, 5, order.goodsCostUzs(), money);
                money(row, 6, order.shippingCostUzs(), money);
                money(row, 7, order.serviceFeeUzs(), money);
                money(row, 8, order.profitUzs(), money);
            }
            OrderReportResponse.Totals totals = report.totals();
            Row total = sheet.createRow(r);
            Cell label = total.createCell(0);
            label.setCellValue("Jami");
            label.setCellStyle(header);
            total.createCell(3).setCellValue(totals.orders());
            money(total, 4, totals.totalPaidUzs(), totalMoney);
            money(total, 5, totals.goodsCostUzs(), totalMoney);
            money(total, 6, totals.shippingCostUzs(), totalMoney);
            money(total, 7, totals.serviceFeeUzs(), totalMoney);
            money(total, 8, totals.profitUzs(), totalMoney);

            sheet.createFreezePane(0, 1);
            for (int i = 0; i < HEADERS.size(); i++) {
                sheet.setColumnWidth(i, (i == 1 || i == 2 ? 18 : 16) * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    // UZS amounts are whole so'm, so a double holds them exactly (they are far below 2^53).
    private static void money(Row row, int column, BigDecimal amount, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(amount.doubleValue());
        cell.setCellStyle(style);
    }
}
