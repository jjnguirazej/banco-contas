package mz.contas.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 9, unique = true)
    private String nuit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Customer() { }

    public Customer(String fullName, String nuit) {
        this.fullName = fullName;
        this.nuit = nuit;
    }

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getNuit() { return nuit; }
    public Instant getCreatedAt() { return createdAt; }
}

