package mz.contas.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() { }

    private AppUser(String username, String passwordHash, Role role, Customer customer) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.customer = customer;
    }

    public static AppUser admin(String username, String passwordHash) {
        return new AppUser(username, passwordHash, Role.ADMIN, null);
    }

    public static AppUser client(String username, String passwordHash, Customer customer) {
        return new AppUser(username, passwordHash, Role.CLIENT, customer);
    }

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Customer getCustomer() { return customer; }
    public boolean isEnabled() { return enabled; }
}

