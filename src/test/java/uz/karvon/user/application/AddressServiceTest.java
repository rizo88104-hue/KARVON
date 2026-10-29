package uz.karvon.user.application;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.user.api.dto.AddressRequest;
import uz.karvon.user.api.dto.AddressResponse;
import uz.karvon.user.domain.Address;
import uz.karvon.user.infrastructure.AddressRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddressServiceTest {

    private final AddressRepository addresses = mock(AddressRepository.class);
    private final AddressService service = new AddressService(addresses, Mappers.getMapper(UserMapper.class));
    private final UUID userId = UUID.randomUUID();

    AddressServiceTest() {
        when(addresses.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void firstAddressIsDefaultEvenIfNotAsked() {
        when(addresses.countByUserId(userId)).thenReturn(0L);

        AddressResponse created = service.create(userId, request(null, false));

        assertThat(created.isDefault()).isTrue();
        assertThat(created.street()).isEqualTo("Bunyodkor 1");
    }

    @Test
    void laterAddressIsNotDefaultUnlessAsked() {
        when(addresses.countByUserId(userId)).thenReturn(2L);

        assertThat(service.create(userId, request(null, null)).isDefault()).isFalse();
        verify(addresses, never()).clearDefault(userId);
    }

    @Test
    void newDefaultClearsTheOldOneFirst() {
        when(addresses.countByUserId(userId)).thenReturn(2L);

        assertThat(service.create(userId, request(null, true)).isDefault()).isTrue();

        InOrder order = inOrder(addresses);
        order.verify(addresses).clearDefault(userId);
        order.verify(addresses).save(any(Address.class));
    }

    @Test
    void eleventhAddressIsRefused() {
        when(addresses.countByUserId(userId)).thenReturn((long) AddressService.MAX_ADDRESSES);

        assertThatThrownBy(() -> service.create(userId, request(null, false)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ADDRESS_LIMIT_REACHED");
        verify(addresses, never()).save(any());
    }

    @Test
    void phoneIsNormalizedOrRejected() {
        when(addresses.countByUserId(userId)).thenReturn(1L);

        assertThat(service.create(userId, request("998 90 111 22 33", false)).phone()).isEqualTo("+998901112233");
        assertThat(service.create(userId, request(" ", false)).phone()).isNull();
        assertThatThrownBy(() -> service.create(userId, request("12345", false)))
                .hasFieldOrPropertyWithValue("code", "PHONE_INVALID");
    }

    @Test
    void currentDefaultStaysDefaultWhenUpdatedWithFalse() {
        Address address = existing(true);

        assertThat(service.update(userId, address.getId(), request(null, false)).isDefault()).isTrue();
        verify(addresses, never()).clearDefault(userId);
    }

    @Test
    void updateCanMakeAddressDefault() {
        Address address = existing(false);

        assertThat(service.update(userId, address.getId(), request(null, true)).isDefault()).isTrue();
        verify(addresses).clearDefault(userId);
    }

    @Test
    void deletingDefaultPromotesNewestRemaining() {
        Address deleted = existing(true);
        Address remaining = new Address(userId);
        when(addresses.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(remaining));

        service.delete(userId, deleted.getId());

        verify(addresses).delete(deleted);
        assertThat(remaining.isDefaultAddress()).isTrue();
    }

    @Test
    void deletingNonDefaultChangesNothingElse() {
        Address deleted = existing(false);

        service.delete(userId, deleted.getId());

        verify(addresses, never()).findFirstByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void addressOfAnotherUserIsNotFound() {
        assertThatThrownBy(() -> service.get(userId, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "ADDRESS_NOT_FOUND");
    }

    private Address existing(boolean isDefault) {
        Address address = new Address(userId);
        address.setDefaultAddress(isDefault);
        when(addresses.findByIdAndUserId(address.getId(), userId)).thenReturn(Optional.of(address));
        return address;
    }

    private static AddressRequest request(String phone, Boolean isDefault) {
        return new AddressRequest("Uy", "Toshkent", "Toshkent", "Chilonzor", "Bunyodkor 1", "12", null, null,
                phone, isDefault);
    }
}
