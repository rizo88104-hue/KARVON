package uz.karvon.admin.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.admin.domain.TickerMessage;

import java.util.List;
import java.util.UUID;

public interface TickerMessageRepository extends JpaRepository<TickerMessage, UUID> {

    List<TickerMessage> findAllByOrderBySortOrderAscCreatedAtAsc();

    List<TickerMessage> findByActiveTrueOrderBySortOrderAscCreatedAtAsc();
}
