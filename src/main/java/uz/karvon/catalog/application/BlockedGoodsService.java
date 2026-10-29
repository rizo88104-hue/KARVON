package uz.karvon.catalog.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import uz.karvon.catalog.api.dto.BlockedKeywordRequest;
import uz.karvon.catalog.api.dto.BlockedKeywordResponse;
import uz.karvon.catalog.domain.BlockedCategory;
import uz.karvon.catalog.domain.BlockedGoodsRules;
import uz.karvon.catalog.domain.BlockedKeyword;
import uz.karvon.catalog.infrastructure.BlockedKeywordRepository;
import uz.karvon.common.api.PageResponse;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.translation.domain.Lang;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Blocked keywords (managed by ADMIN) and the check "can this product be brought in".
 * The rules are kept in memory and reloaded after a change, or after {@link #RELOAD_AFTER}
 * so that changes made on another instance are picked up too.
 */
@Service
@RequiredArgsConstructor
public class BlockedGoodsService {

    static final Duration RELOAD_AFTER = Duration.ofMinutes(1);

    private final BlockedKeywordRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    private volatile Snapshot snapshot;

    /** The blocked category, if any text (Chinese or translated title) contains a blocked keyword. */
    public Optional<BlockedCategory> check(String... texts) {
        return rules().match(texts);
    }

    public boolean isBlocked(String... texts) {
        return check(texts).isPresent();
    }

    @Transactional(readOnly = true)
    public PageResponse<BlockedKeywordResponse> list(Lang lang, BlockedCategory category, String q, int page, int size) {
        Specification<BlockedKeyword> filter = Specification.allOf(
                (root, query, cb) -> lang == null ? null : cb.equal(root.get("lang"), lang),
                (root, query, cb) -> category == null ? null : cb.equal(root.get("category"), category),
                (root, query, cb) -> q == null || q.isBlank() ? null
                        : cb.like(root.get("keyword"), "%" + q.trim().toLowerCase(Locale.ROOT) + "%"));
        return PageResponse.of(repository.findAll(filter, PageRequest.of(page, size, Sort.by("lang", "keyword"))),
                BlockedGoodsService::toResponse);
    }

    @Transactional
    public BlockedKeywordResponse create(BlockedKeywordRequest request) {
        String keyword = normalize(request.keyword());
        if (repository.existsByKeywordAndLang(keyword, request.lang())) {
            throw duplicate();
        }
        BlockedKeyword saved = repository.save(new BlockedKeyword(keyword, request.lang(), request.category()));
        events.publishEvent(new BlockedKeywordsChangedEvent());
        return toResponse(saved);
    }

    @Transactional
    public BlockedKeywordResponse update(UUID id, BlockedKeywordRequest request) {
        BlockedKeyword keyword = load(id);
        String text = normalize(request.keyword());
        if (repository.existsByKeywordAndLangAndIdNot(text, request.lang(), id)) {
            throw duplicate();
        }
        keyword.setKeyword(text);
        keyword.setLang(request.lang());
        keyword.setCategory(request.category());
        events.publishEvent(new BlockedKeywordsChangedEvent());
        return toResponse(keyword);
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(load(id));
        events.publishEvent(new BlockedKeywordsChangedEvent());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onChanged(BlockedKeywordsChangedEvent event) {
        snapshot = null;
    }

    private BlockedGoodsRules rules() {
        Snapshot current = snapshot;
        Instant now = Instant.now(clock);
        if (current == null || current.loadedAt().plus(RELOAD_AFTER).isBefore(now)) {
            current = new Snapshot(new BlockedGoodsRules(repository.findAll()), now);
            snapshot = current;
        }
        return current.rules();
    }

    private BlockedKeyword load(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("BLOCKED_KEYWORD_NOT_FOUND", "error.blockedKeyword.notFound"));
    }

    private static String normalize(String keyword) {
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private static BusinessException duplicate() {
        return new BusinessException(HttpStatus.CONFLICT, "BLOCKED_KEYWORD_EXISTS",
                "error.blockedKeyword.exists");
    }

    private static BlockedKeywordResponse toResponse(BlockedKeyword keyword) {
        return new BlockedKeywordResponse(keyword.getId(), keyword.getKeyword(), keyword.getLang(),
                keyword.getCategory(), keyword.getCreatedAt());
    }

    private record Snapshot(BlockedGoodsRules rules, Instant loadedAt) {
    }
}
