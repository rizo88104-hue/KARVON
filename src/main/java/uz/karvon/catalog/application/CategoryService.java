package uz.karvon.catalog.application;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.catalog.api.dto.AdminCategoryResponse;
import uz.karvon.catalog.api.dto.CategoryRequest;
import uz.karvon.catalog.domain.Category;
import uz.karvon.catalog.infrastructure.CategoryRepository;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ADMIN editing of our category tree. The public tree is {@link CatalogService#categories()}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categories;

    /** All categories, flat, by sort order. */
    public List<AdminCategoryResponse> list() {
        return categories.findAllByOrderBySortOrderAsc().stream().map(CategoryService::response).toList();
    }

    public AdminCategoryResponse get(UUID id) {
        return response(load(id));
    }

    public Optional<AdminCategoryResponse> find(UUID id) {
        return categories.findById(id).map(CategoryService::response);
    }

    @Transactional
    public AdminCategoryResponse create(CategoryRequest request) {
        if (categories.existsBySlug(request.slug())) {
            throw slugTaken();
        }
        checkParent(null, request.parentId());
        Category category = new Category();
        apply(category, request);
        return response(categories.save(category));
    }

    @Transactional
    public AdminCategoryResponse update(UUID id, CategoryRequest request) {
        Category category = load(id);
        if (categories.existsBySlugAndIdNot(request.slug(), id)) {
            throw slugTaken();
        }
        checkParent(id, request.parentId());
        apply(category, request);
        return response(categories.saveAndFlush(category));
    }

    /** Its children are deleted too. */
    @Transactional
    public void delete(UUID id) {
        categories.delete(load(id));
    }

    /** The parent must exist and must not be the category itself or one of its descendants. */
    private void checkParent(UUID id, UUID parentId) {
        if (parentId == null) {
            return;
        }
        Map<UUID, Category> all = categories.findAll().stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        if (!all.containsKey(parentId)) {
            throw new BusinessException("CATEGORY_PARENT_NOT_FOUND", "error.category.parentNotFound");
        }
        for (UUID at = parentId; at != null; at = all.get(at) == null ? null : all.get(at).getParentId()) {
            if (at.equals(id)) {
                throw new BusinessException("CATEGORY_PARENT_INVALID", "error.category.parentInvalid");
            }
        }
    }

    private static void apply(Category category, CategoryRequest request) {
        category.setParentId(request.parentId());
        category.setSlug(request.slug());
        category.setNameUz(request.nameUz().trim());
        category.setNameRu(request.nameRu().trim());
        category.setSearchKeywordZh(request.searchKeywordZh().trim());
        category.setIcon(request.icon() == null || request.icon().isBlank() ? null : request.icon().trim());
        category.setSortOrder(request.sortOrder());
    }

    private Category load(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "error.category.notFound"));
    }

    private static BusinessException slugTaken() {
        return new BusinessException(HttpStatus.CONFLICT, "CATEGORY_SLUG_TAKEN", "error.category.slugTaken");
    }

    private static AdminCategoryResponse response(Category c) {
        return new AdminCategoryResponse(c.getId(), c.getParentId(), c.getSlug(), c.getNameUz(), c.getNameRu(),
                c.getSearchKeywordZh(), c.getIcon(), c.getSortOrder());
    }
}
