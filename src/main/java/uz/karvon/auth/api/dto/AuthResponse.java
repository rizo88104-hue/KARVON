package uz.karvon.auth.api.dto;

import uz.karvon.user.api.dto.UserResponse;

public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {
}
