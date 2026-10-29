package uz.karvon.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import uz.karvon.user.api.dto.UserResponse;
import uz.karvon.user.application.UserService;
import uz.karvon.user.domain.UserStatus;

import java.util.UUID;

/**
 * Turns a valid access token into the request's authentication. The user is loaded on every request,
 * so a blocked user is refused at once and role changes apply without waiting for the token to expire.
 * The authentication name is the user id (see CurrentUser).
 */
@Component
@RequiredArgsConstructor
public class JwtUserAuthenticator {

    private final UserService userService;

    public AbstractAuthenticationToken authenticate(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidBearerTokenException("Token subject is not a user id");
        }
        UserResponse user = userService.findById(userId)
                .orElseThrow(() -> new InvalidBearerTokenException("Token user does not exist"));
        if (user.status() == UserStatus.BLOCKED) {
            throw new DisabledException("User is blocked");
        }
        var authorities = user.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, userId.toString());
    }
}
