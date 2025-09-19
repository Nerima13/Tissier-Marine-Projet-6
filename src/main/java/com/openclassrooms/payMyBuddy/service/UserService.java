package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.TransactionRepository;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // Basic read operations
    public Iterable<User> getUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("Id must not be null.");
        }
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email must not be null.");
        }
        return userRepository.findByEmail(email);
    }

    // Registration : unique email, hashed password, balance initialized to 0.00 if null
    public User registerUser(User user) {
        userRepository.findByEmail(user.getEmail()).ifPresent(u -> {
            throw new IllegalArgumentException("This email already exists.");
        });

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        if (user.getBalance() == null) {
            user.setBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        } else {
            user.setBalance(user.getBalance().setScale(2, RoundingMode.HALF_UP));
        }

        return userRepository.save(user);
    }

    public User getOrCreateOAuth2User(String email, String name) {
        if (email == null) {
            throw new IllegalArgumentException("Email must not be null.");
        }

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }

        User u = new User();
        u.setEmail(email);
        if (name == null || name.isEmpty()) {
            u.setUsername(email);
        } else {
            u.setUsername(name);
        }
        u.setPassword("password");

        return registerUser(u);
    }

    /* --- PROTOTYPE ---
       - Send money to any already-registered user
       - No commission
       - No deposit/withdraw (in V1)
    */

    // Current balance
    public BigDecimal getBalance(Integer userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found : id = " + userId));
        return u.getBalance();
    }

    // Simple transfer without fee to a registered user (by email)
    @Transactional
    public Transaction transfer(Integer senderId, String receiverEmail, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be > 0.");
        }
        BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found : id = " + senderId));

        User receiver = userRepository.findByEmail(receiverEmail)
                .orElseThrow(() -> new IllegalArgumentException("Receiver not found : " + receiverEmail));

        if (sender.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("You cannot send money to yourself.");
        }
        if (sender.getBalance().compareTo(a) < 0) {
            throw new IllegalArgumentException("Insufficient balance.");
        }

        // Debit sender / Credit receiver
        sender.setBalance(sender.getBalance().subtract(a).setScale(2, RoundingMode.HALF_UP));
        receiver.setBalance(receiver.getBalance().add(a).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(sender);
        userRepository.save(receiver);

        // Persist transaction
        Transaction t = new Transaction();
        t.setSender(sender);
        t.setReceiver(receiver);
        t.setAmount(a);
        t.setDescription(description != null ? description : "Transfer to " + receiver.getEmail());

        return transactionRepository.save(t);
    }
}
