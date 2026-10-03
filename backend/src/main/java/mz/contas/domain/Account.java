package mz.contas.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import mz.contas.common.BusinessRuleException;

/**
 * Conta bancária. O saldo só muda através de debit() e credit(),
 * que garantem as regras de valor positivo e de saldo nunca negativo.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    private AccountType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() { }

    public Account(String accountNumber, Customer customer, AccountType type) {
        this.accountNumber = accountNumber;
        this.customer = customer;
        this.type = type;
        this.balance = BigDecimal.ZERO.setScale(2);
    }

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new BusinessRuleException("SALDO_INSUFICIENTE",
                    "Saldo insuficiente na conta " + accountNumber + ".");
        }
        this.balance = this.balance.subtract(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("VALOR_INVALIDO", "O valor tem de ser positivo.");
        }
    }

    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public Customer getCustomer() { return customer; }
    public AccountType getType() { return type; }
    public BigDecimal getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
}

