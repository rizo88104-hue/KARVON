package uz.karvon.settings.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.settings.domain.SlaSettings;

import java.util.Optional;
import java.util.UUID;

public interface SlaSettingsRepository extends JpaRepository<SlaSettings, UUID> {

    Optional<SlaSettings> findFirstByOrderByEffectiveFromDesc();

    Page<SlaSettings> findAllByOrderByEffectiveFromDesc(Pageable pageable);
}
