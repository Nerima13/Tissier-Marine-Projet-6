package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.model.TransactionType;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.TransactionRepository;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final UserRepository userRepo;
    private final TransactionRepository txRepo;

    // 0.5% fee
    private static final BigDecimal FEE_RATE = new BigDecimal("0.005");
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    // Round to 2 decimal places (HALF_UP)
    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0) {
            log.warn("Validation failed - amount invalid: amount={} (min={})", amount, MIN_AMOUNT);
            throw new IllegalArgumentException("Amount must be >= " + MIN_AMOUNT);
        }
    }

    // Fetch the Bank user (users.is_bank = true)
    private User bank() {
        Optional<User> opt = userRepo.findFirstByIsBankTrue();
        if (opt.isEmpty()) {
            log.error("Bank user not found (is_bank = true)");
            throw new IllegalStateException("Bank user not found (is_bank = true)");
        }
        return opt.get();
    }

    // 1) TOP UP: add money to the user's account in the app (0.5% fee)
    @Transactional
    public Transaction topUp(Integer userId, BigDecimal amount, String description) {
        if (userId == null) {
            log.warn("TopUp - missing userId");
            throw new IllegalArgumentException("userId is required");
        }
        validateAmount(amount);

        log.info("TopUp - start userId={} amount={}", userId, amount);

        User user = userRepo.findById(userId).orElseThrow();
        User bank = bank();

        BigDecimal gross = round(amount);
        BigDecimal fee = round(gross.multiply(FEE_RATE));
        BigDecimal net = round(gross.subtract(fee));

        // Credit only the net amount to the user's balance
        user.setBalance(user.getBalance().add(net));

        // Save the transaction
        Transaction tx = new Transaction();
        tx.setType(TransactionType.TOP_UP);
        tx.setSender(bank);               // top-up => external source represented by Bank
        tx.setReceiver(user);
        tx.setDescription(description);
        tx.setGrossAmount(gross);
        tx.setFeeAmount(fee);
        tx.setNetAmount(net);

        Transaction saved = txRepo.save(tx);
        log.info("TopUp - success userId={} txId={} gross={} fee={} net={}",
                userId, saved.getId(), gross, fee, net);
        return saved;
    }

    // 2) P2P TRANSFER: transfer between users (0.5% fee)
    @Transactional
    public Transaction transferP2P(Integer senderId, Integer receiverId, BigDecimal amount, String description) {
        if (senderId == null || receiverId == null || senderId.equals(receiverId)) {
            log.warn("P2P - invalid users: senderId={} receiverId={}", senderId, receiverId);
            throw new IllegalArgumentException("Invalid users");
        }

        User bank = bank();
        if (senderId.equals(bank.getId()) || receiverId.equals(bank.getId())) {
            log.warn("P2P - bank user involved: senderId={} receiverId={}", senderId, receiverId);
            throw new IllegalArgumentException("Bank user cannot participate in P2P transfers");
        }

        validateAmount(amount);
        log.info("P2P - start fromId={} toId={} amount={}", senderId, receiverId, amount);

        User sender = userRepo.findById(senderId).orElseThrow();
        User receiver = userRepo.findById(receiverId).orElseThrow();

        BigDecimal gross = round(amount);
        BigDecimal fee = round(gross.multiply(FEE_RATE));
        BigDecimal totalDebit = gross.add(fee);

        // Check balance
        if (sender.getBalance().compareTo(totalDebit) < 0) {
            log.warn("P2P - insufficient balance fromId={} balance={} required={}",
                    senderId, sender.getBalance(), totalDebit);
            throw new IllegalStateException("Insufficient balance");
        }

        // Balance updates
        sender.setBalance(sender.getBalance().subtract(totalDebit));
        receiver.setBalance(receiver.getBalance().add(gross));

        // Save the transaction
        Transaction tx = new Transaction();
        tx.setType(TransactionType.P2P_TRANSFER);
        tx.setSender(sender);
        tx.setReceiver(receiver);
        tx.setDescription(description);
        tx.setGrossAmount(gross);
        tx.setFeeAmount(fee);
        tx.setNetAmount(gross);

        Transaction saved = txRepo.save(tx);
        log.info("P2P - success txId={} fromId={} toId={} gross={} fee={} totalDebit={}",
                saved.getId(), senderId, receiverId, gross, fee, totalDebit);
        return saved;
    }

    // WITHDRAWAL: transfer money to the user's bank account (0.5% fee)
    @Transactional
    public Transaction withdrawToBank(Integer userId, BigDecimal amount, String description) {
        if (userId == null) {
            log.warn("Withdraw - missing userId");
            throw new IllegalArgumentException("userId is required");
        }
        validateAmount(amount);

        log.info("Withdraw - start userId={} amount={}", userId, amount);

        User user = userRepo.findById(userId).orElseThrow();

        // IBAN/BIC must be provided for withdrawal
        if (user.getIban() == null || user.getBic() == null) {
            log.warn("Withdraw - missing IBAN/BIC userId={}", userId);
            throw new IllegalStateException("IBAN/BIC are required for withdrawal");
        }

        User bank = bank();

        BigDecimal gross = round(amount);
        BigDecimal fee = round(gross.multiply(FEE_RATE));
        BigDecimal totalDebit = gross.add(fee); // what the user pays

        if (user.getBalance().compareTo(totalDebit) < 0) {
            log.warn("Withdraw - insufficient balance userId={} balance={} required={}",
                    userId, user.getBalance(), totalDebit);
            throw new IllegalStateException("Insufficient balance for withdrawal + fee");
        }

        // Update balance
        user.setBalance(user.getBalance().subtract(totalDebit));

        // Save the transaction
        Transaction tx = new Transaction();
        tx.setType(TransactionType.WITHDRAWAL);
        tx.setSender(user);
        tx.setReceiver(bank); // outside the app represented by Bank
        tx.setDescription(description);
        tx.setGrossAmount(gross);
        tx.setFeeAmount(fee);
        tx.setNetAmount(gross);

        Transaction saved = txRepo.save(tx);
        log.info("Withdraw - success txId={} userId={} gross={} fee={} totalDebit={}",
                saved.getId(), userId, gross, fee, totalDebit);
        return saved;
    }
}
