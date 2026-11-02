package uz.karvon.order.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.karvon.common.api.Audited;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.order.api.dto.ArrivedRequest;
import uz.karvon.order.api.dto.BulkStatusRequest;
import uz.karvon.order.api.dto.BulkStatusResult;
import uz.karvon.order.api.dto.DelayedOrderResponse;
import uz.karvon.order.api.dto.OrderPhotoResponse;
import uz.karvon.order.api.dto.ShipRequest;
import uz.karvon.order.api.dto.StaffCommentRequest;
import uz.karvon.order.api.dto.StaffOrderResponse;
import uz.karvon.order.api.dto.WeightRequest;
import uz.karvon.order.application.DelayedOrdersService;
import uz.karvon.order.application.OrderBulkService;
import uz.karvon.order.application.OrderPhotoService;
import uz.karvon.order.application.StaffOrderService;
import uz.karvon.user.api.StaffRoles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/**
 * Warehouse and shipping steps. We have no own warehouse: "warehouse" is where the consolidation or cargo partner
 * weighs the parcel; staff enter the weight, tracking numbers and pickup information by hand.
 */
@Tag(name = "Admin: orders")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class StaffOrderStepsController {

    private final StaffOrderService orders;
    private final OrderPhotoService photos;
    private final OrderBulkService bulk;
    private final DelayedOrdersService delayed;

    /** PURCHASED -> AT_WAREHOUSE with the real weight; more expensive shipping must be paid, cheaper goes to the wallet. */
    @PostMapping("/{id}/weight")
    @Audited(action = "ORDER_WEIGH", entityType = "ORDER")
    @PreAuthorize("hasAnyRole('BUYER', 'OPERATOR', 'ADMIN')")
    public StaffOrderResponse weight(Authentication authentication, @PathVariable UUID id,
                                     @Valid @RequestBody WeightRequest request) {
        return orders.weigh(CurrentUser.id(authentication), StaffRoles.of(authentication), id, request.realWeightKg(),
                request.comment());
    }

    /** Up to 4 parcel photos (JPEG or PNG, 5 MB each), form field "files"; shown to the customer. */
    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Audited(action = "ORDER_PHOTOS_ADD", entityType = "ORDER")
    @PreAuthorize("hasAnyRole('BUYER', 'OPERATOR', 'ADMIN')")
    public List<OrderPhotoResponse> addPhotos(Authentication authentication, @PathVariable UUID id,
                                              @RequestPart("files") List<MultipartFile> files) {
        List<OrderPhotoService.Upload> uploads = files.stream().map(file -> {
            try {
                return new OrderPhotoService.Upload(file.getBytes(), file.getSize());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }).toList();
        return photos.add(CurrentUser.id(authentication), id, uploads);
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @Audited(action = "ORDER_PHOTO_DELETE", entityType = "ORDER")
    @PreAuthorize("hasAnyRole('BUYER', 'OPERATOR', 'ADMIN')")
    public List<OrderPhotoResponse> deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        return photos.delete(id, photoId);
    }

    /** -> IN_TRANSIT. Refused while a price or shipping difference is unpaid. */
    @PostMapping("/{id}/ship")
    @Audited(action = "ORDER_SHIP", entityType = "ORDER")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public StaffOrderResponse ship(Authentication authentication, @PathVariable UUID id,
                                   @Valid @RequestBody ShipRequest request) {
        return orders.ship(CurrentUser.id(authentication), StaffRoles.of(authentication), id,
                request.internationalTrackingNumber(), request.carrier(), request.expectedArrivalDate(), request.comment());
    }

    /** -> ARRIVED, with where and how to pick the parcel up. */
    @PostMapping("/{id}/arrived")
    @Audited(action = "ORDER_ARRIVED", entityType = "ORDER")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public StaffOrderResponse arrived(Authentication authentication, @PathVariable UUID id,
                                      @Valid @RequestBody(required = false) ArrivedRequest request) {
        return orders.arrived(CurrentUser.id(authentication), StaffRoles.of(authentication), id,
                request == null ? null : request.pickupInfo(), request == null ? null : request.comment());
    }

    @PostMapping("/{id}/delivered")
    @Audited(action = "ORDER_DELIVERED", entityType = "ORDER")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public StaffOrderResponse delivered(Authentication authentication, @PathVariable UUID id,
                                        @Valid @RequestBody(required = false) StaffCommentRequest request) {
        return orders.delivered(CurrentUser.id(authentication), StaffRoles.of(authentication), id,
                request == null ? null : request.comment());
    }

    /** Many orders shipped together; each one is checked on its own and reported. */
    @PostMapping("/bulk-status")
    @Audited(action = "ORDER_BULK_STATUS", entityType = "ORDER")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public List<BulkStatusResult> bulkStatus(Authentication authentication, @Valid @RequestBody BulkStatusRequest request) {
        return bulk.change(CurrentUser.id(authentication), StaffRoles.of(authentication), request.orderIds(),
                request.toStatus(), request.trackingNumber());
    }

    /** Orders over their SLA limit (see /api/v1/admin/settings/sla), the most overdue first. */
    @GetMapping("/delayed")
    @PreAuthorize("hasAnyRole('OPERATOR', 'BUYER', 'ADMIN')")
    public List<DelayedOrderResponse> delayed() {
        return delayed.delayed();
    }
}
