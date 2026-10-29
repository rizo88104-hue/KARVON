package uz.karvon.settings.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.common.api.PageResponse;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.settings.api.dto.PricingSettingsResponse;
import uz.karvon.settings.api.dto.PublicPricingSettingsResponse;
import uz.karvon.settings.api.dto.UpdatePricingSettingsRequest;
import uz.karvon.settings.domain.PricingSettings;
import uz.karvon.settings.infrastructure.PricingSettingsCache;
import uz.karvon.settings.infrastructure.PricingSettingsRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Pricing settings with history. A change never edits a row: it inserts a new version that becomes active,
 * so orders priced earlier still point to the version they used.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PricingSettingsService {

    private final PricingSettingsRepository repository;
    private final PricingSettingsCache cache;
    private final SettingsMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** The active version, from Redis when cached. */
    public PricingSettingsResponse current() {
        return cache.get().orElseGet(() -> {
            PricingSettingsResponse settings = mapper.toResponse(repository.findFirstByOrderByEffectiveFromDesc()
                    .orElseThrow(() -> new IllegalStateException("No pricing settings: the V3 migration seeds them")));
            cache.put(settings);
            return settings;
        });
    }

    public PublicPricingSettingsResponse currentPublic() {
        return mapper.toPublic(current());
    }

    /** A specific version, e.g. the one an order was priced with. */
    public PricingSettingsResponse getById(UUID id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new NotFoundException("SETTINGS_NOT_FOUND", "error.settings.notFound"));
    }

    @Transactional
    public PricingSettingsResponse update(UUID adminId, UpdatePricingSettingsRequest request) {
        PricingSettings saved = repository.save(PricingSettings.builder()
                .customerRate(request.customerRate())
                .feePercent(request.feePercent())
                .minServiceFee(request.minServiceFee())
                .shippingCostPerKg(request.shippingCostPerKg())
                .ourMarkupPerKg(request.ourMarkupPerKg())
                .minWeightKg(request.minWeightKg())
                .defaultWeightKg(request.defaultWeightKg())
                .priceChangeThresholdPercent(request.priceChangeThresholdPercent())
                .effectiveFrom(Instant.now(clock))
                .changedBy(adminId)
                .build());
        events.publishEvent(new PricingSettingsChangedEvent(saved.getId()));
        return mapper.toResponse(saved);
    }

    /** Newest first. */
    public PageResponse<PricingSettingsResponse> history(int page, int size) {
        return PageResponse.of(repository.findAllByOrderByEffectiveFromDesc(PageRequest.of(page, size)),
                mapper::toResponse);
    }
}
