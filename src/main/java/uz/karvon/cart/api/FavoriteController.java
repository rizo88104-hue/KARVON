package uz.karvon.cart.api;

import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.cart.api.dto.MergeFavoritesRequest;
import uz.karvon.cart.application.FavoriteService;
import uz.karvon.catalog.api.dto.ProductCardResponse;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.translation.domain.Lang;

import java.util.List;

/**
 * "Sevimlilar": the customer's saved products. Adding and removing are idempotent.
 */
@Tag(name = "Favorites")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/me/favorites")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favorites;

    /** Saved products as cards (customer's language, current UZS price), newest first. */
    @GetMapping
    public List<ProductCardResponse> list(Authentication authentication) {
        return favorites.list(CurrentUser.id(authentication), lang());
    }

    /** Only the product ids, newest first: enough to show which hearts are filled. */
    @GetMapping("/ids")
    public List<String> ids(Authentication authentication) {
        return favorites.ids(CurrentUser.id(authentication));
    }

    @PutMapping("/{externalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(Authentication authentication,
                    @Parameter(example = "680000001117") @PathVariable String externalId) {
        favorites.add(CurrentUser.id(authentication), externalId, lang());
    }

    @DeleteMapping("/{externalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication authentication, @PathVariable String externalId) {
        favorites.remove(CurrentUser.id(authentication), externalId);
    }

    /** Keeps a guest's browser favorites after logging in; answers all saved ids. */
    @PostMapping("/merge")
    public List<String> merge(Authentication authentication, @Valid @RequestBody MergeFavoritesRequest request) {
        return favorites.merge(CurrentUser.id(authentication), request.externalIds(), lang());
    }

    private static Lang lang() {
        return Lang.ofCustomer(LocaleContextHolder.getLocale());
    }
}
