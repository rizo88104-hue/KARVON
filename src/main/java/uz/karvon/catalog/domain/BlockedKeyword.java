package uz.karvon.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uz.karvon.common.domain.BaseEntity;
import uz.karvon.translation.domain.Lang;

/**
 * A word that marks goods we cannot bring in. Stored in lower case.
 */
@Getter
@Setter
@Entity
@Table(name = "blocked_keywords")
public class BlockedKeyword extends BaseEntity {

    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    @Enumerated(EnumType.STRING)
    @Column(name = "lang", nullable = false, length = 2)
    private Lang lang;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private BlockedCategory category;

    protected BlockedKeyword() {
    }

    public BlockedKeyword(String keyword, Lang lang, BlockedCategory category) {
        this.keyword = keyword;
        this.lang = lang;
        this.category = category;
    }
}
