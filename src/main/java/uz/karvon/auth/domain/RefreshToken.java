package uz.karvon.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import uz.karvon.common.domain.BaseEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * One login session. Only the SHA-256 hash of the token is stored. Each refresh revokes this row
 * and creates a new one (rotation).
 */
@Getter
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
