package uz.karvon.user.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import uz.karvon.common.domain.PhoneNumbers;
import uz.karvon.user.domain.Role;

/**
 * Creates the first ADMIN from the ADMIN_PHONE environment variable, so staff can be managed from the start.
 */
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserService userService;

    @Value("${karvon.admin.phone:}")
    private String adminPhone;

    @Override
    public void run(ApplicationArguments args) {
        if (adminPhone == null || adminPhone.isBlank()) {
            log.info("ADMIN_PHONE is not set, no admin user is created");
            return;
        }
        String phone = PhoneNumbers.normalize(adminPhone)
                .orElseThrow(() -> new IllegalStateException("ADMIN_PHONE must be an Uzbek number +998XXXXXXXXX"));
        if (userService.createAdminIfMissing(phone)) {
            log.info("Created ADMIN user {}", phone);
            return;
        }
        boolean isAdmin = userService.findByPhone(phone)
                .map(user -> user.roles().contains(Role.ADMIN))
                .orElse(false);
        if (!isAdmin) {
            log.warn("ADMIN_PHONE {} belongs to an existing user without the ADMIN role; roles were not changed", phone);
        }
    }
}
