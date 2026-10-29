package uz.karvon.user.api.dto;

import uz.karvon.user.domain.Language;
import uz.karvon.user.domain.Role;
import uz.karvon.user.domain.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String phone,
        String fullName,
        Language language,
        UserStatus status,
        Set<Role> roles,
        Instant createdAt) {
}
