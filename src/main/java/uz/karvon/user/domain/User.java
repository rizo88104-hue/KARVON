package uz.karvon.user.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uz.karvon.common.domain.BaseEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * A person who logs in with a phone number. Staff are users with extra roles.
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    /** E.164, always +998XXXXXXXXX. */
    @Column(name = "phone", nullable = false, unique = true, length = 13)
    private String phone;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    private Language language = Language.UZ;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Set<Role> roles = new HashSet<>();

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    protected User() {
    }

    public User(String phone, Set<Role> roles) {
        this.phone = phone;
        this.roles = new HashSet<>(roles);
    }

    public boolean isBlocked() {
        return status == UserStatus.BLOCKED;
    }

    public void replaceRoles(Set<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }
}
