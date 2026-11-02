package uz.karvon.admin.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.admin.api.dto.CustomerSupportResponse;
import uz.karvon.admin.application.CustomerSupportService;

import java.util.UUID;

@Tag(name = "Admin: customer support")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/customers")
@PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerSupportService support;

    /** Profile, latest orders, wallet and latest notifications of a customer. */
    @GetMapping("/{id}")
    public CustomerSupportResponse customer(@PathVariable UUID id) {
        return support.customer(id);
    }
}
