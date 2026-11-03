package uz.karvon.notification.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.common.api.PageResponse;
import uz.karvon.notification.api.dto.InAppNotificationResponse;
import uz.karvon.notification.api.dto.NotificationSettingsResponse;
import uz.karvon.notification.api.dto.TelegramLinkResponse;
import uz.karvon.notification.api.dto.UnreadCountResponse;
import uz.karvon.notification.api.dto.UpdateNotificationSettingsRequest;
import uz.karvon.notification.application.NotificationService;
import uz.karvon.notification.application.TelegramService;
import uz.karvon.translation.domain.Lang;
import uz.karvon.user.domain.Language;

import java.util.UUID;

@Tag(name = "Me: notifications")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeNotificationController {

    private final NotificationService notifications;
    private final TelegramService telegram;

    /** A one-time deep link to the bot; pressing Start in Telegram links the chat. */
    @GetMapping("/telegram-link")
    public TelegramLinkResponse telegramLink(Authentication authentication) {
        return telegram.createLink(CurrentUser.id(authentication));
    }

    /** In-app notifications (the bell): what could not go to Telegram or SMS. Newest first, in the site language. */
    @GetMapping("/notifications")
    public PageResponse<InAppNotificationResponse> notifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Language language = Lang.ofCustomer(LocaleContextHolder.getLocale()) == Lang.RU ? Language.RU : Language.UZ;
        return notifications.inApp(CurrentUser.id(authentication), language,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/notifications/unread-count")
    public UnreadCountResponse unreadCount(Authentication authentication) {
        return new UnreadCountResponse(notifications.unreadCount(CurrentUser.id(authentication)));
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(Authentication authentication, @PathVariable UUID id) {
        notifications.markRead(CurrentUser.id(authentication), id);
    }

    /** Marks all of them read. */
    @PostMapping("/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(Authentication authentication) {
        notifications.markAllRead(CurrentUser.id(authentication));
    }

    @GetMapping("/notification-settings")
    public NotificationSettingsResponse settings(Authentication authentication) {
        return notifications.settings(CurrentUser.id(authentication));
    }

    @PatchMapping("/notification-settings")
    public NotificationSettingsResponse updateSettings(Authentication authentication,
                                                       @Valid @RequestBody UpdateNotificationSettingsRequest request) {
        return notifications.updateSettings(CurrentUser.id(authentication), request);
    }
}
