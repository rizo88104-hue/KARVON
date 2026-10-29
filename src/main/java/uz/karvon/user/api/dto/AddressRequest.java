package uz.karvon.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Used for create and full update. {@code phone} is optional (the account phone is used when empty).
 * {@code isDefault = true} makes this the default address; the first address is always the default.
 */
public record AddressRequest(
        @Size(max = 50) String label,
        @NotBlank @Size(max = 100) String region,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String district,
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 50) String house,
        @Size(max = 50) String apartment,
        @Size(max = 255) String landmark,
        @Size(max = 20) String phone,
        @JsonProperty("isDefault") Boolean isDefault) {
}
