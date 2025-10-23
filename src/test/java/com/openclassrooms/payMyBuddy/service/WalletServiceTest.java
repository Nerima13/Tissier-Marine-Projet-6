package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.model.TransactionType;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.TransactionRepository;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock UserRepository userRepository;
    @Mock TransactionRepository transactionRepository;

    @InjectMocks WalletService service;

    private User makeUser(Integer id, String email, String balance) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setBalance(new BigDecimal(balance));
        return u;
    }

    @BeforeEach
    void resetMocks() {
        reset(userRepository, transactionRepository);
    }

    // TOP UP

    // Happy path: topUp credits (amount - 0.5% fee), persists a transaction with rounded amounts
    @Test
    void topUp_success_updatesBalance_andSavesTransaction() {
        User user = makeUser(1, "user@example.com", "0.00");

        User bank = makeUser(100, "bank@gmail.com", "0.00");
        bank.setBank(true);
        when(userRepository.findFirstByIsBankTrue()).thenReturn(Optional.of(bank));

        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tr = service.topUp(1, new BigDecimal("100.00"), "Deposit");

        // Assert (0.5% fee → 0.50; net 99.50)
        assertEquals(new BigDecimal("99.50"), user.getBalance());
        assertNotNull(tr);
        assertEquals(TransactionType.TOP_UP, tr.getType());

        assertEquals(bank, tr.getSender());
        assertEquals(user, tr.getReceiver());

        assertEquals(new BigDecimal("100.00"), tr.getGrossAmount());
        assertEquals(new BigDecimal("0.50"), tr.getFeeAmount());
        assertEquals(new BigDecimal("99.50"), tr.getNetAmount());
        assertEquals("Deposit", tr.getDescription());
    }

    // Validation: amount must be >= 0.01
    @Test
    void topUp_invalidAmount_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.topUp(1, new BigDecimal("0.001"), "tiny"));
        assertThrows(IllegalArgumentException.class,
                () -> service.topUp(1, null, "null"));
        verifyNoInteractions(userRepository, transactionRepository);
    }

    // Rounding: values are rounded HALF_UP to two decimals
    @Test
    void topUp_rounding_halfUp_twoDecimals() {
        User user = makeUser(1, "u@e.com", "0.00");
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        User bank = makeUser(100, "bank@gmail.com", "0.00");
        bank.setBank(true);
        when(userRepository.findFirstByIsBankTrue()).thenReturn(Optional.of(bank));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // amount 10.005 -> gross 10.01 ; fee 0.05 ; net 9.96
        Transaction tr = service.topUp(1, new BigDecimal("10.005"), "round");

        assertEquals(new BigDecimal("10.01"), tr.getGrossAmount());
        assertEquals(new BigDecimal("0.05"), tr.getFeeAmount());
        assertEquals(new BigDecimal("9.96"), tr.getNetAmount());
        assertEquals(new BigDecimal("9.96"), user.getBalance());

        assertEquals(bank, tr.getSender());
        assertEquals(user, tr.getReceiver());
    }

    // P2P TRANSFER

    // Happy path: sender pays (gross + fee), receiver gets gross; persist a transaction accordingly
    @Test
    void transferP2P_success_debitsSender_creditsReceiver_andSavesTransaction() {
        User sender = makeUser(1, "s@e.com", "200.00");
        User receiver = makeUser(2, "r@e.com", "5.00");
        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2)).thenReturn(Optional.of(receiver));

        // ⬅️ NEW: stub Bank lookup (Bank is not a participant here)
        User bank = makeUser(100, "bank@gmail.com", "0.00");
        bank.setBank(true);
        when(userRepository.findFirstByIsBankTrue()).thenReturn(Optional.of(bank));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // amount=100.00; fee=0.50; sender debit=100.50; receiver +100.00
        Transaction tr = service.transferP2P(1, 2, new BigDecimal("100.00"), "Pay dinner");

        assertEquals(new BigDecimal("99.50"), sender.getBalance());       // 200 - 100.50
        assertEquals(new BigDecimal("105.00"), receiver.getBalance());    // 5 + 100
        assertEquals(TransactionType.P2P_TRANSFER, tr.getType());
        assertEquals(sender, tr.getSender());
        assertEquals(receiver, tr.getReceiver());
        assertEquals(new BigDecimal("100.00"), tr.getGrossAmount());
        assertEquals(new BigDecimal("0.50"), tr.getFeeAmount());
        assertEquals(new BigDecimal("100.00"), tr.getNetAmount());        // net equals gross for receiver
        assertEquals("Pay dinner", tr.getDescription());
    }

    // Sender must have enough balance to cover (gross + fee)
    @Test
    void transferP2P_insufficientBalance_throws() {
        User sender = makeUser(1, "s@e.com", "100.49"); // not enough for 100 + 0.50
        User receiver = makeUser(2, "r@e.com", "0.00");

        User bank = makeUser(100, "bank@gmail.com", "0.00");
        bank.setBank(true);
        when(userRepository.findFirstByIsBankTrue()).thenReturn(Optional.of(bank));

        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2)).thenReturn(Optional.of(receiver));

        assertThrows(IllegalStateException.class,
                () -> service.transferP2P(1, 2, new BigDecimal("100.00"), "Pay"));

        // balances unchanged
        assertEquals(new BigDecimal("100.49"), sender.getBalance());
        assertEquals(new BigDecimal("0.00"), receiver.getBalance());

        // no persistence when it fails
        verify(transactionRepository, never()).save(any());
    }

    // Validation: invalid users (null ids or same id)
    @Test
    void transferP2P_invalidUsers_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transferP2P(null, 2, new BigDecimal("10.00"), "x"));
        assertThrows(IllegalArgumentException.class,
                () -> service.transferP2P(1, null, new BigDecimal("10.00"), "x"));
        assertThrows(IllegalArgumentException.class,
                () -> service.transferP2P(1, 1, new BigDecimal("10.00"), "x"));
        verifyNoInteractions(userRepository, transactionRepository);
    }

    // WITHDRAW

    // Happy path: IBAN/BIC present, debit (gross + fee), persist a transaction with gross/fee/net
    @Test
    void withdraw_success_debitsBalance_andSavesTransaction() {
        // Arrange
        User user = makeUser(1, "u@e.com", "200.00");
        user.setIban("FR761234567890");
        user.setBic("AGRIFRPP");
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        User bank = makeUser(100, "bank@gmail.com", "0.00");
        bank.setBank(true);
        when(userRepository.findFirstByIsBankTrue()).thenReturn(Optional.of(bank));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // amount=50.00; fee=0.25; total debit=50.25
        Transaction tr = service.withdrawToBank(1, new BigDecimal("50.00"), "Cash out");

        assertEquals(new BigDecimal("149.75"), user.getBalance()); // 200 - 50.25
        assertEquals(TransactionType.WITHDRAWAL, tr.getType());
        assertEquals(user, tr.getSender());
        assertEquals(bank, tr.getReceiver());
        assertEquals(new BigDecimal("50.00"), tr.getGrossAmount());
        assertEquals(new BigDecimal("0.25"), tr.getFeeAmount());
        assertEquals(new BigDecimal("50.00"), tr.getNetAmount()); // net sent to the bank
        assertEquals("Cash out", tr.getDescription());
    }

    // IBAN/BIC are required for withdrawal
    @Test
    void withdraw_missingIbanBic_throws() {
        User user = makeUser(1, "u@e.com", "100.00"); // IBAN/BIC not set
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class,
                () -> service.withdrawToBank(1, new BigDecimal("10.00"), "Cash out"));

        verify(transactionRepository, never()).save(any());
        assertEquals(new BigDecimal("100.00"), user.getBalance());
    }

    // User must have enough balance to cover (gross + fee)
    @Test
    void withdraw_insufficientBalance_throws() {
        User user = makeUser(1, "u@e.com", "10.10");
        user.setIban("FRXXX");
        user.setBic("BICXXX");
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class,
                () -> service.withdrawToBank(1, new BigDecimal("10.10"), "Cash out"));

        // fee=0.05; total=10.15 > 10.10 → no change
        assertEquals(new BigDecimal("10.10"), user.getBalance());
        verify(transactionRepository, never()).save(any());
    }
}