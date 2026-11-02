package uz.karvon.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uz.karvon.common.domain.BaseEntity;

/**
 * A text page of the site (FAQ, delivery, returns, public offer). The body is Markdown.
 */
@Getter
@Setter
@Entity
@Table(name = "pages")
public class ContentPage extends BaseEntity {

    @Column(name = "slug", nullable = false, unique = true, length = 60)
    private String slug;

    @Column(name = "title_uz", nullable = false, length = 200)
    private String titleUz;

    @Column(name = "title_ru", nullable = false, length = 200)
    private String titleRu;

    @Column(name = "body_uz", nullable = false)
    private String bodyUz;

    @Column(name = "body_ru", nullable = false)
    private String bodyRu;

    /** Unpublished pages are hidden from the public endpoint. */
    @Column(name = "published", nullable = false)
    private boolean published;
}
