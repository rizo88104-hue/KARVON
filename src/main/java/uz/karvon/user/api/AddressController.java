package uz.karvon.user.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.user.api.dto.AddressRequest;
import uz.karvon.user.api.dto.AddressResponse;
import uz.karvon.user.application.AddressService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Me: addresses")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public List<AddressResponse> list(Authentication authentication) {
        return addressService.list(CurrentUser.id(authentication));
    }

    @GetMapping("/{id}")
    public AddressResponse get(Authentication authentication, @PathVariable UUID id) {
        return addressService.get(CurrentUser.id(authentication), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(Authentication authentication, @Valid @RequestBody AddressRequest request) {
        return addressService.create(CurrentUser.id(authentication), request);
    }

    @PutMapping("/{id}")
    public AddressResponse update(Authentication authentication, @PathVariable UUID id,
                                  @Valid @RequestBody AddressRequest request) {
        return addressService.update(CurrentUser.id(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable UUID id) {
        addressService.delete(CurrentUser.id(authentication), id);
    }
}
