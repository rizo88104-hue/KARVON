package uz.karvon.admin.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.admin.api.dto.CustomerSupportResponse;
import uz.karvon.notification.application.NotificationService;
import uz.karvon.order.application.StaffOrderService;
import uz.karvon.user.api.dto.UserResponse;
import uz.karvon.user.application.UserService;
import uz.karvon.wallet.application.WalletService;

import java.util.UUID;

/**
 * One view of a customer for OPERATOR support, put together from the user, order, wallet and notification modules.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerSupportService {

    static final int LATEST = 20;

    private final UserService users;
    private final StaffOrderService orders;
    private final WalletService wallets;
    private final NotificationService notifications;

    public CustomerSupportResponse customer(UUID id) {
        UserResponse profile = users.getById(id);
        return new CustomerSupportResponse(profile, orders.forCustomer(id, 0, LATEST), wallets.get(id, 0, LATEST),
                notifications.forUser(id, PageRequest.of(0, LATEST, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }
}
