package uz.karvon.common.api;

import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * The logged-in user's id. The JWT filter sets the authentication name to the token subject (user id).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
