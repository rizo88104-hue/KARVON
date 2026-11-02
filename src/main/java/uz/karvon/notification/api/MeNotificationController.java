package uz.karvon.notification.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.notification.api.dto.NotificationSettingsRequest;
import uz.karvon.notification.api.dto.NotificationSettingsResponse;
import uz.karvon.notification.api.dto.TelegramLinkResponse;
import uz.karvon.notification.application.NotificationService;
import uz.karvon.notification.application.TelegramService;

@Tag(name = "Me: notifications")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeNotificationController {

    private final NotificationService notifications;
    private final TelegramService telegram;

    @GetMapping("/notification-settings")
    public NotificationSettingsResponse settings(Authentication authentication) {
        return notifications.settings(CurrentUser.id(authentication));
    }

    @PatchMapping("/notification-settings")
    public NotificationSettingsResponse updateSettings(Authentication authentication,
                                                       @RequestBody NotificationSettingsRequest request) {
        return notifications.updateSettings(CurrentUser.id(authentication), request.sms(), request.telegram());
    }

    /** A one-time link to the bot; opening it and pressing Start links this Telegram chat to you. */
    @GetMapping("/telegram-link")
    public TelegramLinkResponse telegramLink(Authentication authentication) {
        return telegram.createLink(CurrentUser.id(authentication));
    }
}
