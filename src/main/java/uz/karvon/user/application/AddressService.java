package uz.karvon.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.common.domain.PhoneNumbers;
import uz.karvon.user.api.dto.AddressRequest;
import uz.karvon.user.api.dto.AddressResponse;
import uz.karvon.user.domain.Address;
import uz.karvon.user.infrastructure.AddressRepository;

import java.util.List;
import java.util.UUID;

/**
 * A user's delivery addresses. Rules: at most {@value #MAX_ADDRESSES} per user, and exactly one default
 * while the user has any (the first one becomes default, deleting the default promotes the newest remaining).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

    public static final int MAX_ADDRESSES = 10;

    private final AddressRepository addresses;
    private final UserMapper mapper;

    public List<AddressResponse> list(UUID userId) {
        return addresses.findByUserIdOrderByCreatedAtAsc(userId).stream().map(mapper::toResponse).toList();
    }

    public AddressResponse get(UUID userId, UUID id) {
        return mapper.toResponse(load(userId, id));
    }

    @Transactional
    public AddressResponse create(UUID userId, AddressRequest request) {
        long count = addresses.countByUserId(userId);
        if (count >= MAX_ADDRESSES) {
            throw new BusinessException("ADDRESS_LIMIT_REACHED", "error.address.limitReached", MAX_ADDRESSES);
        }
        Address address = new Address(userId);
        apply(request, address);
        if (count == 0 || Boolean.TRUE.equals(request.isDefault())) {
            addresses.clearDefault(userId);
            address.setDefaultAddress(true);
        }
        return mapper.toResponse(addresses.save(address));
    }

    /** {@code isDefault = false} on the current default is ignored: pick another address as default instead. */
    @Transactional
    public AddressResponse update(UUID userId, UUID id, AddressRequest request) {
        Address address = load(userId, id);
        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefaultAddress()) {
            // Clear the old default before this entity changes, so the flush before the bulk update has nothing to write.
            addresses.clearDefault(userId);
            address.setDefaultAddress(true);
        }
        apply(request, address);
        return mapper.toResponse(address);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Address address = load(userId, id);
        addresses.delete(address);
        addresses.flush();
        if (address.isDefaultAddress()) {
            addresses.findFirstByUserIdOrderByCreatedAtDesc(userId)
                    .ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    private void apply(AddressRequest request, Address address) {
        mapper.updateAddress(request, address);
        address.setPhone(normalizePhone(request.phone()));
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return PhoneNumbers.normalize(phone)
                .orElseThrow(() -> new BusinessException("PHONE_INVALID", "error.phone.invalid"));
    }

    private Address load(UUID userId, UUID id) {
        return addresses.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("ADDRESS_NOT_FOUND", "error.address.notFound"));
    }
}
