package uz.karvon.cart.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.cart.api.dto.AddCartItemRequest;
import uz.karvon.cart.api.dto.CartResponse;
import uz.karvon.cart.api.dto.SelectAllRequest;
import uz.karvon.cart.api.dto.UpdateCartItemRequest;
import uz.karvon.cart.application.CartService;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.translation.domain.Lang;

import java.util.UUID;

/**
 * The customer's cart. Every call answers the whole updated cart, priced with the current settings.
 */
@Tag(name = "Cart")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/cart")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CartController {

    private final CartService cart;

    @GetMapping
    public CartResponse get(Authentication authentication) {
        return cart.get(CurrentUser.id(authentication));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse add(Authentication authentication, @Valid @RequestBody AddCartItemRequest request) {
        return cart.add(CurrentUser.id(authentication), request, lang());
    }

    @PatchMapping("/items/{id}")
    public CartResponse update(Authentication authentication, @PathVariable UUID id,
                               @Valid @RequestBody UpdateCartItemRequest request) {
        return cart.update(CurrentUser.id(authentication), id, request);
    }

    @PatchMapping("/select-all")
    public CartResponse selectAll(Authentication authentication, @Valid @RequestBody SelectAllRequest request) {
        return cart.selectAll(CurrentUser.id(authentication), request.selected());
    }

    @DeleteMapping("/items/{id}")
    public CartResponse delete(Authentication authentication, @PathVariable UUID id) {
        return cart.delete(CurrentUser.id(authentication), id);
    }

    /** {@code selected=true} removes the selected lines, {@code false} the unselected ones. */
    @DeleteMapping("/items")
    public CartResponse deleteBySelected(Authentication authentication, @RequestParam boolean selected) {
        return cart.deleteBySelected(CurrentUser.id(authentication), selected);
    }

    /** Reads every product again from Taobao and marks changed prices and missing stock. */
    @PostMapping("/refresh-prices")
    public CartResponse refreshPrices(Authentication authentication) {
        return cart.refreshPrices(CurrentUser.id(authentication), lang());
    }

    private static Lang lang() {
        return Lang.ofCustomer(LocaleContextHolder.getLocale());
    }
}
