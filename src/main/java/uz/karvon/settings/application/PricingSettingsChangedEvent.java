package uz.karvon.settings.application;

import java.util.UUID;

/**
 * A new pricing settings version was saved. Published inside the saving transaction.
 */
public record PricingSettingsChangedEvent(UUID settingsId) {
}
