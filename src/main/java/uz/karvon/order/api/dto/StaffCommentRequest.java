package uz.karvon.order.api.dto;

import jakarta.validation.constraints.Size;

/**
 * An optional comment written into the status history.
 */
public record StaffCommentRequest(@Size(max = 500) String comment) {
}
