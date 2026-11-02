package uz.karvon.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Our own category, edited by ADMIN (the first ones are seeded by Flyway).
 * A category opens a Taobao search for {@code searchKeywordZh}. Deleting a category deletes its children.
 */
@Getter
@Setter
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "slug", nullable = false, length = 60)
    private String slug;

    @Column(name = "name_uz", nullable = false, length = 100)
    private String nameUz;

    @Column(name = "name_ru", nullable = false, length = 100)
    private String nameRu;

    @Column(name = "search_keyword_zh", nullable = false, length = 100)
    private String searchKeywordZh;

    /** An icon URL or icon name; optional. */
    @Column(name = "icon", length = 300)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
