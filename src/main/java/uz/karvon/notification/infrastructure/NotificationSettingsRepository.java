package uz.karvon.notification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.notification.domain.NotificationSettings;

import java.util.UUID;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, UUID> {
}
