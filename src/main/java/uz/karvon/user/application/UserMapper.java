package uz.karvon.user.application;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import uz.karvon.user.api.dto.AddressRequest;
import uz.karvon.user.api.dto.AddressResponse;
import uz.karvon.user.api.dto.UserResponse;
import uz.karvon.user.domain.Address;
import uz.karvon.user.domain.User;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);

    @Mapping(target = "isDefault", source = "defaultAddress")
    AddressResponse toResponse(Address address);

    /** Copies the plain text fields. Phone and the default flag have rules and are set by AddressService. */
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "phone", ignore = true)
    @Mapping(target = "defaultAddress", ignore = true)
    void updateAddress(AddressRequest request, @MappingTarget Address address);
}
