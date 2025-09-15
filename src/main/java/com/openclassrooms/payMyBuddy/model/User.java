package com.openclassrooms.payMyBuddy.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
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

    @ManyToMany
    @JoinTable(
            name = "user_connections",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "connection_id")
    )
    @ToString.Exclude
    private List<User> connections = new ArrayList<>();

    @ManyToMany(mappedBy = "connections")
    @ToString.Exclude
    private List<User> connected = new ArrayList<>();


    // HELPERS
    public void addConnection(User other) {
        if (other == null || other == this) return;

        if (!this.connections.contains(other)) {
            this.connections.add(other);
        }
        if (!other.connected.contains(this)) {
            other.connected.add(this);
        }
    }

    public void removeConnection(User other) {
        if (other == null) return;
        this.connections.remove(other);
        other.connected.remove(this);
    }
}
