package uz.karvon.admin.api.dto;

/**
 * An active ticker message in the customer's language, in display order.
 */
public record PublicTickerMessageResponse(String text, String linkUrl) {
}
