package uz.karvon.auth.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev and test only: the last login code made for each phone, whatever channel delivered it (Telegram, SMS).
 * Read by GET /api/v1/dev/otp/{phone}. The bean does not exist in prod, so codes are never kept there.
 */
@Component
@Profile({"dev", "test"})
public class DevOtpCodes {

    private final Map<String, Code> codes = new ConcurrentHashMap<>();

    public record Code(String code, String text) {
    }

    void record(String phone, String code, String text) {
        codes.put(phone, new Code(code, text));
    }

    public Optional<Code> last(String phone) {
        return Optional.ofNullable(codes.get(phone));
    }
}
