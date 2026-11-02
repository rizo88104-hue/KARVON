package uz.karvon.notification.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

    @GetMapping
    public PageResponse<NotificationResponse> search(@RequestParam(required = false) NotificationStatus status,
                                                     @PageableDefault(size = 20, sort = "createdAt",
                                                             direction = Sort.Direction.DESC) Pageable pageable) {
        return notifications.search(status, pageable);
    }

    /** Sends it again now (also after the automatic retries ran out). A SENT notification gives 409. */
    @PostMapping("/{id}/retry")
    public NotificationResponse retry(@PathVariable UUID id) {
        return notifications.retry(id);
    }
}
