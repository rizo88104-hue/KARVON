package uz.karvon.user.api.dto;

import jakarta.validation.constraints.Size;
import uz.karvon.user.domain.Language;

/**
 * Partial update: a null field is left unchanged; a blank fullName clears it.
 */
public record UpdateProfileRequest(
        @Size(max = 100) String fullName,
        Language language) {
}
