package uz.karvon.user.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.user.api.dto.UpdateProfileRequest;
import uz.karvon.user.api.dto.UserResponse;
import uz.karvon.user.application.UserService;

@Tag(name = "Me")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    @GetMapping
    public UserResponse me(Authentication authentication) {
        return userService.getById(CurrentUser.id(authentication));
    }

    @PatchMapping
    public UserResponse update(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(CurrentUser.id(authentication), request);
    }
}
