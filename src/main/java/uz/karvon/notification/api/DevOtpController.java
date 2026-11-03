package uz.karvon.notification.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.auth.application.DevOtpCodes;
import uz.karvon.auth.application.TelegramLoginService;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.common.domain.PhoneNumbers;

/**
 * Dev and test only: the last login code made for a phone (whatever channel delivered it), and a stand-in for the
 * bot when no real one is configured. The bean does not exist in prod.
 */
@Tag(name = "Dev")
@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/v1/dev/otp")
@RequiredArgsConstructor
public class DevOtpController {

    private final DevOtpCodes codes;
    private final TelegramLoginService telegramLogin;

    /** {@code phone} may be written with or without the plus sign (e.g. 998901234567). */
    @GetMapping("/{phone}")
    public DevOtpResponse lastCode(@PathVariable String phone) {
        String normalized = PhoneNumbers.normalize(phone).orElse(phone);
        return codes.last(normalized)
                .map(code -> new DevOtpResponse(normalized, code.code(), code.text()))
                .orElseThrow(() -> new NotFoundException("DEV_OTP_NOT_FOUND", "error.dev.otpNotFound"));
    }

    /**
     * Without a real bot (no TELEGRAM_BOT_TOKEN): does what pressing Start and sharing the contact would do after
     * POST /auth/otp/request answered TELEGRAM_LINK. The phone gets a fake chat and a code (read it with GET).
     */
    @PostMapping("/{phone}/telegram-login")
    public DevOtpResponse telegramLogin(@PathVariable String phone) {
        String normalized = PhoneNumbers.normalize(phone)
                .orElseThrow(() -> new BusinessException("PHONE_INVALID", "error.phone.invalid"));
        long fakeChatId = -Long.parseLong(normalized.substring(1));
        telegramLogin.linkAndSendCode(fakeChatId, normalized);
        return lastCode(normalized);
    }

    public record DevOtpResponse(String phone, String code, String text) {
    }
}
