package uz.karvon.wallet.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.wallet.api.dto.AdjustWalletRequest;
import uz.karvon.wallet.api.dto.CardRefundRequest;
import uz.karvon.wallet.api.dto.WalletResponse;
import uz.karvon.wallet.api.dto.WalletTransactionResponse;
import uz.karvon.wallet.application.WalletService;

import java.util.UUID;

@Tag(name = "Admin: wallets")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/wallets/{userId}")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminWalletController {

    private final WalletService wallets;

    @GetMapping
    public WalletResponse wallet(@PathVariable UUID userId,
                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return wallets.get(userId, page, size);
    }

    /** Adds (positive) or takes (negative) money with a reason. Recorded with your user id. */
    @PostMapping("/adjust")
    public WalletTransactionResponse adjust(Authentication authentication, @PathVariable UUID userId,
                                            @Valid @RequestBody AdjustWalletRequest request) {
        return wallets.adjust(CurrentUser.id(authentication), userId, request.amount(), request.reason());
    }

    /** Records that you returned this wallet money to the customer card by hand. */
    @PostMapping("/card-refund")
    public WalletTransactionResponse cardRefund(Authentication authentication, @PathVariable UUID userId,
                                                @Valid @RequestBody CardRefundRequest request) {
        return wallets.cardRefund(CurrentUser.id(authentication), userId, request.amount(), request.comment());
    }
}
