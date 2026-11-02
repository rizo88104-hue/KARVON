package uz.karvon.admin.api.dto;

import java.util.UUID;

/**
 * A banner shown on the home page, in display order.
 */
public record PublicBannerResponse(UUID id, String title, String subtitle, String imageUrl, String linkUrl) {
}
