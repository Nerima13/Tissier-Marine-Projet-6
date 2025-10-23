package com.openclassrooms.payMyBuddy.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"connections", "connected"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @Column(name = "id")
    private Integer id;

    @Column(name = "username", nullable = false, length = 25, unique = true)
    private String username;

    @Column(name = "email", nullable = false, length = 25, unique = true)
    private String email;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // IBAN/BIC optional
    @Column(name = "iban", length = 34)
    private String iban;

    @Column(name = "bic", length = 11)
    private String bic;

    @Column(name = "is_bank", nullable = false)
    private boolean isBank = false;

    // Connections
    @ManyToMany
    @JoinTable(
            name = "user_connections",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "connection_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_user_connections_pair",
                    columnNames = {"user_id", "connection_id"}
            )
    )
    private Set<User> connections = new HashSet<>();

    @ManyToMany(mappedBy = "connections")
    private Set<User> connected = new HashSet<>();

    // Helpers
    public void addConnection(User other) {
        if (other == null || other == this) return;
        if (this.connections.add(other)) {
            other.connected.add(this);
        }
    }

    public void removeConnection(User other) {
        if (other == null) return;
        if (this.connections.remove(other)) {
            other.connected.remove(this);
        }
    }
}
