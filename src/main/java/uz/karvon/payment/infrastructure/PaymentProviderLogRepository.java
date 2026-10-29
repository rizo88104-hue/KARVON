package uz.karvon.payment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.payment.domain.PaymentProviderLog;

import java.util.UUID;

public interface PaymentProviderLogRepository extends JpaRepository<PaymentProviderLog, UUID> {
}
