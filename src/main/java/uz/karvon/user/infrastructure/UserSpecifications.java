package uz.karvon.user.infrastructure;

import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import uz.karvon.user.domain.Role;
import uz.karvon.user.domain.User;
import uz.karvon.user.domain.UserStatus;

/**
 * Filters for the staff user list. A null argument means "no filter".
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> phoneContains(String phone) {
        return (root, query, cb) -> phone == null || phone.isBlank()
                ? null
                : cb.like(root.get("phone"), "%" + escapeLike(phone.trim()) + "%", '\\');
    }

    public static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> {
            if (role == null) {
                return null;
            }
            query.distinct(true);
            Join<User, Role> roles = root.join("roles");
            return cb.equal(roles, role);
        };
    }

    public static Specification<User> hasStatus(UserStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
