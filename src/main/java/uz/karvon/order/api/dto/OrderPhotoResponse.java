package uz.karvon.order.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A parcel photo. {@code url} may expire (S3 links live one hour); load the order again for a fresh one.
 */
public record OrderPhotoResponse(UUID id, String url, Instant createdAt) {
}
