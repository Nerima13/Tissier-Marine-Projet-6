package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.TransactionRepository;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // Basic read operations
    @Test
    public void getUsers_returnsUsersFromRepository() {
        User u1 = new User(); u1.setEmail("a@example.com");
        User u2 = new User(); u2.setEmail("b@example.com");
        List<User> expected = List.of(u1, u2);

        when(userRepository.findAll()).thenReturn(expected);

        Iterable<User> result = userService.getUsers();

        verify(userRepository).findAll();
        assertIterableEquals(expected, result);
    }

    @Test
    public void getUserById_returnsUserWhenFound() {
        User u = new User();
        u.setId(42);
        u.setEmail("a@example.com");
        when(userRepository.findById(42)).thenReturn(Optional.of(u));

        Optional<User> result = userService.getUserById(42);

        verify(userRepository).findById(42);
        assertTrue(result.isPresent());
        assertEquals(42, result.get().getId());
        assertEquals("a@example.com", result.get().getEmail());
    }

    @Test
    public void getUserByEmail_returnsUserWhenFound() {
        String email = "a@example.com";
        User u = new User();
        u.setId(7);
        u.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));

        Optional<User> result = userService.getUserByEmail(email);

        verify(userRepository).findByEmail(email);
        assertTrue(result.isPresent());
        assertEquals(7, result.get().getId());
        assertEquals(email, result.get().getEmail());
    }

    // Registration
    @Test
    public void registerUser_ok_setsHashedPassword_andInitBalanceWhenNull() {
        User input = new User();
        input.setEmail("a@example.com");
        input.setPassword("password");
        input.setBalance(null);

        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("hashed password");
        when(userRepository.save(any(User.class))).thenReturn(input);

        User saved = userService.registerUser(input);

        verify(userRepository).findByEmail("a@example.com");
        verify(passwordEncoder).encode("password");
        verify(userRepository).save(saved);

        assertEquals("hashed password", saved.getPassword());
        assertEquals(0, saved.getBalance().compareTo(new BigDecimal("0.00")));
        assertEquals(2, saved.getBalance().scale());
    }

    @Test
    public void registerUser_ok_keepsProvidedBalanceButScalesTo2() {
        String email = "a@example.com";
        User input = new User();
        input.setEmail(email);
        input.setPassword("password");
        input.setBalance(new BigDecimal("12"));

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("hashed password");
        when(userRepository.save(any(User.class))).thenReturn(input);

        User saved = userService.registerUser(input);

        verify(userRepository).findByEmail(email);
        verify(passwordEncoder).encode("password");
        verify(userRepository).save(any(User.class));

        assertEquals("hashed password", saved.getPassword());
        assertEquals(0, saved.getBalance().compareTo(new BigDecimal("12.00")));
        assertEquals(2, saved.getBalance().scale());
    }

    // Current balance
    @Test
    public void getBalance_returnsCurrentBalance() {
        User u = new User();
        u.setId(42);
        u.setBalance(new BigDecimal("15.50"));
        when(userRepository.findById(42)).thenReturn(Optional.of(u));

        BigDecimal balance = userService.getBalance(42);

        verify(userRepository).findById(42);
        assertEquals(0, balance.compareTo(new BigDecimal("15.50")));
    }

    // Simple transfer
    @Test
    void transfer_ok_updatesBalances_andReturnsSavedTransaction() {
        User sender = new User();
        sender.setId(1);
        sender.setEmail("a@example.com");
        sender.setBalance(new BigDecimal("50.00"));
        User receiver = new User();
        receiver.setId(2);
        receiver.setEmail("b@example.com");
        receiver.setBalance(new BigDecimal("5.00"));

        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(receiver));
        when(transactionRepository.save(any(Transaction.class))).then(returnsFirstArg());

        Transaction tx = userService.transfer(1, "b@example.com", new BigDecimal("10.00"), "Money");

        assertEquals(new BigDecimal("40.00"), sender.getBalance());   // 50 - 10
        assertEquals(new BigDecimal("15.00"), receiver.getBalance()); // 5 + 10
        assertEquals(new BigDecimal("10.00"), tx.getAmount());
        assertEquals("Money", tx.getDescription());

        assertEquals(sender, tx.getSender());
        assertEquals(receiver, tx.getReceiver());
        verify(userRepository).save(sender);
        verify(userRepository).save(receiver);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void transfer_amountMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> userService.transfer(1, "b@example.com", null, null));
        assertThrows(IllegalArgumentException.class, () -> userService.transfer(1, "b@example.com", new BigDecimal("0"), null));
        assertThrows(IllegalArgumentException.class, () -> userService.transfer(1, "b@example.com", new BigDecimal("-1"), null));

        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void transfer_senderNotFound() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> userService.transfer(99, "b@example.com", new BigDecimal("5.00"), null));

        verify(userRepository).findById(99);
        verify(userRepository, never()).findByEmail(anyString());
        verify(transactionRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void transfer_receiverNotFound() {
        User sender = new User();
        sender.setId(1);
        sender.setBalance(new BigDecimal("100.00"));

        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userService.transfer(1, "b@example.com", new BigDecimal("5.00"), null));

        verify(userRepository).findById(1);
        verify(userRepository).findByEmail("b@example.com");
        verify(transactionRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void transfer_selfTransfer() {
        User sender = new User();
        sender.setId(1);
        sender.setEmail("a@example.com");
        sender.setBalance(new BigDecimal("100.00"));

        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(sender));

        assertThrows(IllegalArgumentException.class,
                () -> userService.transfer(1, "a@example.com", new BigDecimal("5.00"), null));

        verify(userRepository).findById(1);
        verify(userRepository).findByEmail("a@example.com");
        verify(transactionRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void transfer_insufficientBalance() {
        User sender = new User();
        sender.setId(1);
        sender.setEmail("a@example.com");
        sender.setBalance(new BigDecimal("3.00"));
        User receiver = new User();
        receiver.setId(2);
        receiver.setEmail("b@example.com");
        receiver.setBalance(new BigDecimal("0.00"));

        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(receiver));

        assertThrows(IllegalArgumentException.class,
                () -> userService.transfer(1, "b@example.com", new BigDecimal("5.00"), "Money"));

        assertEquals(new BigDecimal("3.00"), sender.getBalance());
        assertEquals(new BigDecimal("0.00"), receiver.getBalance());
        verify(transactionRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void transfer_roundsAmount_HALF_UP() {
        User sender = new User();
        sender.setId(1);
        sender.setEmail("a@example.com");
        sender.setBalance(new BigDecimal("50.00"));
        User receiver = new User();
        receiver.setId(2);
        receiver.setEmail("b@example.com");
        receiver.setBalance(new BigDecimal("5.00"));

        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(receiver));
        when(transactionRepository.save(any(Transaction.class))).then(returnsFirstArg());

        // 10.005 -> 10.01 with HALF_UP
        Transaction tx = userService.transfer(1, "b@example.com", new BigDecimal("10.005"), "Money");

        assertEquals(new BigDecimal("39.99"), sender.getBalance()); // 50.00 - 10.01
        assertEquals(new BigDecimal("15.01"), receiver.getBalance()); // 5.00 + 10.01
        assertEquals(new BigDecimal("10.01"), tx.getAmount());
    }
}
