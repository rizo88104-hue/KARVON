package uz.karvon.settings.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Maximum hours an order may stay in a status, e.g. {"PAID": 48, "PURCHASED": 240, "IN_TRANSIT": 600}.
 */
public record SlaSettingsResponse(UUID id, Map<String, Integer> limits, Instant effectiveFrom, UUID changedBy) {
}
