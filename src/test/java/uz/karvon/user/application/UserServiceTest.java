package uz.karvon.user.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.ApplicationEventPublisher;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.user.api.dto.UpdateProfileRequest;
import uz.karvon.user.api.dto.UserResponse;
import uz.karvon.user.domain.Language;
import uz.karvon.user.domain.Role;
import uz.karvon.user.domain.User;
import uz.karvon.user.domain.UserStatus;
import uz.karvon.user.infrastructure.UserRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final UserService service = new UserService(users, Mappers.getMapper(UserMapper.class), events);

    private final UUID adminId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("+998901234567", Set.of(Role.CUSTOMER));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void newPhoneBecomesActiveUzbekCustomer() {
        when(users.findByPhone("+998907654321")).thenReturn(Optional.empty());

        UserResponse created = service.findOrCreateCustomer("+998907654321");

        assertThat(created.roles()).containsExactly(Role.CUSTOMER);
        assertThat(created.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.language()).isEqualTo(Language.UZ);
    }

    @Test
    void existingPhoneIsNotRegisteredAgain() {
        when(users.findByPhone(user.getPhone())).thenReturn(Optional.of(user));

        service.findOrCreateCustomer(user.getPhone());

        verify(users, never()).save(any());
    }

    @Test
    void blankFullNameClearsItAndNullKeepsIt() {
        user.setFullName("Ali");

        assertThat(service.updateProfile(userId, new UpdateProfileRequest(null, Language.RU)).fullName()).isEqualTo("Ali");
        assertThat(service.updateProfile(userId, new UpdateProfileRequest("   ", null)).fullName()).isNull();
        assertThat(user.getLanguage()).isEqualTo(Language.RU);
    }

    @Test
    void rolesAreReplaced() {
        UserResponse updated = service.updateRoles(adminId, userId, Set.of(Role.BUYER, Role.OPERATOR));

        assertThat(updated.roles()).containsExactlyInAnyOrder(Role.BUYER, Role.OPERATOR);
    }

    @Test
    void adminCannotRemoveOwnAdminRole() {
        assertThatThrownBy(() -> service.updateRoles(adminId, adminId, Set.of(Role.OPERATOR)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CANNOT_CHANGE_OWN_ACCOUNT");
    }

    @Test
    void adminCannotChangeOwnStatus() {
        assertThatThrownBy(() -> service.updateStatus(adminId, adminId, UserStatus.BLOCKED))
                .hasFieldOrPropertyWithValue("code", "CANNOT_CHANGE_OWN_ACCOUNT");
    }

    @Test
    void blockingPublishesEventOnce() {
        service.updateStatus(adminId, userId, UserStatus.BLOCKED);
        service.updateStatus(adminId, userId, UserStatus.BLOCKED);

        verify(events).publishEvent(new UserBlockedEvent(userId));
        assertThat(user.isBlocked()).isTrue();
    }

    @Test
    void unblockingPublishesNothing() {
        user.setStatus(UserStatus.BLOCKED);

        service.updateStatus(adminId, userId, UserStatus.ACTIVE);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void unknownUserIsNotFound() {
        assertThatThrownBy(() -> service.getById(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
    }

    @Test
    void adminIsCreatedOnlyWhenPhoneIsFree() {
        when(users.findByPhone("+998900000001")).thenReturn(Optional.empty());
        assertThat(service.createAdminIfMissing("+998900000001")).isTrue();
        verify(users).save(any(User.class));

        when(users.findByPhone(user.getPhone())).thenReturn(Optional.of(user));
        assertThat(service.createAdminIfMissing(user.getPhone())).isFalse();
    }
}
