package uz.karvon.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uz.karvon.common.domain.BaseEntity;

import java.time.Instant;

/**
 * A home page banner.
 */
@Getter
@Setter
@Entity
@Table(name = "banners")
public class Banner extends BaseEntity {

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "subtitle", length = 300)
    private String subtitle;

    @Column(name = "image_url", nullable = false, length = 1000)
    private String imageUrl;

    @Column(name = "link_url", length = 1000)
    private String linkUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Null: no start limit. */
    @Column(name = "active_from")
    private Instant activeFrom;

    /** Exclusive; null: no end limit. */
    @Column(name = "active_to")
    private Instant activeTo;

    /** Shown to customers at {@code now}. */
    public boolean visibleAt(Instant now) {
        return active && (activeFrom == null || !now.isBefore(activeFrom)) && (activeTo == null || now.isBefore(activeTo));
    }
}
