package uz.karvon.admin.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.admin.api.dto.PublicTickerMessageResponse;
import uz.karvon.admin.api.dto.TickerMessageRequest;
import uz.karvon.admin.api.dto.TickerMessageResponse;
import uz.karvon.admin.domain.TickerMessage;
import uz.karvon.admin.infrastructure.TickerMessageRepository;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.translation.domain.Lang;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The ticker that runs along the site's top bar: short messages edited by ADMIN. A link must be a site path
 * ("/pages/faq") or an http(s) address, so no other scheme (e.g. javascript:) reaches the site.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TickerService {

    private static final Pattern LINK = Pattern.compile("^(/(?!/)|https?://)\\S*$");

    private final TickerMessageRepository messages;

    public List<TickerMessageResponse> list() {
        return messages.findAllByOrderBySortOrderAscCreatedAtAsc().stream().map(TickerService::response).toList();
    }

    public TickerMessageResponse get(UUID id) {
        return response(load(id));
    }

    public Optional<TickerMessageResponse> find(UUID id) {
        return messages.findById(id).map(TickerService::response);
    }

    /** Active messages in the customer's language (Accept-Language), in display order. */
    public List<PublicTickerMessageResponse> visible() {
        boolean ru = Lang.ofCustomer(LocaleContextHolder.getLocale()) == Lang.RU;
        return messages.findByActiveTrueOrderBySortOrderAscCreatedAtAsc().stream()
                .map(m -> new PublicTickerMessageResponse(ru ? m.getTextRu() : m.getTextUz(), m.getLinkUrl()))
                .toList();
    }

    @Transactional
    public TickerMessageResponse create(TickerMessageRequest request) {
        TickerMessage message = new TickerMessage();
        apply(message, request);
        return response(messages.save(message));
    }

    @Transactional
    public TickerMessageResponse update(UUID id, TickerMessageRequest request) {
        TickerMessage message = load(id);
        apply(message, request);
        return response(messages.saveAndFlush(message));
    }

    @Transactional
    public void delete(UUID id) {
        messages.delete(load(id));
    }

    private TickerMessage load(UUID id) {
        return messages.findById(id)
                .orElseThrow(() -> new NotFoundException("TICKER_MESSAGE_NOT_FOUND", "error.ticker.notFound"));
    }

    private static void apply(TickerMessage message, TickerMessageRequest request) {
        String link = request.linkUrl() == null || request.linkUrl().isBlank() ? null : request.linkUrl().trim();
        if (link != null && !LINK.matcher(link).matches()) {
            throw new BusinessException("TICKER_LINK_INVALID", "error.ticker.linkInvalid");
        }
        message.setTextUz(request.textUz().trim());
        message.setTextRu(request.textRu().trim());
        message.setLinkUrl(link);
        message.setSortOrder(request.sortOrder());
        message.setActive(request.active());
    }

    private static TickerMessageResponse response(TickerMessage m) {
        return new TickerMessageResponse(m.getId(), m.getTextUz(), m.getTextRu(), m.getLinkUrl(), m.getSortOrder(),
                m.isActive(), m.getCreatedAt(), m.getUpdatedAt());
    }
}
