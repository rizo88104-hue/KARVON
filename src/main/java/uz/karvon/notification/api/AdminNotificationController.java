package uz.karvon.notification.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.PageResponse;
import uz.karvon.notification.api.dto.NotificationResponse;
import uz.karvon.notification.application.NotificationService;
import uz.karvon.notification.domain.NotificationStatus;

import java.util.UUID;

@Tag(name = "Admin: notifications")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notifications;

    /** Newest first; {@code status} PENDING, SENT or FAILED (all if omitted). */
    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(required = false) NotificationStatus status,
                                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notifications.search(status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /** Sends a not yet sent notification again now and returns the result (409 if it was already sent). */
    @PostMapping("/{id}/retry")
    public NotificationResponse retry(@PathVariable UUID id) {
        return notifications.retry(id);
    }
}
