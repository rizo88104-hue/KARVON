package uz.karvon.user.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.common.api.PageResponse;
import uz.karvon.user.api.dto.UpdateRolesRequest;
import uz.karvon.user.api.dto.UpdateStatusRequest;
import uz.karvon.user.api.dto.UserResponse;
import uz.karvon.user.application.UserService;
import uz.karvon.user.domain.Role;
import uz.karvon.user.domain.UserStatus;

import java.util.UUID;

@Tag(name = "Admin: users")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    /** {@code phone} matches any part of the number. */
    @GetMapping
    public PageResponse<UserResponse> search(@RequestParam(required = false) String phone,
                                             @RequestParam(required = false) Role role,
                                             @RequestParam(required = false) UserStatus status,
                                             @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                             Pageable pageable) {
        return userService.search(phone, role, status, pageable);
    }

    @PatchMapping("/{id}/roles")
    public UserResponse updateRoles(Authentication authentication, @PathVariable UUID id,
                                    @Valid @RequestBody UpdateRolesRequest request) {
        return userService.updateRoles(CurrentUser.id(authentication), id, request.roles());
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(Authentication authentication, @PathVariable UUID id,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        return userService.updateStatus(CurrentUser.id(authentication), id, request.status());
    }
}
