package uz.karvon.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uz.karvon.common.domain.BaseEntity;

/**
 * A short message that runs along the site's top bar (the ticker), in Uzbek and Russian, with an optional link.
 */
@Getter
@Setter
@Entity
@Table(name = "ticker_messages")
public class TickerMessage extends BaseEntity {

    @Column(name = "text_uz", nullable = false, length = 200)
    private String textUz;

    @Column(name = "text_ru", nullable = false, length = 200)
    private String textRu;

    @Column(name = "link_url", length = 1000)
    private String linkUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;
}
