package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;

    // 1) Basic read operations
    public Iterable<User> getUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Integer id) {
        if (id == null) throw new IllegalArgumentException("Id must not be null.");
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        if (email == null) throw new IllegalArgumentException("Email must not be null.");
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    // 2) Registration
    public User registerUser(User user) {
        if (user == null || user.getEmail() == null || user.getPassword() == null) {
            throw new IllegalArgumentException("User, email and password must not be null.");
        }

        String normalizedEmail = user.getEmail().trim().toLowerCase();
        userRepository.findByEmail(normalizedEmail).ifPresent(u -> {
            throw new IllegalArgumentException("This email already exists.");
        });

        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        BigDecimal balance = (user.getBalance() == null) ? BigDecimal.ZERO : user.getBalance();
        user.setBalance(balance); // The rounding/scale will be handled by WalletService when money flows occur

        // If the username can be null from the DTO, ensure a default value
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(normalizedEmail);
        }

        return userRepository.save(user);
    }

    public User getOrCreateOAuth2User(String email, String name) {
        if (email == null) throw new IllegalArgumentException("Email must not be null.");

        String normalizedEmail = email.trim().toLowerCase();

        Optional<User> existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            return existing.get();
        }

        User u = new User();
        u.setEmail(normalizedEmail);
        u.setUsername((name == null || name.trim().isEmpty()) ? normalizedEmail : name.trim());
        u.setPassword(UUID.randomUUID().toString()); // random password (prevents form-login usage)

        return registerUser(u);
    }

    // 3) Balance
    public BigDecimal getBalance(Integer userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found : id = " + userId));
        return u.getBalance();
    }

    // 4) Money flows (delegated to WalletService)
    /**
     * Deposit (TOP_UP) with 0.5% fee (only the net amount is credited).
     * Keeps the same signature (returns the updated balance).
     */
    public BigDecimal deposit(Integer userId, BigDecimal amount) {
        if (userId == null) throw new IllegalArgumentException("User id must not be null.");

        walletService.topUp(userId, amount, "Deposit");
        // Reload the user to return the updated balance
        return getBalance(userId);
    }

    /**
     * P2P transfer: the sender pays a 0.5% fee,
     * the receiver gets the full (gross) amount.
     * Keeps the same signature but aligns persistence with Transaction (gross/fee/net + type).
     */
    public Transaction transfer(Integer senderId, String receiverEmail, BigDecimal amount, String description) {
        if (senderId == null) throw new IllegalArgumentException("Sender id must not be null.");
        if (receiverEmail == null) throw new IllegalArgumentException("Receiver email must not be null.");

        // Load sender (to ensure it's not the Bank user)
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found : id = " + senderId));

        if (sender.isBank()) {
            throw new IllegalArgumentException("Bank user cannot participate in P2P transfers");
        }

        String recEmail = receiverEmail.trim().toLowerCase();
        User receiver = userRepository.findByEmail(recEmail)
                .orElseThrow(() -> new IllegalArgumentException("Receiver not found : " + recEmail));

        if (receiver.isBank()) {
            throw new IllegalArgumentException("Bank user cannot participate in P2P transfers");
        }

        String desc = (description == null || description.trim().isEmpty())
                ? ("Transfer to " + receiver.getEmail())
                : description.trim();

        return walletService.transferP2P(senderId, receiver.getId(), amount, desc);
    }

    // 5) Connection management
    public void addConnection(String ownerEmail, String friendEmail) {
        if (ownerEmail == null || friendEmail == null) {
            throw new IllegalArgumentException("Emails must not be null.");
        }
        String meEmail = ownerEmail.trim().toLowerCase();
        String frEmail = friendEmail.trim().toLowerCase();
        if (meEmail.equals(frEmail)) {
            throw new IllegalArgumentException("You cannot add yourself.");
        }

        User me = userRepository.findByEmail(meEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + meEmail));
        User friend = userRepository.findByEmail(frEmail)
                .orElseThrow(() -> new IllegalArgumentException("Friend not found: " + frEmail));

        if (me.getConnections().contains(friend)) {
            throw new IllegalArgumentException("This user is already in your connections.");
        }

        me.addConnection(friend);
        userRepository.save(me);
    }

    public void removeConnection(String ownerEmail, String friendEmail) {
        if (ownerEmail == null || friendEmail == null) {
            throw new IllegalArgumentException("Emails must not be null.");
        }
        String meEmail = ownerEmail.trim().toLowerCase();
        String frEmail = friendEmail.trim().toLowerCase();
        if (meEmail.equals(frEmail)) {
            throw new IllegalArgumentException("You cannot remove yourself.");
        }

        User me = userRepository.findByEmail(meEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + meEmail));
        User friend = userRepository.findByEmail(frEmail)
                .orElseThrow(() -> new IllegalArgumentException("Friend not found: " + frEmail));

        me.removeConnection(friend);
        userRepository.save(me);
    }

    // 6) Profile update
    public User updateProfile(String currentEmail, String newUsername, String newEmail,
                              String currentPassword, String newPassword, String confirmPassword) {

        if (currentEmail == null) throw new IllegalArgumentException("Email must not be null.");

        User me = getUserByEmail(currentEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + currentEmail));

        // Username
        if (newUsername != null) {
            String u = newUsername.trim();
            if (!u.isEmpty()) me.setUsername(u);
        }

        // Email
        if (newEmail != null) {
            String normalized = newEmail.trim().toLowerCase();
            if (!normalized.isBlank() && !normalized.equals(me.getEmail())) {
                userRepository.findByEmail(normalized).ifPresent(other -> {
                    if (!other.getId().equals(me.getId())) {
                        throw new IllegalArgumentException("This email already exists.");
                    }
                });
                me.setEmail(normalized);
            }
        }

        // Password
        boolean wantsPwdChange = newPassword != null && !newPassword.isBlank();
        if (wantsPwdChange) {
            if (currentPassword == null || currentPassword.isBlank()) {
                throw new IllegalArgumentException("Please enter your current password.");
            }
            if (!passwordEncoder.matches(currentPassword, me.getPassword())) {
                throw new IllegalArgumentException("Current password is incorrect.");
            }
            if (!newPassword.equals(confirmPassword)) {
                throw new IllegalArgumentException("Password confirmation does not match.");
            }
            if (newPassword.length() < 8) {
                throw new IllegalArgumentException("New password must be at least 8 characters.");
            }
            me.setPassword(passwordEncoder.encode(newPassword));
        }

        return userRepository.save(me);
    }
}