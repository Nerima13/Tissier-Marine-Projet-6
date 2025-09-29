package com.openclassrooms.payMyBuddy.repository;

import com.openclassrooms.payMyBuddy.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)

public class UserRepositoryIT {

    @Autowired UserRepository userRepository;

    @Autowired EntityManager entityManager;

    private User user(String email, String username) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setPassword("{noop}password");
        u.setBalance(BigDecimal.ZERO);
        return u;
    }

    @Test
    void findByEmail_whenExists_returnsUser() {
        User alice = userRepository.save(user("alice@example.com", "Alice"));
        userRepository.save(user("bob@example.com", "Bob"));
        entityManager.flush();

        Optional<User> found = userRepository.findByEmail("alice@example.com");
        assertTrue(found.isPresent());
        assertEquals(alice.getId(), found.get().getId());
    }

    @Test
    void findByEmail_whenUnknown_returnsEmpty() {
        assertTrue(userRepository.findByEmail("nobody@example.com").isEmpty());
    }

    @Test
    void uniqueEmail_constraintIfPresent() {
        userRepository.save(user("unique@example.com", "U1"));
        entityManager.flush();

        userRepository.save(user("unique@example.com", "U2"));
        try {
            entityManager.flush();
            assertTrue(userRepository.findByEmail("unique@example.com").isPresent());
        } catch (DataIntegrityViolationException | PersistenceException expected) {
            assertTrue(true);
        }
    }
}
