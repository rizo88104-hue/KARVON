package uz.karvon.admin.api;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.admin.api.dto.DashboardResponse;
import uz.karvon.admin.api.dto.MonthlyReportResponse;
import uz.karvon.admin.api.dto.OrderReportResponse;
import uz.karvon.admin.application.ReportService;
import uz.karvon.admin.infrastructure.OrderReportExcel;

import java.time.LocalDate;

/**
 * Dates are days in Asia/Tashkent (ISO, e.g. 2026-10-01), both included. Without dates: the last 30 days.
 * A period may be at most 366 days.
 */
@Tag(name = "Admin: dashboard and reports")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reports;
    private final OrderReportExcel excel;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public DashboardResponse dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.dashboard(from, to);
    }

    /** One row per order with cost and profit; {@code format=xlsx} downloads an Excel file. */
    @GetMapping("/reports/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> orders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(example = "json") @RequestParam(defaultValue = "json") @Pattern(regexp = "json|xlsx") String format) {
        OrderReportResponse report = reports.orders(from, to);
        if (!"xlsx".equals(format)) {
            return ResponseEntity.ok(report);
        }
        String fileName = "karvon-orders-%s_%s.xlsx".formatted(report.from(), report.to());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OrderReportExcel.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(excel.write(report));
    }

    /** Orders, revenue and profit per month; without {@code year}: this year. */
    @GetMapping("/reports/monthly")
    @PreAuthorize("hasRole('ADMIN')")
    public MonthlyReportResponse monthly(@RequestParam(required = false) Integer year) {
        return reports.monthly(year);
    }
}
