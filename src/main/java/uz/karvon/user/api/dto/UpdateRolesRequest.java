package uz.karvon.user.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import uz.karvon.user.domain.Role;

import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<@NotNull Role> roles) {
}
