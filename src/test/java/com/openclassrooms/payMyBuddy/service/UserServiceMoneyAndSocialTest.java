package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.TransactionRepository;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceMoneyAndSocialTest {

    @Mock UserRepository userRepository;
    @Mock TransactionRepository transactionRepository;

    @InjectMocks UserService userService;

    // getBalance

    @Test
    public void getBalance_ok() {
        User u = new User(); u.setId(1); u.setBalance(new BigDecimal("15.50"));
        when(userRepository.findById(1)).thenReturn(Optional.of(u));

        BigDecimal b = userService.getBalance(1);

        assertEquals(new BigDecimal("15.50"), b);
        verify(userRepository).findById(1);
    }

    @Test
    public void getBalance_userNotFound_throws() {
        when(userRepository.findById(2)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.getBalance(2));
        assertEquals("User not found : id = 2", ex.getMessage());
    }

    // transfer

    @Test
    public void transfer_ok_updatesBalances_persistsTransaction() {
        User sender = new User(); sender.setId(1); sender.setEmail("a@example.com"); sender.setBalance(new BigDecimal("50.00"));
        User receiver = new User(); receiver.setId(2); receiver.setEmail("b@example.com"); receiver.setBalance(new BigDecimal("5.00"));

        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(receiver));
        when(transactionRepository.save(any(Transaction.class))).then(returnsFirstArg());

        Transaction tx = userService.transfer(1, "  b@example.com  ", new BigDecimal("10.00"), "Money");

        assertEquals(new BigDecimal("40.00"), sender.getBalance());
        assertEquals(new BigDecimal("15.00"), receiver.getBalance());
        assertEquals(new BigDecimal("10.00"), tx.getAmount());
        assertEquals("Money", tx.getDescription());
        assertEquals(sender, tx.getSender());
        assertEquals(receiver, tx.getReceiver());

        InOrder order = inOrder(userRepository, transactionRepository);
        order.verify(userRepository).save(sender);
        order.verify(userRepository).save(receiver);
        order.verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    public void transfer_roundsAmount_HALF_UP_andDefaultDescriptionWhenBlank() {
        User sender = new User(); sender.setId(1); sender.setEmail("a@example.com"); sender.setBalance(new BigDecimal("50.00"));
        User receiver = new User(); receiver.setId(2); receiver.setEmail("b@example.com"); receiver.setBalance(new BigDecimal("5.00"));

        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(receiver));
        when(transactionRepository.save(any(Transaction.class))).then(returnsFirstArg());

        Transaction tx = userService.transfer(1, "b@example.com", new BigDecimal("10.005"), "   "); // blank desc

        assertEquals(new BigDecimal("39.99"), sender.getBalance()); // 50 - 10.01
        assertEquals(new BigDecimal("15.01"), receiver.getBalance()); // 5 + 10.01
        assertEquals(new BigDecimal("10.01"), tx.getAmount());
        assertEquals("Transfer to b@example.com", tx.getDescription());
    }

    @Test
    public void transfer_amountValidation() {
        assertThrows(IllegalArgumentException.class, () -> userService.transfer(1, "b@example.com", null, null));
        assertThrows(IllegalArgumentException.class, () -> userService.transfer(1, "b@example.com", new BigDecimal("0"), null));
        assertThrows(IllegalArgumentException.class, () -> userService.transfer(1, "b@example.com", new BigDecimal("-1"), null));

        verifyNoInteractions(transactionRepository);
        verifyNoMoreInteractions(userRepository); // nothing fetched
    }

    @Test
    public void transfer_senderNotFound() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.transfer(99, "b@example.com", new BigDecimal("5.00"), null));

        verify(userRepository).findById(99);
        verify(userRepository, never()).findByEmail(anyString());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    public void transfer_receiverEmailNull_throws() {
        when(userRepository.findById(1)).thenReturn(Optional.of(new User()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.transfer(1, null, new BigDecimal("5.00"), null));
        assertEquals("Receiver email must not be null.", ex.getMessage());

        verify(userRepository).findById(1);
        verify(userRepository, never()).findByEmail(anyString());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    public void transfer_receiverNotFound() {
        User sender = new User(); sender.setId(1); sender.setBalance(new BigDecimal("100.00"));
        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.transfer(1, "b@example.com", new BigDecimal("5.00"), null));

        verify(userRepository).findById(1);
        verify(userRepository).findByEmail("b@example.com");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    public void transfer_self_throws() {
        User sender = new User(); sender.setId(1); sender.setEmail("a@example.com"); sender.setBalance(new BigDecimal("100.00"));
        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(sender));

        assertThrows(IllegalArgumentException.class, () -> userService.transfer(1, "a@example.com", new BigDecimal("5.00"), null));

        verify(transactionRepository, never()).save(any());
    }

    @Test
    public void transfer_insufficientBalance_throws() {
        User sender = new User(); sender.setId(1); sender.setEmail("a@example.com"); sender.setBalance(new BigDecimal("3.00"));
        User receiver = new User(); receiver.setId(2); receiver.setEmail("b@example.com"); receiver.setBalance(new BigDecimal("0.00"));

        when(userRepository.findById(1)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(receiver));

        assertThrows(IllegalArgumentException.class, () -> userService.transfer(1, "b@example.com", new BigDecimal("5.00"), "Money"));

        assertEquals(new BigDecimal("3.00"), sender.getBalance());
        assertEquals(new BigDecimal("0.00"), receiver.getBalance());
        verify(transactionRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    // addConnection

    @Test
    public void addConnection_success() {
        User me = new User();
        me.setId(1);
        me.setEmail("me@example.com");
        User friend = new User();
        friend.setId(2);
        friend.setEmail("friend@example.com");

        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(me));
        when(userRepository.findByEmail("friend@example.com")).thenReturn(Optional.of(friend));

        userService.addConnection("  ME@EXAMPLE.COM  ", "  FRIEND@Example.com ");

        verify(userRepository).findByEmail("me@example.com");
        verify(userRepository).findByEmail("friend@example.com");
        verify(userRepository).save(me);
    }

    @Test
    public void addConnection_nullEmails_throws() {
        assertThrows(IllegalArgumentException.class, () -> userService.addConnection(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> userService.addConnection("x", null));
        verifyNoInteractions(userRepository);
    }

    @Test
    public void addConnection_self_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.addConnection("me@example.com", " ME@EXAMPLE.COM "));
        assertEquals("You cannot add yourself.", ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    public void addConnection_userOrFriendNotFound_throws() {
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> userService.addConnection("me@example.com", "friend@example.com"));

        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(new User()));
        when(userRepository.findByEmail("friend@example.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> userService.addConnection("me@example.com", "friend@example.com"));
    }

    @Test
    public void removeConnection_success() {
        User me = new User();
        me.setId(1);
        me.setEmail("me@example.com");
        User friend = new User();
        friend.setId(2);
        friend.setEmail("friend@example.com");

        me.getConnections().add(friend);

        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(me));
        when(userRepository.findByEmail("friend@example.com")).thenReturn(Optional.of(friend));
        when(userRepository.save(any(User.class))).then(returnsFirstArg());

        userService.removeConnection("  ME@EXAMPLE.COM  ", "  FRIEND@example.com ");

        assertFalse(me.getConnections().contains(friend));
        verify(userRepository).findByEmail("me@example.com");
        verify(userRepository).findByEmail("friend@example.com");
        verify(userRepository).save(me);
        verify(userRepository, never()).save(friend);
    }

    @Test
    public void removeConnection_nullEmails_throws() {
        IllegalArgumentException ex1 =
                assertThrows(IllegalArgumentException.class, () -> userService.removeConnection(null, "x"));
        assertEquals("Emails must not be null.", ex1.getMessage());

        IllegalArgumentException ex2 =
                assertThrows(IllegalArgumentException.class, () -> userService.removeConnection("x", null));
        assertEquals("Emails must not be null.", ex2.getMessage());

        verifyNoInteractions(userRepository);
    }

    @Test
    public void removeConnection_self_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.removeConnection("  Me@Example.com  ", "ME@example.COM"));
        assertEquals("You cannot remove yourself.", ex.getMessage());

        verifyNoInteractions(userRepository);
    }

    @Test
    public void removeConnection_userNotFound_throws() {
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.removeConnection("me@example.com", "friend@example.com"));
        assertEquals("User not found: me@example.com", ex.getMessage());

        verify(userRepository).findByEmail("me@example.com");
        verify(userRepository, never()).findByEmail("friend@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    public void removeConnection_friendNotFound_throws() {
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(new User()));
        when(userRepository.findByEmail("friend@example.com")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.removeConnection("me@example.com", "friend@example.com"));
        assertEquals("Friend not found: friend@example.com", ex.getMessage());

        verify(userRepository).findByEmail("me@example.com");
        verify(userRepository).findByEmail("friend@example.com");
        verify(userRepository, never()).save(any());
    }
}