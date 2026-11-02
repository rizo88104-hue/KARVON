package uz.karvon.settings.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.common.api.PageResponse;
import uz.karvon.settings.api.dto.SlaSettingsResponse;
import uz.karvon.settings.domain.SlaSettings;
import uz.karvon.settings.infrastructure.SlaSettingsRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * SLA limits with history: a change inserts a new version. Status names are plain strings here; the order module
 * reads them as order statuses.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlaSettingsService {

    private static final TypeReference<TreeMap<String, Integer>> LIMITS = new TypeReference<>() {
    };

    private final SlaSettingsRepository repository;
    private final JsonMapper json;
    private final Clock clock;

    public SlaSettingsResponse current() {
        return repository.findFirstByOrderByEffectiveFromDesc().map(this::toResponse)
                .orElseThrow(() -> new IllegalStateException("No SLA settings: the V8 migration seeds them"));
    }

    @Transactional
    public SlaSettingsResponse update(UUID adminId, Map<String, Integer> limits) {
        SlaSettings saved = repository.save(new SlaSettings(UUID.randomUUID(), json.writeValueAsString(new TreeMap<>(limits)),
                Instant.now(clock), adminId));
        return toResponse(saved);
    }

    /** Newest first. */
    public PageResponse<SlaSettingsResponse> history(int page, int size) {
        return PageResponse.of(repository.findAllByOrderByEffectiveFromDesc(PageRequest.of(page, size)), this::toResponse);
    }

    private SlaSettingsResponse toResponse(SlaSettings settings) {
        return new SlaSettingsResponse(settings.getId(), json.readValue(settings.getLimits(), LIMITS),
                settings.getEffectiveFrom(), settings.getChangedBy());
    }
}
