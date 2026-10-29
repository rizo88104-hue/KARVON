package uz.karvon.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * A raw provider call (request body) and our raw answer.
 */
@Getter
@Entity
@Immutable
@Table(name = "payment_provider_logs")
public class PaymentProviderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private PaymentProvider provider;

    @Column(name = "operation", nullable = false, length = 50)
    private String operation;

    @Column(name = "request", nullable = false)
    private String request;

    @Column(name = "response", nullable = false)
    private String response;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentProviderLog() {
    }

    public PaymentProviderLog(PaymentProvider provider, String operation, String request, String response,
                              UUID transactionId, Instant createdAt) {
        this.provider = provider;
        this.operation = operation;
        this.request = request;
        this.response = response;
        this.transactionId = transactionId;
        this.createdAt = createdAt;
    }
}
