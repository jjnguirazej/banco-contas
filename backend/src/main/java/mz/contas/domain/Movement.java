package mz.contas.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Linha imutável do livro-razão de uma conta. Nunca é actualizada nem apagada. */
@Entity
@Table(name = "movements")
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", updatable = false)
    private Transfer transfer;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30, updatable = false)
    private MovementType type;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Column(nullable = false, length = 200, updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Movement() { }

    private Movement(Account account, Transfer transfer, MovementType type, BigDecimal amount, String description) {
        this.account = account;
        this.transfer = transfer;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = account.getBalance();
        this.description = description;
    }

    /** Deve ser chamado depois de o saldo da conta já ter sido actualizado. */
    public static Movement initialBalance(Account account, BigDecimal amount) {
        return new Movement(account, null, MovementType.SALDO_INICIAL, amount, "Saldo inicial");
    }

    public static Movement transferDebit(Transfer t) {
        return new Movement(t.getSourceAccount(), t, MovementType.TRANSFERENCIA_DEBITO, t.getAmount(), t.getDescription());
    }

    public static Movement transferCredit(Transfer t) {
        return new Movement(t.getTargetAccount(), t, MovementType.TRANSFERENCIA_CREDITO, t.getAmount(), t.getDescription());
    }

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public Transfer getTransfer() { return transfer; }
    public MovementType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}

