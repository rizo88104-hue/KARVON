package uz.karvon.auth.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.auth.api.dto.AuthResponse;
import uz.karvon.auth.api.dto.OtpRequest;
import uz.karvon.auth.api.dto.OtpRequestResponse;
import uz.karvon.auth.api.dto.OtpVerifyRequest;
import uz.karvon.auth.api.dto.RefreshRequest;
import uz.karvon.auth.application.AuthService;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/otp/request")
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest request) {
        return new OtpRequestResponse(authService.requestOtp(request.phone()));
    }

    /** Registers a new CUSTOMER on first login. */
    @PostMapping("/otp/verify")
    public AuthResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return authService.verifyOtp(request.phone(), request.code());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** Takes the refresh token, so it also works after the access token has expired. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
