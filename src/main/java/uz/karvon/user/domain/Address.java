package uz.karvon.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import uz.karvon.common.domain.BaseEntity;

import java.util.UUID;

/**
 * Delivery address of a user. Every user with addresses has exactly one default address.
 */
@Getter
@Setter
@Entity
@Table(name = "addresses")
public class Address extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "region", nullable = false, length = 100)
    private String region;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "street", nullable = false)
    private String street;

    @Column(name = "house", nullable = false, length = 50)
    private String house;

    @Column(name = "apartment", length = 50)
    private String apartment;

    @Column(name = "landmark")
    private String landmark;

    /** Contact phone for delivery (E.164); null means the account phone. */
    @Column(name = "phone", length = 13)
    private String phone;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    protected Address() {
    }

    public Address(UUID userId) {
        this.userId = userId;
    }
}
