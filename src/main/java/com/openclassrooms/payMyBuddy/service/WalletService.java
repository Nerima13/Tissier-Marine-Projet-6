package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.model.TransactionType;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.TransactionRepository;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserRepository userRepo;
    private final TransactionRepository txRepo;

    // 0.5% fee
    private static final BigDecimal FEE_RATE = new BigDecimal("0.005");

    // Round to 2 decimal places (HALF_UP)
    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    // If a transaction with the same key already exists, return it
    private Transaction findExistingByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) return null;
        Optional<Transaction> existing = txRepo.findByIdempotencyKey(idempotencyKey);
        return existing.orElse(null);
    }

    // 1) TOP UP: add money to the user's account in the app (0.5% fee)
    @Transactional
    public Transaction topUp(Integer userId, BigDecimal amount, String idempotencyKey, String description) {
        Transaction existing = findExistingByIdempotencyKey(idempotencyKey);
        if (existing != null) return existing;

        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new IllegalArgumentException("Amount must be >= 0.01");
        }

        User user = userRepo.findById(userId).orElseThrow();

        BigDecimal fee = round(amount.multiply(FEE_RATE));
        BigDecimal net = amount.subtract(fee);

        // Credit only the net amount to the user's balance
        user.setBalance(user.getBalance().add(net));

        // Save the transaction
        Transaction tx = new Transaction();
        tx.setType(TransactionType.TOP_UP);
        tx.setSender(null);               // top-up => no internal sender
        tx.setReceiver(user);
        tx.setDescription(description);
        tx.setGrossAmount(round(amount));
        tx.setFeeAmount(fee);
        tx.setNetAmount(net);
        tx.setIdempotencyKey(idempotencyKey);

        return txRepo.save(tx);
    }

    // 2) P2P TRANSFER: transfer between users (0.5% fee)
    @Transactional
    public Transaction transferP2P(Integer senderId, Integer receiverId, BigDecimal amount, String idempotencyKey, String description) {
        Transaction existing = findExistingByIdempotencyKey(idempotencyKey);
        if (existing != null) return existing;

        if (senderId == null || receiverId == null || senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Invalid users");
        }
        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new IllegalArgumentException("Amount must be >= 0.01");
        }

        User sender = userRepo.findById(senderId).orElseThrow();
        User receiver = userRepo.findById(receiverId).orElseThrow();

        BigDecimal fee = round(amount.multiply(FEE_RATE));
        BigDecimal net = amount.subtract(fee);

        // Check if the sender has enough balance (for the full amount)
        if (sender.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }

        // Balance updates: debit full amount, credit net amount to receiver
        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(net));

        // Save the transaction
        Transaction tx = new Transaction();
        tx.setType(TransactionType.P2P_TRANSFER);
        tx.setSender(sender);
        tx.setReceiver(receiver);
        tx.setDescription(description);
        tx.setGrossAmount(round(amount));
        tx.setFeeAmount(fee);
        tx.setNetAmount(net);
        tx.setIdempotencyKey(idempotencyKey);

        return txRepo.save(tx);
    }

    // WITHDRAWAL: transfer money to the user's bank account (0.5% fee)
    @Transactional
    public Transaction withdrawToBank(Integer userId, BigDecimal amount, String idempotencyKey, String description) {
        Transaction existing = findExistingByIdempotencyKey(idempotencyKey);
        if (existing != null) return existing;

        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new IllegalArgumentException("Amount must be >= 0.01");
        }

        User user = userRepo.findById(userId).orElseThrow();

        // IBAN/BIC must be provided for withdrawal
        if (user.getIban() == null || user.getBic() == null) {
            throw new IllegalStateException("IBAN/BIC are required for withdrawal");
        }

        BigDecimal fee = round(amount.multiply(FEE_RATE));
        BigDecimal totalDebit = amount.add(fee); // ce que l'utilisateur paie

        if (user.getBalance().compareTo(totalDebit) < 0) {
            throw new IllegalStateException("Insufficient balance for withdrawal + fee");
        }

        // Update balance: debit amount + fee from the user's account
        user.setBalance(user.getBalance().subtract(totalDebit));

        // netAmount = amount sent to the external bank (outside the app)
        Transaction tx = new Transaction();
        tx.setType(TransactionType.WITHDRAWAL);
        tx.setSender(user);
        tx.setReceiver(null); // outside the app
        tx.setDescription(description);
        tx.setGrossAmount(round(amount));
        tx.setFeeAmount(fee);
        tx.setNetAmount(amount);
        tx.setIdempotencyKey(idempotencyKey);

        return txRepo.save(tx);
    }
}