package uz.karvon.user.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.karvon.common.application.AuditSnapshotSource;
import uz.karvon.user.application.UserService;

import java.util.Optional;
import java.util.UUID;

/**
 * "Before" states of users (roles, status) for the audit log.
 */
@Configuration
public class UserAuditSnapshots {

    @Bean
    AuditSnapshotSource userSnapshots(UserService users) {
        return AuditSnapshotSource.of("USER", id -> id == null ? Optional.empty() : users.findById(UUID.fromString(id)));
    }
}
