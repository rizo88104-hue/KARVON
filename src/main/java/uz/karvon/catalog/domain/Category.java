package uz.karvon.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/**
 * Our own category. Seeded by Flyway; a category opens a Taobao search for {@code searchKeywordZh}.
 */
@Getter
@Entity
@Immutable
@Table(name = "categories")
public class Category {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "name_uz", nullable = false)
    private String nameUz;

    @Column(name = "name_ru", nullable = false)
    private String nameRu;

    @Column(name = "search_keyword_zh", nullable = false)
    private String searchKeywordZh;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Category() {
    }
}
