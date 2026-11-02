package uz.karvon.common.application;

/**
 * Stores files such as parcel photos. Local disk in dev and test, S3-compatible object storage in prod.
 * Keys are paths like "orders/{orderId}/{photoId}.jpg" made by the caller.
 */
public interface FileStorage {

    void store(String key, byte[] content, String contentType);

    /** A URL the customer browser can open (for S3 a link valid for a limited time). */
    String url(String key);

    void delete(String key);
}
