package uz.karvon.user.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import uz.karvon.user.domain.Role;

import java.util.EnumSet;
import java.util.Set;

/**
 * The roles of the logged-in user, read from the authorities the JWT filter set ("ROLE_ADMIN" ...).
 */
public final class StaffRoles {

    private StaffRoles() {
    }

    public static Set<Role> of(Authentication authentication) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                try {
                    roles.add(Role.valueOf(name.substring("ROLE_".length())));
                } catch (IllegalArgumentException ignored) {
                    // not one of our roles
                }
            }
        }
        return roles;
    }
}
