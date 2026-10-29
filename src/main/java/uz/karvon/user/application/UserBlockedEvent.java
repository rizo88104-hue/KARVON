package uz.karvon.user.application;

import java.util.UUID;

/**
 * Published inside the transaction that blocks a user, so other modules can end the user's sessions.
 */
public record UserBlockedEvent(UUID userId) {
}
