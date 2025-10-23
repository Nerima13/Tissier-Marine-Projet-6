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
            throw new IllegalArgumentException("Amount must be >= " + MIN_AMOUNT);
        }
    }

    // Fetch the Bank user (users.is_bank = true)
    private User bank() {
        return userRepo.findFirstByIsBankTrue()
                .orElseThrow(() -> new IllegalStateException("Bank user not found (is_bank = true)"));
    }

    // 1) TOP UP: add money to the user's account in the app (0.5% fee)
    @Transactional
    public Transaction topUp(Integer userId, BigDecimal amount, String description) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        validateAmount(amount);

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
        tx.setSender(bank);               // top-up =>  external source represented by Bank
        tx.setReceiver(user);
        tx.setDescription(description);
        tx.setGrossAmount(gross);
        tx.setFeeAmount(fee);
        tx.setNetAmount(net);

        return txRepo.save(tx);
    }

    // 2) P2P TRANSFER: transfer between users (0.5% fee)
    @Transactional
    public Transaction transferP2P(Integer senderId, Integer receiverId, BigDecimal amount, String description) {
        if (senderId == null || receiverId == null || senderId.equals(receiverId)) {
            throw new IllegalArgumentException("Invalid users");
        }

        // Guard: Bank user cannot participate in P2P transfers
        User bank = bank();
        if (senderId.equals(bank.getId()) || receiverId.equals(bank.getId())) {
            throw new IllegalArgumentException("Bank user cannot participate in P2P transfers");
        }

        validateAmount(amount);

        User sender = userRepo.findById(senderId).orElseThrow();
        User receiver = userRepo.findById(receiverId).orElseThrow();

        BigDecimal gross = round(amount);
        BigDecimal fee = round(gross.multiply(FEE_RATE));
        BigDecimal totalDebit = gross.add(fee);

        // Check if the sender has enough balance (for the full amount)
        if (sender.getBalance().compareTo(totalDebit) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }

        // Balance updates: debit full amount, credit gross amount to receiver
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

        return txRepo.save(tx);
    }

    // WITHDRAWAL: transfer money to the user's bank account (0.5% fee)
    @Transactional
    public Transaction withdrawToBank(Integer userId, BigDecimal amount, String description) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        validateAmount(amount);

        User user = userRepo.findById(userId).orElseThrow();

        // IBAN/BIC must be provided for withdrawal
        if (user.getIban() == null || user.getBic() == null) {
            throw new IllegalStateException("IBAN/BIC are required for withdrawal");
        }

        User bank = bank();

        BigDecimal gross = round(amount);
        BigDecimal fee = round(gross.multiply(FEE_RATE));
        BigDecimal totalDebit = gross.add(fee); // what the user pays

        if (user.getBalance().compareTo(totalDebit) < 0) {
            throw new IllegalStateException("Insufficient balance for withdrawal + fee");
        }

        // Update balance: debit amount + fee from the user's account
        user.setBalance(user.getBalance().subtract(totalDebit));

        // netAmount = amount sent to the external bank (outside the app)
        Transaction tx = new Transaction();
        tx.setType(TransactionType.WITHDRAWAL);
        tx.setSender(user);
        tx.setReceiver(bank); // outside the app represented by Bank
        tx.setDescription(description);
        tx.setGrossAmount(gross);
        tx.setFeeAmount(fee);
        tx.setNetAmount(gross);

        return txRepo.save(tx);
    }
}