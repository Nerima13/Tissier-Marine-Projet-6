package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.TransactionRepository;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, TransactionRepository transactionRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
    }

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
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    // Registration : unique email, hashed password, balance initialized to 0.00 if null
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

        user.setBalance(balance.setScale(2, RoundingMode.HALF_UP));

        return userRepository.save(user);
    }

    public User getOrCreateOAuth2User(String email, String name) {
        if (email == null) {
            throw new IllegalArgumentException("Email must not be null.");
        }

        String normalizedEmail = email.trim().toLowerCase();

        Optional<User> existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            return existing.get();
        }

        User u = new User();
        u.setEmail(normalizedEmail);
        u.setUsername((name == null || name.trim().isEmpty()) ? normalizedEmail : name.trim());
        u.setPassword(UUID.randomUUID().toString()); // random strong password so form-login cannot guess it

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

    @Transactional
    public BigDecimal deposit(Integer userId, BigDecimal amount) {
        if (userId == null) {
            throw new IllegalArgumentException("User id must not be null.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be > 0.");
        }

        BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);

        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found : id = " + userId));

        u.setBalance(u.getBalance().add(a).setScale(2, RoundingMode.HALF_UP));
        userRepository.save(u);
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

        if (receiverEmail == null) {
            throw new IllegalArgumentException("Receiver email must not be null.");
        }

        String recEmail = receiverEmail.trim().toLowerCase();

        User receiver = userRepository.findByEmail(recEmail)
                .orElseThrow(() -> new IllegalArgumentException("Receiver not found : " + recEmail));

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
        t.setDescription((description == null || description.trim().isEmpty()) ? ("Transfer to " + receiver.getEmail()) : description.trim());

        return transactionRepository.save(t);
    }

    @Transactional
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

        // No duplicate
        if (me.getConnections().contains(friend)) {
            throw new IllegalArgumentException("This user is already in your connections.");
        }

        me.addConnection(friend); // updates join table
        userRepository.save(me);
    }

    @Transactional
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

        // If friend is not in the connections, removeConnection will simply have no effect
        me.removeConnection(friend);
        userRepository.save(me);
    }

    @Transactional
    public User updateProfile(String currentEmail, String newUsername, String newEmail, String currentPassword, String newPassword, String confirmPassword) {

        if (currentEmail == null) {
            throw new IllegalArgumentException("Email must not be null.");
        }

        // Fetch current user by email
        User me = getUserByEmail(currentEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + currentEmail));

        // Username
        if (newUsername != null) {
            String u = newUsername.trim();
            if (!u.isEmpty()) {
                me.setUsername(u);
            }
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
                // Note: the current session remains bound to the old email until the user signs in again.
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
