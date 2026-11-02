package uz.karvon.admin.api;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.payment.PaymentTestSupport.as;

/**
 * Dashboard and reports on orders seeded in February and March 2020 (no other test makes orders that old).
 * Settings: shipping 70 000 + markup 10 000 per kg; customer rate 1 800 until 2020-03-03, then 2 000.
 * <pre>
 * A 2020-03-02 DELIVERED  paid 278 000, fee 18 000, final shipping 80 000 (markup 10 000, cargo 70 000),
 *                         items 90 CNY x1 bought 03-02 (162 000) + (10 + 2 China shipping) CNY x2 bought 03-04 (48 000),
 *                         profit 278 000 - 210 000 - 70 000 = -2 000
 * B 2020-03-02 PAID       paid 145 000, fee 15 000, prepaid shipping 40 000 (markup 5 000, cargo 35 000), nothing bought,
 *                         profit 110 000
 * C 2020-03-05 CANCELLED  paid and fully refunded, fee 15 000: profit 0
 * D 2020-03-05 AWAITING_PAYMENT, fee 15 000: profit 0
 * E 2020-02-28 DELIVERED  paid 50 000, prepaid shipping 16 000 (cargo 14 000), 10 CNY bought (18 000): profit 18 000
 * </pre>
 */
class AdminReportIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final String MARCH = "from=2020-03-01&to=2020-03-31";
    private static boolean seeded;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String admin;

    @BeforeEach
    void seed() {
        admin = TestLogins.adminToken(mvc);
        synchronized (AdminReportIntegrationTest.class) {
            if (!seeded) {
                seedOrders();
                seeded = true;
            }
        }
    }

    @Test
    void dashboardNumbersOnSeededOrders() {
        MvcTestResult result = as(mvc, admin, mvc.get().uri("/api/v1/admin/dashboard?" + MARCH));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.from").isEqualTo("2020-03-01");
        assertThat(result).bodyJson().extractingPath("$.ordersInPeriod").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.revenueUzs").isEqualTo(423000);
        assertThat(result).bodyJson().extractingPath("$.serviceIncomeUzs").isEqualTo(33000);
        assertThat(result).bodyJson().extractingPath("$.shippingIncomeUzs").isEqualTo(15000);
        // 423 000 / 3 paid orders
        assertThat(result).bodyJson().extractingPath("$.averageOrderUzs").isEqualTo(141000);
        assertThat(result).bodyJson().extractingPath("$.ordersByStatus.length()").isEqualTo(12);
        assertThat(result).bodyJson().extractingPath("$.ordersByStatus.DELIVERED").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.ordersByStatus.PAID").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.ordersByStatus.CANCELLED").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.ordersByStatus.AWAITING_PAYMENT").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.ordersByStatus.IN_TRANSIT").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.dailySeries.length()").isEqualTo(31);
        assertThat(result).bodyJson().extractingPath("$.dailySeries[0].orders").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.dailySeries[1].date").isEqualTo("2020-03-02");
        assertThat(result).bodyJson().extractingPath("$.dailySeries[1].orders").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.dailySeries[1].revenue").isEqualTo(423000);
        assertThat(result).bodyJson().extractingPath("$.dailySeries[4].orders").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.dailySeries[4].revenue").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.ordersToday").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.delayedCount").isNotNull();
    }

    @Test
    void dashboardIsForAdminAndOperatorAndChecksThePeriod() {
        String operator = TestLogins.staffToken(mvc, "OPERATOR");
        String buyer = TestLogins.staffToken(mvc, "BUYER");

        assertThat(as(mvc, operator, mvc.get().uri("/api/v1/admin/dashboard"))).hasStatusOk();
        assertThat(as(mvc, operator, mvc.get().uri("/api/v1/admin/dashboard")))
                .bodyJson().extractingPath("$.dailySeries.length()").isEqualTo(30);
        assertThat(as(mvc, buyer, mvc.get().uri("/api/v1/admin/dashboard"))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(as(mvc, operator, mvc.get().uri("/api/v1/admin/reports/orders?" + MARCH))).hasStatus(HttpStatus.FORBIDDEN);

        MvcTestResult invalid = as(mvc, admin, mvc.get().uri("/api/v1/admin/dashboard?from=2020-03-10&to=2020-03-01"));
        assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(invalid).bodyJson().extractingPath("$.code").isEqualTo("REPORT_PERIOD_INVALID");
        assertThat(as(mvc, admin, mvc.get().uri("/api/v1/admin/dashboard?from=2019-01-01&to=2020-03-01")))
                .bodyJson().extractingPath("$.code").isEqualTo("REPORT_PERIOD_INVALID");
    }

    @Test
    void ordersReportAsJson() {
        MvcTestResult result = as(mvc, admin, mvc.get().uri("/api/v1/admin/reports/orders?" + MARCH));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.rows.length()").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.rows[0].number").isEqualTo("RPT-A");
        assertThat(result).bodyJson().extractingPath("$.rows[0].customerPhone").isEqualTo(phone);
        assertThat(result).bodyJson().extractingPath("$.rows[0].date").isEqualTo("2020-03-02T10:00:00");
        assertThat(result).bodyJson().extractingPath("$.rows[0].totalPaidUzs").isEqualTo(278000);
        assertThat(result).bodyJson().extractingPath("$.rows[0].goodsCostUzs").isEqualTo(210000);
        assertThat(result).bodyJson().extractingPath("$.rows[0].shippingCostUzs").isEqualTo(70000);
        assertThat(result).bodyJson().extractingPath("$.rows[0].serviceFeeUzs").isEqualTo(18000);
        assertThat(result).bodyJson().extractingPath("$.rows[0].profitUzs").isEqualTo(-2000);
        assertThat(result).bodyJson().extractingPath("$.rows[1].profitUzs").isEqualTo(110000);
        assertThat(result).bodyJson().extractingPath("$.rows[2].status").isEqualTo("CANCELLED");
        assertThat(result).bodyJson().extractingPath("$.rows[2].profitUzs").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.totals.orders").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.totals.totalPaidUzs").isEqualTo(423000);
        assertThat(result).bodyJson().extractingPath("$.totals.goodsCostUzs").isEqualTo(210000);
        assertThat(result).bodyJson().extractingPath("$.totals.shippingCostUzs").isEqualTo(105000);
        assertThat(result).bodyJson().extractingPath("$.totals.serviceFeeUzs").isEqualTo(63000);
        assertThat(result).bodyJson().extractingPath("$.totals.profitUzs").isEqualTo(108000);
    }

    @Test
    void ordersReportExcelOpensWithTheRightRows() throws IOException {
        MvcTestResult result = as(mvc, admin, mvc.get().uri("/api/v1/admin/reports/orders?" + MARCH + "&format=xlsx"));

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains("karvon-orders-2020-03-01_2020-03-31.xlsx");
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            // header + 4 orders + total
            assertThat(sheet.getLastRowNum()).isEqualTo(5);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Raqam");
            assertThat(sheet.getRow(0).getCell(8).getStringCellValue()).isEqualTo("Foyda (so'm)");
            Row first = sheet.getRow(1);
            assertThat(first.getCell(0).getStringCellValue()).isEqualTo("RPT-A");
            assertThat(first.getCell(1).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2020, 3, 2, 10, 0));
            assertThat(first.getCell(2).getStringCellValue()).isEqualTo(phone);
            assertThat(first.getCell(3).getStringCellValue()).isEqualTo("DELIVERED");
            assertThat(first.getCell(4).getNumericCellValue()).isEqualTo(278000);
            assertThat(first.getCell(5).getNumericCellValue()).isEqualTo(210000);
            assertThat(first.getCell(6).getNumericCellValue()).isEqualTo(70000);
            assertThat(first.getCell(7).getNumericCellValue()).isEqualTo(18000);
            assertThat(first.getCell(8).getNumericCellValue()).isEqualTo(-2000);
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("RPT-D");
            Row total = sheet.getRow(5);
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Jami");
            assertThat(total.getCell(4).getNumericCellValue()).isEqualTo(423000);
            assertThat(total.getCell(8).getNumericCellValue()).isEqualTo(108000);
        }

        assertThat(as(mvc, admin, mvc.get().uri("/api/v1/admin/reports/orders?format=pdf"))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void monthlyReport() {
        MvcTestResult result = as(mvc, admin, mvc.get().uri("/api/v1/admin/reports/monthly?year=2020"));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.months.length()").isEqualTo(12);
        assertThat(result).bodyJson().extractingPath("$.months[0].orders").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.months[1].month").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.months[1].orders").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.months[1].revenueUzs").isEqualTo(50000);
        assertThat(result).bodyJson().extractingPath("$.months[1].profitUzs").isEqualTo(18000);
        assertThat(result).bodyJson().extractingPath("$.months[2].orders").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.months[2].revenueUzs").isEqualTo(423000);
        assertThat(result).bodyJson().extractingPath("$.months[2].profitUzs").isEqualTo(108000);
        assertThat(result).bodyJson().extractingPath("$.orders").isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.revenueUzs").isEqualTo(473000);
        assertThat(result).bodyJson().extractingPath("$.profitUzs").isEqualTo(126000);

        assertThat(as(mvc, admin, mvc.get().uri("/api/v1/admin/reports/monthly?year=1990")))
                .bodyJson().extractingPath("$.code").isEqualTo("REPORT_YEAR_INVALID");
    }

    // --- seed data

    private static String phone;

    private void seedOrders() {
        UUID customer = UUID.randomUUID();
        phone = TestLogins.newPhone();
        jdbc.update("""
                INSERT INTO users (id, phone, full_name, language, status, created_at, updated_at, version)
                VALUES (?, ?, 'Hisobot Mijoz', 'UZ', 'ACTIVE', now(), now(), 0)""", customer, phone);
        UUID settings = settings("1800", "2020-01-01T00:00:00Z");
        settings("2000", "2020-03-03T00:00:00Z");

        UUID a = order("RPT-A", customer, settings, "DELIVERED", at(2020, 3, 2, 10), true,
                "180000", "18000", "40000", "80000", "238000", "278000", "0");
        item(a, "100", "0", 1, "90", "PURCHASED", at(2020, 3, 2, 12));
        item(a, "10", "2", 2, "10", "PURCHASED", at(2020, 3, 4, 12));
        UUID b = order("RPT-B", customer, settings, "PAID", at(2020, 3, 2, 15), true,
                "90000", "15000", "40000", null, "145000", "145000", "0");
        item(b, "50", "0", 1, null, "PENDING", null);
        UUID c = order("RPT-C", customer, settings, "CANCELLED", at(2020, 3, 5, 12), true,
                "45000", "15000", "40000", null, "100000", "100000", "100000");
        item(c, "25", "0", 1, null, "PENDING", null);
        UUID d = order("RPT-D", customer, settings, "AWAITING_PAYMENT", at(2020, 3, 5, 13), false,
                "45000", "15000", "40000", null, "100000", "0", "0");
        item(d, "25", "0", 1, null, "PENDING", null);
        UUID e = order("RPT-E", customer, settings, "DELIVERED", at(2020, 2, 28, 9), true,
                "18000", "16000", "16000", null, "50000", "50000", "0");
        item(e, "10", "0", 1, "10", "PURCHASED", at(2020, 2, 28, 12));
    }

    private UUID settings(String rate, String effectiveFrom) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pricing_settings (id, customer_rate, fee_percent, min_service_fee, shipping_cost_per_kg,
                                              our_markup_per_kg, min_weight_kg, default_weight_kg,
                                              price_change_threshold_percent, effective_from, changed_by)
                VALUES (?, ?, 10, 15000, 70000, 10000, 0.5, 0.5, 3, ?, NULL)""",
                id, new BigDecimal(rate), Timestamp.from(Instant.parse(effectiveFrom)));
        return id;
    }

    private UUID order(String number, UUID customer, UUID settings, String status, Instant createdAt, boolean paid,
                       String goods, String fee, String prepaidShipping, String finalShipping, String total,
                       String paidUzs, String refunded) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO orders (id, number, customer_id, status, delivery_method, payment_method,
                                            pricing_settings_id, goods_uzs, service_fee_uzs, prepaid_shipping_uzs,
                                            final_shipping_uzs, total_uzs, paid_uzs, refunded_uzs, paid_at,
                                            created_at, updated_at, version)
                        VALUES (?, ?, ?, ?, 'CARGO_PICKUP', 'PAYME', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""",
                id, number, customer, status, settings, new BigDecimal(goods), new BigDecimal(fee),
                new BigDecimal(prepaidShipping), finalShipping == null ? null : new BigDecimal(finalShipping),
                new BigDecimal(total), new BigDecimal(paidUzs), new BigDecimal(refunded),
                paid ? Timestamp.from(createdAt.plusSeconds(600)) : null, Timestamp.from(createdAt),
                Timestamp.from(createdAt));
        return id;
    }

    private void item(UUID order, String priceCny, String chinaShippingCny, int quantity, String actualPriceCny,
                      String status, Instant purchasedAt) {
        jdbc.update("""
                        INSERT INTO order_items (id, order_id, external_product_id, sku_id, title, sku_properties,
                                                 price_cny, china_shipping_cny, quantity, goods_uzs, service_fee_uzs,
                                                 shipping_uzs, total_uzs, chargeable_weight_kg, actual_price_cny,
                                                 status, purchased_at, created_at, updated_at, version)
                        VALUES (?, ?, '680000000001', '680000000001001', 'Hisobot tovari', '[]', ?, ?, ?, 0, 0, 0, 0,
                                0.5, ?, ?, ?, now(), now(), 0)""",
                UUID.randomUUID(), order, new BigDecimal(priceCny), new BigDecimal(chinaShippingCny), quantity,
                actualPriceCny == null ? null : new BigDecimal(actualPriceCny), status,
                purchasedAt == null ? null : Timestamp.from(purchasedAt));
    }

    /** A time in Tashkent. */
    private static Instant at(int year, int month, int day, int hour) {
        return LocalDateTime.of(year, month, day, hour, 0).atZone(TASHKENT).toInstant();
    }
}
