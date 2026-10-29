package uz.karvon.user.api.dto;

import jakarta.validation.constraints.NotNull;
import uz.karvon.user.domain.UserStatus;

public record UpdateStatusRequest(@NotNull UserStatus status) {
}
