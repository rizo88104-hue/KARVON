package uz.karvon.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.FileStorage;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.order.api.dto.OrderPhotoResponse;
import uz.karvon.order.domain.Order;
import uz.karvon.order.domain.OrderPhoto;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.order.infrastructure.OrderPhotoRepository;
import uz.karvon.order.infrastructure.OrderRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Parcel photos from the warehouse: at most {@value OrderPhoto#MAX_PER_ORDER} per order, JPEG or PNG
 * (checked by the file content, not the name), at most {@value #MAX_BYTES} bytes each.
 */
@Service
@RequiredArgsConstructor
public class OrderPhotoService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    /** Photos are taken when the parcel reaches the warehouse, until it is sent. */
    static final Set<OrderStatus> ALLOWED = EnumSet.of(OrderStatus.PURCHASED, OrderStatus.AT_WAREHOUSE,
            OrderStatus.AWAITING_SHIPPING_PAYMENT);

    private final OrderRepository orders;
    private final OrderPhotoRepository photos;
    private final FileStorage storage;
    private final Clock clock;

    public record Upload(byte[] content, long size) {
    }

    @Transactional
    public List<OrderPhotoResponse> add(UUID staffId, UUID orderId, List<Upload> uploads) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "error.order.notFound"));
        if (!ALLOWED.contains(order.getStatus())) {
            throw new BusinessException("PHOTO_NOT_ALLOWED_NOW", "error.photo.notAllowedNow");
        }
        if (uploads.isEmpty()) {
            throw new BusinessException("PHOTO_REQUIRED", "error.photo.required");
        }
        if (photos.countByOrderId(orderId) + uploads.size() > OrderPhoto.MAX_PER_ORDER) {
            throw new BusinessException("PHOTO_LIMIT", "error.photo.limit", OrderPhoto.MAX_PER_ORDER);
        }
        for (Upload upload : uploads) {
            if (upload.size() > MAX_BYTES) {
                throw new BusinessException("PHOTO_TOO_LARGE", "error.photo.tooLarge", MAX_BYTES / (1024 * 1024));
            }
            if (type(upload.content()) == null) {
                throw new BusinessException("PHOTO_TYPE_NOT_ALLOWED", "error.photo.typeNotAllowed");
            }
        }
        for (Upload upload : uploads) {
            ImageType type = type(upload.content());
            UUID id = UUID.randomUUID();
            String key = "orders/" + orderId + "/" + id + "." + type.extension;
            storage.store(key, upload.content(), type.contentType);
            photos.save(new OrderPhoto(id, orderId, key, type.contentType, upload.size(), staffId, Instant.now(clock)));
        }
        return list(orderId);
    }

    @Transactional
    public List<OrderPhotoResponse> delete(UUID orderId, UUID photoId) {
        OrderPhoto photo = photos.findByIdAndOrderId(photoId, orderId)
                .orElseThrow(() -> new NotFoundException("PHOTO_NOT_FOUND", "error.photo.notFound"));
        photos.delete(photo);
        storage.delete(photo.getStorageKey());
        return list(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderPhotoResponse> list(UUID orderId) {
        return photos.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(photo -> new OrderPhotoResponse(photo.getId(), storage.url(photo.getStorageKey()), photo.getCreatedAt()))
                .toList();
    }

    private enum ImageType {
        JPEG("image/jpeg", "jpg"), PNG("image/png", "png");

        final String contentType;
        final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    /** By the first bytes: JPEG FF D8 FF, PNG 89 50 4E 47 0D 0A 1A 0A. */
    private static ImageType type(byte[] content) {
        if (content.length >= 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (content.length >= png.length) {
            for (int i = 0; i < png.length; i++) {
                if (content[i] != png[i]) {
                    return null;
                }
            }
            return ImageType.PNG;
        }
        return null;
    }
}
