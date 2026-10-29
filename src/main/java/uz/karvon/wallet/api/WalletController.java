package uz.karvon.wallet.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.wallet.api.dto.WalletResponse;
import uz.karvon.wallet.application.WalletService;

@Tag(name = "Me: wallet")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/me/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService wallets;

    @GetMapping
    public WalletResponse wallet(Authentication authentication,
                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return wallets.get(CurrentUser.id(authentication), page, size);
    }
}
