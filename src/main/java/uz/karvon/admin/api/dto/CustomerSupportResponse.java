package uz.karvon.admin.api.dto;

import uz.karvon.common.api.PageResponse;
import uz.karvon.notification.api.dto.NotificationResponse;
import uz.karvon.order.api.dto.StaffOrderSummaryResponse;
import uz.karvon.user.api.dto.UserResponse;
import uz.karvon.wallet.api.dto.WalletResponse;

/**
 * Everything support needs about one customer: the latest orders, the wallet with its latest movements and the
 * latest notifications (newest first, 20 of each; page further with the orders, wallets and notifications endpoints).
 */
public record CustomerSupportResponse(UserResponse profile, PageResponse<StaffOrderSummaryResponse> orders,
                                      WalletResponse wallet, PageResponse<NotificationResponse> notifications) {
}
