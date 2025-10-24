package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;

    // Mask email (ex: m***e@gmail.com)
    private String maskEmail(String email) {
        return (email == null) ? "unknown" : email.replaceAll("(^.).*(@.*$)", "$1***$2");
    }

    // 1) Basic read operations
    public Iterable<User> getUsers() {
        log.info("UserService.getUsers - list all users");
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Integer id) {
        if (id == null) {
            log.warn("UserService.getUserById - null id");
            throw new IllegalArgumentException("Id must not be null.");
        }
        log.info("UserService.getUserById - id={}", id);
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        if (email == null) {
            log.warn("UserService.getUserByEmail - null email");
            throw new IllegalArgumentException("Email must not be null.");
        }
        String norm = email.trim().toLowerCase();
        log.info("UserService.getUserByEmail - email={}", maskEmail(norm));
        return userRepository.findByEmail(norm);
    }

    // 2) Registration
    public User registerUser(User user) {
        if (user == null || user.getEmail() == null || user.getPassword() == null) {
            log.warn("UserService.registerUser - missing fields");
            throw new IllegalArgumentException("User, email and password must not be null.");
        }

        String normalizedEmail = user.getEmail().trim().toLowerCase();
        log.info("UserService.registerUser - attempt email={}", maskEmail(normalizedEmail));

        userRepository.findByEmail(normalizedEmail).ifPresent(u -> {
            log.warn("UserService.registerUser - email already exists email={}", maskEmail(normalizedEmail));
            throw new IllegalArgumentException("This email already exists.");
        });

        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(user.getPassword())); // never log password

        BigDecimal balance = (user.getBalance() == null) ? BigDecimal.ZERO : user.getBalance();
        user.setBalance(balance);

        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(normalizedEmail);
        }

        User saved = userRepository.save(user);
        log.info("UserService.registerUser - success userId={} email={}", saved.getId(), maskEmail(saved.getEmail()));
        return saved;
    }

    public User getOrCreateOAuth2User(String email, String name) {
        if (email == null) {
            log.warn("UserService.getOrCreateOAuth2User - null email");
            throw new IllegalArgumentException("Email must not be null.");
        }

        String normalizedEmail = email.trim().toLowerCase();
        log.info("UserService.getOrCreateOAuth2User - lookup email={}", maskEmail(normalizedEmail));

        Optional<User> existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            log.info("UserService.getOrCreateOAuth2User - found userId={} email={}",
                    existing.get().getId(), maskEmail(normalizedEmail));
            return existing.get();
        }

        User u = new User();
        u.setEmail(normalizedEmail);
        u.setUsername((name == null || name.trim().isEmpty()) ? normalizedEmail : name.trim());
        u.setPassword(UUID.randomUUID().toString()); // random password (prevents form-login usage)

        User created = registerUser(u);
        log.info("UserService.getOrCreateOAuth2User - created userId={} email={}", created.getId(), maskEmail(created.getEmail()));
        return created;
    }

    // 3) Balance
    public BigDecimal getBalance(Integer userId) {
        if (userId == null) {
            log.warn("UserService.getBalance - null userId");
            throw new IllegalArgumentException("User id must not be null.");
        }
        log.info("UserService.getBalance - userId={}", userId);
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found : id = " + userId));
        return u.getBalance();
    }

    // 4) Money flows (delegated to WalletService)
    public BigDecimal deposit(Integer userId, BigDecimal amount) {
        if (userId == null) {
            log.warn("UserService.deposit - null userId");
            throw new IllegalArgumentException("User id must not be null.");
        }
        log.info("UserService.deposit - start userId={} amount={}", userId, amount);
        walletService.topUp(userId, amount, "Deposit");
        BigDecimal balance = getBalance(userId);
        log.info("UserService.deposit - success userId={} newBalance={}", userId, balance);
        return balance;
    }

    public Transaction transfer(Integer senderId, String receiverEmail, BigDecimal amount, String description) {
        if (senderId == null) {
            log.warn("UserService.transfer - null senderId");
            throw new IllegalArgumentException("Sender id must not be null.");
        }
        if (receiverEmail == null) {
            log.warn("UserService.transfer - null receiverEmail");
            throw new IllegalArgumentException("Receiver email must not be null.");
        }

        String recEmail = receiverEmail.trim().toLowerCase();
        log.info("UserService.transfer - start fromId={} toEmail={} amount={}", senderId, maskEmail(recEmail), amount);

        // Load sender (to ensure it's not the Bank user)
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found : id = " + senderId));

        if (sender.isBank()) {
            log.warn("UserService.transfer - bank user as sender senderId={}", senderId);
            throw new IllegalArgumentException("Bank user cannot participate in P2P transfers");
        }

        User receiver = userRepository.findByEmail(recEmail)
                .orElseThrow(() -> new IllegalArgumentException("Receiver not found : " + recEmail));

        if (receiver.isBank()) {
            log.warn("UserService.transfer - bank user as receiver receiverId={}", receiver.getId());
            throw new IllegalArgumentException("Bank user cannot participate in P2P transfers");
        }

        String desc = (description == null || description.trim().isEmpty())
                ? ("Transfer to " + receiver.getEmail())
                : description.trim();

        Transaction tx = walletService.transferP2P(senderId, receiver.getId(), amount, desc);
        log.info("UserService.transfer - success txId={} fromId={} toId={} amount={}",
                tx.getId(), senderId, receiver.getId(), amount);
        return tx;
    }

    // 5) Connection management
    public void addConnection(String ownerEmail, String friendEmail) {
        if (ownerEmail == null || friendEmail == null) {
            log.warn("UserService.addConnection - null emails");
            throw new IllegalArgumentException("Emails must not be null.");
        }
        String meEmail = ownerEmail.trim().toLowerCase();
        String frEmail = friendEmail.trim().toLowerCase();

        log.info("UserService.addConnection - owner={} friend={}", maskEmail(meEmail), maskEmail(frEmail));

        if (meEmail.equals(frEmail)) {
            log.warn("UserService.addConnection - self connection owner={}", maskEmail(meEmail));
            throw new IllegalArgumentException("You cannot add yourself.");
        }

        User me = userRepository.findByEmail(meEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + meEmail));
        User friend = userRepository.findByEmail(frEmail)
                .orElseThrow(() -> new IllegalArgumentException("Friend not found: " + frEmail));

        if (me.getConnections().contains(friend)) {
            log.warn("UserService.addConnection - already connected ownerId={} friendId={}", me.getId(), friend.getId());
            throw new IllegalArgumentException("This user is already in your connections.");
        }

        me.addConnection(friend);
        userRepository.save(me);
        log.info("UserService.addConnection - success ownerId={} friendId={}", me.getId(), friend.getId());
    }

    public void removeConnection(String ownerEmail, String friendEmail) {
        if (ownerEmail == null || friendEmail == null) {
            log.warn("UserService.removeConnection - null emails");
            throw new IllegalArgumentException("Emails must not be null.");
        }
        String meEmail = ownerEmail.trim().toLowerCase();
        String frEmail = friendEmail.trim().toLowerCase();

        log.info("UserService.removeConnection - owner={} friend={}", maskEmail(meEmail), maskEmail(frEmail));

        if (meEmail.equals(frEmail)) {
            log.warn("UserService.removeConnection - self remove owner={}", maskEmail(meEmail));
            throw new IllegalArgumentException("You cannot remove yourself.");
        }

        User me = userRepository.findByEmail(meEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + meEmail));
        User friend = userRepository.findByEmail(frEmail)
                .orElseThrow(() -> new IllegalArgumentException("Friend not found: " + frEmail));

        me.removeConnection(friend);
        userRepository.save(me);
        log.info("UserService.removeConnection - success ownerId={} friendId={}", me.getId(), friend.getId());
    }

    // 6) Profile update
    public User updateProfile(String currentEmail, String newUsername, String newEmail,
                              String currentPassword, String newPassword, String confirmPassword) {

        if (currentEmail == null) {
            log.warn("UserService.updateProfile - null currentEmail");
            throw new IllegalArgumentException("Email must not be null.");
        }

        log.info("UserService.updateProfile - start currentEmail={} newEmail={}",
                maskEmail(currentEmail), maskEmail(newEmail));

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
                        log.warn("UserService.updateProfile - email already exists newEmail={}", maskEmail(normalized));
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
                log.warn("UserService.updateProfile - missing current password");
                throw new IllegalArgumentException("Please enter your current password.");
            }
            if (!passwordEncoder.matches(currentPassword, me.getPassword())) {
                log.warn("UserService.updateProfile - current password mismatch userId={}", me.getId());
                throw new IllegalArgumentException("Current password is incorrect.");
            }
            if (!newPassword.equals(confirmPassword)) {
                log.warn("UserService.updateProfile - password confirmation mismatch userId={}", me.getId());
                throw new IllegalArgumentException("Password confirmation does not match.");
            }
            if (newPassword.length() < 8) {
                log.warn("UserService.updateProfile - new password too short userId={}", me.getId());
                throw new IllegalArgumentException("New password must be at least 8 characters.");
            }
            me.setPassword(passwordEncoder.encode(newPassword));
        }

        User saved = userRepository.save(me);
        log.info("UserService.updateProfile - success userId={} email={}", saved.getId(), maskEmail(saved.getEmail()));
        return saved;
    }
}