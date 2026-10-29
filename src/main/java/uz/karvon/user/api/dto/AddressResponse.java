package uz.karvon.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record AddressResponse(
        UUID id,
        String label,
        String region,
        String city,
        String district,
        String street,
        String house,
        String apartment,
        String landmark,
        String phone,
        @JsonProperty("isDefault") boolean isDefault) {
}
