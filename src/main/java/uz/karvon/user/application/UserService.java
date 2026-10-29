package uz.karvon.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.common.api.PageResponse;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.user.api.dto.UpdateProfileRequest;
import uz.karvon.user.api.dto.UserResponse;
import uz.karvon.user.domain.Role;
import uz.karvon.user.domain.User;
import uz.karvon.user.domain.UserStatus;
import uz.karvon.user.infrastructure.UserRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static uz.karvon.user.infrastructure.UserSpecifications.hasRole;
import static uz.karvon.user.infrastructure.UserSpecifications.hasStatus;
import static uz.karvon.user.infrastructure.UserSpecifications.phoneContains;

/**
 * User accounts. Phones passed in here are already normalized to E.164.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;
    private final UserMapper mapper;
    private final ApplicationEventPublisher events;

    /** Login: returns the user with this phone, or registers a new CUSTOMER. */
    @Transactional
    public UserResponse findOrCreateCustomer(String phone) {
        User user = users.findByPhone(phone)
                .orElseGet(() -> users.save(new User(phone, Set.of(Role.CUSTOMER))));
        return mapper.toResponse(user);
    }

    public Optional<UserResponse> findByPhone(String phone) {
        return users.findByPhone(phone).map(mapper::toResponse);
    }

    public Optional<UserResponse> findById(UUID id) {
        return users.findById(id).map(mapper::toResponse);
    }

    public UserResponse getById(UUID id) {
        return mapper.toResponse(load(id));
    }

    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfileRequest request) {
        User user = load(id);
        if (request.fullName() != null) {
            String fullName = request.fullName().trim();
            user.setFullName(fullName.isEmpty() ? null : fullName);
        }
        if (request.language() != null) {
            user.setLanguage(request.language());
        }
        return mapper.toResponse(user);
    }

    public PageResponse<UserResponse> search(String phone, Role role, UserStatus status, Pageable pageable) {
        Specification<User> filter = Specification.allOf(phoneContains(phone), hasRole(role), hasStatus(status));
        return PageResponse.of(users.findAll(filter, pageable), mapper::toResponse);
    }

    /** An admin cannot take the ADMIN role away from themselves, so the system never loses its last admin by accident. */
    @Transactional
    public UserResponse updateRoles(UUID actorId, UUID id, Set<Role> roles) {
        if (actorId.equals(id) && !roles.contains(Role.ADMIN)) {
            throw new BusinessException("CANNOT_CHANGE_OWN_ACCOUNT", "error.user.cannotChangeOwnAccount");
        }
        User user = load(id);
        user.replaceRoles(roles);
        return mapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateStatus(UUID actorId, UUID id, UserStatus status) {
        if (actorId.equals(id)) {
            throw new BusinessException("CANNOT_CHANGE_OWN_ACCOUNT", "error.user.cannotChangeOwnAccount");
        }
        User user = load(id);
        boolean blocking = status == UserStatus.BLOCKED && !user.isBlocked();
        user.setStatus(status);
        if (blocking) {
            events.publishEvent(new UserBlockedEvent(id));
        }
        return mapper.toResponse(user);
    }

    /** Startup: creates the ADMIN user for this phone if no user has it. Returns false if the phone is already taken. */
    @Transactional
    public boolean createAdminIfMissing(String phone) {
        if (users.findByPhone(phone).isPresent()) {
            return false;
        }
        users.save(new User(phone, Set.of(Role.ADMIN)));
        return true;
    }

    private User load(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "error.user.notFound"));
    }
}
