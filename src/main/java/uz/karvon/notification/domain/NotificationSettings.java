package uz.karvon.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The channels a user allows. Missing row = {@link NotificationPreferences#DEFAULT}.
 */
@Getter
@Setter
@Entity
@Table(name = "notification_settings")
public class NotificationSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "sms", nullable = false)
    private boolean sms;

    @Column(name = "telegram", nullable = false)
    private boolean telegram;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationSettings() {
    }

    public NotificationSettings(UUID userId, NotificationPreferences preferences, Instant now) {
        this.userId = userId;
        this.sms = preferences.sms();
        this.telegram = preferences.telegram();
        this.updatedAt = now;
    }

    public NotificationPreferences preferences() {
        return new NotificationPreferences(sms, telegram);
    }
}
