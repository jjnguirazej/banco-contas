package mz.contas.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID reference;

    @Column(name = "idempotency_key", unique = true, length = 100, updatable = false)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false, updatable = false)
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_account_id", nullable = false, updatable = false)
    private Account targetAccount;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 200, updatable = false)
    private String description;

    @Column(name = "created_by", nullable = false, length = 50, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transfer() { }

    public Transfer(String idempotencyKey, Account source, Account target,
                    BigDecimal amount, String description, String createdBy) {
        this.reference = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.sourceAccount = source;
        this.targetAccount = target;
        this.amount = amount;
        this.description = description;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public UUID getReference() { return reference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Account getSourceAccount() { return sourceAccount; }
    public Account getTargetAccount() { return targetAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
