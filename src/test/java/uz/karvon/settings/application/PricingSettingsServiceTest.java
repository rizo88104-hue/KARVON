package uz.karvon.settings.application;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import uz.karvon.settings.api.dto.PricingSettingsResponse;
import uz.karvon.settings.api.dto.PublicPricingSettingsResponse;
import uz.karvon.settings.api.dto.UpdatePricingSettingsRequest;
import uz.karvon.settings.domain.PricingSettings;
import uz.karvon.settings.infrastructure.PricingSettingsCache;
import uz.karvon.settings.infrastructure.PricingSettingsRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PricingSettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private final PricingSettingsRepository repository = mock(PricingSettingsRepository.class);
    private final PricingSettingsCache cache = mock(PricingSettingsCache.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PricingSettingsService service = new PricingSettingsService(repository, cache,
            Mappers.getMapper(SettingsMapper.class), events, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void cachedSettingsAreUsedWithoutTheDatabase() {
        PricingSettingsResponse cached = response();
        when(cache.get()).thenReturn(Optional.of(cached));

        assertThat(service.current()).isSameAs(cached);
        verifyNoInteractions(repository);
    }

    @Test
    void cacheMissLoadsNewestRowAndCachesIt() {
        PricingSettings row = row("1780");
        when(cache.get()).thenReturn(Optional.empty());
        when(repository.findFirstByOrderByEffectiveFromDesc()).thenReturn(Optional.of(row));

        PricingSettingsResponse current = service.current();

        assertThat(current.customerRate()).isEqualByComparingTo("1780");
        verify(cache).put(current);
    }

    @Test
    void changeInsertsANewVersionAndAnnouncesIt() {
        UUID adminId = UUID.randomUUID();
        when(repository.save(any(PricingSettings.class))).thenAnswer(call -> call.getArgument(0));

        PricingSettingsResponse saved = service.update(adminId, request("1800"));

        ArgumentCaptor<PricingSettings> inserted = ArgumentCaptor.forClass(PricingSettings.class);
        verify(repository).save(inserted.capture());
        assertThat(inserted.getValue().getId()).as("a new row, not an update").isNull();
        assertThat(saved.customerRate()).isEqualByComparingTo("1800");
        assertThat(saved.changedBy()).isEqualTo(adminId);
        assertThat(saved.effectiveFrom()).isEqualTo(NOW);
        verify(events).publishEvent(any(PricingSettingsChangedEvent.class));
        verify(repository, never()).findFirstByOrderByEffectiveFromDesc();
    }

    @Test
    void publicViewShowsOnlyTheShippingSum() {
        when(cache.get()).thenReturn(Optional.of(response()));

        PublicPricingSettingsResponse view = service.currentPublic();

        assertThat(view.shippingPricePerKg()).isEqualByComparingTo("80000");
        assertThat(view.feePercent()).isEqualByComparingTo("10");
    }

    private static PricingSettings row(String rate) {
        return PricingSettings.builder()
                .customerRate(new BigDecimal(rate)).feePercent(BigDecimal.TEN).minServiceFee(new BigDecimal("15000"))
                .shippingCostPerKg(new BigDecimal("70000")).ourMarkupPerKg(new BigDecimal("10000"))
                .minWeightKg(new BigDecimal("0.5")).defaultWeightKg(new BigDecimal("0.5"))
                .priceChangeThresholdPercent(new BigDecimal("3")).effectiveFrom(NOW).build();
    }

    private static PricingSettingsResponse response() {
        return new PricingSettingsResponse(UUID.randomUUID(), new BigDecimal("1780"), BigDecimal.TEN,
                new BigDecimal("15000"), new BigDecimal("70000"), new BigDecimal("10000"), new BigDecimal("0.5"),
                new BigDecimal("0.5"), new BigDecimal("3"), NOW, null);
    }

    private static UpdatePricingSettingsRequest request(String rate) {
        return new UpdatePricingSettingsRequest(new BigDecimal(rate), BigDecimal.TEN, new BigDecimal("15000"),
                new BigDecimal("70000"), new BigDecimal("10000"), new BigDecimal("0.5"), new BigDecimal("0.5"),
                new BigDecimal("3"));
    }
}
