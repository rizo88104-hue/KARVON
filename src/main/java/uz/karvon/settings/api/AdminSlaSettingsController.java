package uz.karvon.settings.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.Audited;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.common.api.PageResponse;
import uz.karvon.settings.api.dto.SlaSettingsResponse;
import uz.karvon.settings.api.dto.UpdateSlaSettingsRequest;
import uz.karvon.settings.application.SlaSettingsService;

@Tag(name = "Admin: settings")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/settings/sla")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSlaSettingsController {

    private final SlaSettingsService sla;

    @GetMapping
    public SlaSettingsResponse current() {
        return sla.current();
    }

    /** Saves a new version of all limits; earlier versions are kept. */
    @PutMapping
    @Audited(action = "SLA_SETTINGS_UPDATE", entityType = "SLA_SETTINGS")
    public SlaSettingsResponse update(Authentication authentication, @Valid @RequestBody UpdateSlaSettingsRequest request) {
        return sla.update(CurrentUser.id(authentication), request.limits());
    }

    @GetMapping("/history")
    public PageResponse<SlaSettingsResponse> history(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return sla.history(page, size);
    }
}
