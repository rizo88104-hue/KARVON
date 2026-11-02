package uz.karvon.notification.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.common.domain.PhoneNumbers;
import uz.karvon.notification.infrastructure.FakeSmsSender;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dev and test only: shows the last OTP the fake SMS sender "sent". The bean does not exist in prod.
 */
@Tag(name = "Dev")
@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/v1/dev/otp")
@RequiredArgsConstructor
public class DevOtpController {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{5})\\b");
    /** Part of the login SMS text (OtpService). */
    private static final String OTP_MARKER = "kodi";

    private final FakeSmsSender sms;

    /** {@code phone} may be written with or without the plus sign (e.g. 998901234567). */
    @GetMapping("/{phone}")
    public DevOtpResponse lastCode(@PathVariable String phone) {
        String normalized = PhoneNumbers.normalize(phone).orElse(phone);
        // The newest login SMS; order notifications to the same phone are skipped.
        List<String> sent = sms.messages(normalized);
        for (int i = sent.size() - 1; i >= 0; i--) {
            String text = sent.get(i);
            Matcher matcher = CODE.matcher(text);
            if (text.contains(OTP_MARKER) && matcher.find()) {
                return new DevOtpResponse(normalized, matcher.group(1), text);
            }
        }
        throw new NotFoundException("DEV_OTP_NOT_FOUND", "error.dev.otpNotFound");
    }

    public record DevOtpResponse(String phone, String code, String text) {
    }
}
