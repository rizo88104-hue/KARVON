package uz.karvon.catalog.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.catalog.api.dto.AdminCategoryResponse;
import uz.karvon.catalog.api.dto.CategoryRequest;
import uz.karvon.catalog.application.CategoryService;
import uz.karvon.common.api.Audited;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin: categories")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categories;

    /** Flat list by sort order; the public tree is GET /api/v1/catalog/categories. */
    @GetMapping
    public List<AdminCategoryResponse> list() {
        return categories.list();
    }

    @GetMapping("/{id}")
    public AdminCategoryResponse get(@PathVariable UUID id) {
        return categories.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Audited(action = "CATEGORY_CREATE", entityType = "CATEGORY")
    public AdminCategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return categories.create(request);
    }

    @PutMapping("/{id}")
    @Audited(action = "CATEGORY_UPDATE", entityType = "CATEGORY")
    public AdminCategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return categories.update(id, request);
    }

    /** Deletes its children too. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Audited(action = "CATEGORY_DELETE", entityType = "CATEGORY")
    public void delete(@PathVariable UUID id) {
        categories.delete(id);
    }
}
