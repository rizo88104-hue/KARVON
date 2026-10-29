package uz.karvon.settings.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.settings.domain.PricingSettings;

import java.util.Optional;
import java.util.UUID;

public interface PricingSettingsRepository extends JpaRepository<PricingSettings, UUID> {

    /** The active settings. */
    Optional<PricingSettings> findFirstByOrderByEffectiveFromDesc();

    Page<PricingSettings> findAllByOrderByEffectiveFromDesc(Pageable pageable);
}
