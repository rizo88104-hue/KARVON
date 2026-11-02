package uz.karvon.pricing.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.common.api.PageResponse;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.pricing.api.dto.PromoCodeRequest;
import uz.karvon.pricing.api.dto.PromoCodeResponse;
import uz.karvon.pricing.domain.PromoCode;
import uz.karvon.pricing.domain.PromoCodeType;
import uz.karvon.pricing.domain.PromoCodeUsage;
import uz.karvon.pricing.infrastructure.PromoCodeRepository;
import uz.karvon.pricing.infrastructure.PromoCodeUsageRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Promo codes: ADMIN editing, and using a code when an order is created.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromoCodeService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PromoCodeRepository codes;
    private final PromoCodeUsageRepository usages;
    private final Clock clock;

    /** What a code took off an order's service fee. */
    public record AppliedPromo(String code, BigDecimal discountUzs) {
    }

    /**
     * Uses {@code code} for a new order: checks that it is active and inside its limits, records the use and returns
     * the discount on {@code serviceFeeUzs} (never more than the fee). Runs in the caller's transaction, which must
     * roll back if the order is not created.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AppliedPromo apply(String code, UUID userId, UUID orderId, BigDecimal serviceFeeUzs) {
        PromoCode promo = codes.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new BusinessException("PROMO_CODE_NOT_FOUND", "error.promo.notFound"));
        PromoCode.Refusal refusal = promo.refusal(Instant.now(clock), usages.countByPromoCodeIdAndUserId(promo.getId(), userId));
        if (refusal != null) {
            throw switch (refusal) {
                case NOT_ACTIVE -> new BusinessException("PROMO_CODE_NOT_ACTIVE", "error.promo.notActive");
                case LIMIT_REACHED -> new BusinessException("PROMO_CODE_LIMIT_REACHED", "error.promo.limitReached");
                case USER_LIMIT_REACHED -> new BusinessException("PROMO_CODE_USER_LIMIT_REACHED", "error.promo.userLimitReached");
            };
        }
        BigDecimal discount = promo.discountFor(serviceFeeUzs);
        promo.setUsedCount(promo.getUsedCount() + 1);
        usages.save(new PromoCodeUsage(promo.getId(), userId, orderId, discount, Instant.now(clock)));
        return new AppliedPromo(promo.getCode(), discount);
    }

    /** A cancelled order gives its use back. Does nothing when the order used no code. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID orderId) {
        usages.findByOrderId(orderId).ifPresent(usage -> {
            codes.findByIdForUpdate(usage.getPromoCodeId())
                    .ifPresent(promo -> promo.setUsedCount(Math.max(0, promo.getUsedCount() - 1)));
            usages.delete(usage);
        });
    }

    // --- admin

    /** {@code q} matches any part of the code. Newest first. */
    public PageResponse<PromoCodeResponse> list(String q, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(q == null || q.isBlank() ? codes.findAll(pageable)
                : codes.findByCodeContaining(normalize(q), pageable), PromoCodeService::response);
    }

    public PromoCodeResponse get(UUID id) {
        return response(load(id));
    }

    public Optional<PromoCodeResponse> find(UUID id) {
        return codes.findById(id).map(PromoCodeService::response);
    }

    @Transactional
    public PromoCodeResponse create(PromoCodeRequest request) {
        String code = normalize(request.code());
        if (codes.existsByCode(code)) {
            throw codeTaken();
        }
        PromoCode promo = new PromoCode();
        apply(promo, code, request);
        return response(codes.save(promo));
    }

    @Transactional
    public PromoCodeResponse update(UUID id, PromoCodeRequest request) {
        PromoCode promo = load(id);
        String code = normalize(request.code());
        if (codes.existsByCodeAndIdNot(code, id)) {
            throw codeTaken();
        }
        apply(promo, code, request);
        return response(codes.saveAndFlush(promo));
    }

    /** Orders that used it keep their discount and the code text. */
    @Transactional
    public void delete(UUID id) {
        codes.delete(load(id));
    }

    private static void apply(PromoCode promo, String code, PromoCodeRequest request) {
        if (request.type() == PromoCodeType.PERCENT_OF_FEE && request.value().compareTo(HUNDRED) > 0) {
            throw new BusinessException("PROMO_CODE_VALUE_INVALID", "error.promo.valueInvalid");
        }
        if (request.validFrom() != null && request.validTo() != null && !request.validTo().isAfter(request.validFrom())) {
            throw new BusinessException("PROMO_CODE_PERIOD_INVALID", "error.promo.periodInvalid");
        }
        promo.setCode(code);
        promo.setType(request.type());
        promo.setValue(request.value());
        promo.setMaxUses(request.maxUses());
        promo.setPerUserLimit(request.perUserLimit());
        promo.setValidFrom(request.validFrom());
        promo.setValidTo(request.validTo());
        promo.setActive(request.active());
    }

    private PromoCode load(UUID id) {
        return codes.findById(id).orElseThrow(() -> new NotFoundException("PROMO_CODE_NOT_FOUND", "error.promo.notFound"));
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static BusinessException codeTaken() {
        return new BusinessException(HttpStatus.CONFLICT, "PROMO_CODE_TAKEN", "error.promo.taken");
    }

    private static PromoCodeResponse response(PromoCode p) {
        return new PromoCodeResponse(p.getId(), p.getCode(), p.getType(), p.getValue(), p.getMaxUses(),
                p.getPerUserLimit(), p.getValidFrom(), p.getValidTo(), p.isActive(), p.getUsedCount(), p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
