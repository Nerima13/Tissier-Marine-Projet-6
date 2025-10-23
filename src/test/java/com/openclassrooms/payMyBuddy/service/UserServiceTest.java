package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock WalletService walletService;

    @InjectMocks UserService service;

    private User buildUser(Integer id, String email, String username, String encodedPassword, String balance) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setUsername(username);
        u.setPassword(encodedPassword);
        u.setBalance(new BigDecimal(balance));
        u.setConnections(new HashSet<>());
        u.setBank(false);
        return u;
    }

    private User buildBankUser(Integer id, String email) {
        User u = buildUser(id, email, "Bank", "enc", "0");
        u.setBank(true);
        return u;
    }

    @BeforeEach
    void resetMocks() {
        reset(userRepository, passwordEncoder, walletService);
    }

    // BASIC READ METHODS

    // Null id should throw
    @Test
    void getUserById_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.getUserById(null));
        verifyNoInteractions(userRepository);
    }

    // Null email should throw
    @Test
    void getUserByEmail_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.getUserByEmail(null));
        verifyNoInteractions(userRepository);
    }

    // REGISTERING

    // Success encodes password, normalizes email, sets defaults, and saves
    @Test
    void registerUser_success_encodesPassword_andSaves() {
        User input = new User();
        input.setEmail("  JOHN.DOE@Example.COM ");
        input.setPassword("plain");
        input.setUsername(""); // will fallback to email

        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plain")).thenReturn("ENC(plain)");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.registerUser(input);

        assertEquals("john.doe@example.com", saved.getEmail());
        assertEquals("ENC(plain)", saved.getPassword());
        assertEquals("john.doe@example.com", saved.getUsername()); // default fallback
        assertEquals(new BigDecimal("0"), saved.getBalance());
        verify(userRepository).findByEmail("john.doe@example.com");
        verify(passwordEncoder).encode("plain");
        verify(userRepository).save(saved);
    }

    // Email already exists should throw
    @Test
    void registerUser_duplicateEmail_throws() {
        User input = new User();
        input.setEmail("user@example.com");
        input.setPassword("x");

        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(buildUser(1, "user@example.com", "User", "enc", "0")));

        assertThrows(IllegalArgumentException.class, () -> service.registerUser(input));
        verify(userRepository).findByEmail("user@example.com");
        verify(userRepository, never()).save(any());
    }

    // Null user or missing email/password should throw
    @Test
    void registerUser_invalidInput_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.registerUser(null));

        User noEmail = new User(); noEmail.setPassword("p");
        assertThrows(IllegalArgumentException.class, () -> service.registerUser(noEmail));

        User noPwd = new User(); noPwd.setEmail("a@b.c");
        assertThrows(IllegalArgumentException.class, () -> service.registerUser(noPwd));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    // OAUTH2: GET OR CREATE USER

    // Returns existing user if found
    @Test
    void getOrCreateOAuth2User_existing_returnsExisting() {
        User existing = buildUser(1, "u@e.com", "U", "enc", "0");
        when(userRepository.findByEmail("u@e.com")).thenReturn(Optional.of(existing));

        User res = service.getOrCreateOAuth2User("  U@E.COM ", "Name Ignored");

        assertSame(existing, res);
        verify(userRepository).findByEmail("u@e.com");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    // Creates new via registerUser with default username if name empty
    @Test
    void getOrCreateOAuth2User_missing_createsNew_withFallbackUsername() {
        when(userRepository.findByEmail("new@e.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("ENC(random)");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(2);
            return u;
        });

        User res = service.getOrCreateOAuth2User(" new@E.com ", "  "); // blank name → fallback to email

        assertEquals(2, res.getId());
        assertEquals("new@e.com", res.getEmail());
        assertEquals("new@e.com", res.getUsername()); // fallback
        assertNotNull(res.getPassword()); // random then encoded by registerUser
        verify(userRepository, atLeast(1)).findByEmail("new@e.com");
        verify(userRepository).save(any(User.class));
    }

    // Null email should throw
    @Test
    void getOrCreateOAuth2User_nullEmail_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.getOrCreateOAuth2User(null, "n"));
        verifyNoInteractions(userRepository);
    }

    // BALANCE

    // Returns the current balance, errors if user absent
    @Test
    void getBalance_success_readsFromRepository() {
        User u = buildUser(1, "u@e.com", "U", "enc", "123.45");
        when(userRepository.findById(1)).thenReturn(Optional.of(u));

        BigDecimal b = service.getBalance(1);

        assertEquals(new BigDecimal("123.45"), b);
        verify(userRepository).findById(1);
    }

    // Returns the current balance, errors if user absent
    @Test
    void getBalance_userMissing_throws() {
        when(userRepository.findById(1)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getBalance(1));
    }

    // DEPOSIT

    // Delegates to walletService.topUp and returns refreshed balance
    @Test
    void deposit_delegatesToWalletService_andReturnsUpdatedBalance() {
        User reloaded = buildUser(1, "u@e.com", "U", "enc", "50.00");
        when(userRepository.findById(1)).thenReturn(Optional.of(reloaded));

        BigDecimal result = service.deposit(1, new BigDecimal("25.00"));

        assertEquals(new BigDecimal("50.00"), result);
        verify(walletService).topUp(eq(1), eq(new BigDecimal("25.00")), eq("Deposit"));
        InOrder order = inOrder(walletService, userRepository);
        order.verify(walletService).topUp(eq(1), eq(new BigDecimal("25.00")), eq("Deposit"));
        order.verify(userRepository).findById(1);
    }

    // Null userId should throw
    @Test
    void deposit_nullUserId_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.deposit(null, new BigDecimal("10.00")));
        verifyNoInteractions(walletService, userRepository);
    }

    // TRANSFER

    // Resolves receiver by email and delegates to walletService.transferP2P
    @Test
    void transfer_resolvesReceiver_andDelegates() {
        User sender = buildUser(1, "me@example.com", "Me", "enc", "0");
        when(userRepository.findById(1)).thenReturn(Optional.of(sender));

        User receiver = buildUser(2, "friend@example.com", "Friend", "enc", "0");
        when(userRepository.findByEmail("friend@example.com")).thenReturn(Optional.of(receiver));

        Transaction fake = new Transaction();
        when(walletService.transferP2P(eq(1), eq(2), eq(new BigDecimal("10.00")), eq("Coffee")))
                .thenReturn(fake);

        Transaction res = service.transfer(1, "  FRIEND@example.com  ", new BigDecimal("10.00"), "  Coffee  ");

        assertSame(fake, res);
        verify(userRepository).findById(1);
        verify(userRepository).findByEmail("friend@example.com");
        verify(walletService).transferP2P(eq(1), eq(2), eq(new BigDecimal("10.00")), eq("Coffee"));
    }

    // Null description → uses default "Transfer to <email>"
    @Test
    void transfer_nullDescription_usesDefaultMessage() {
        User sender = buildUser(1, "me@example.com", "Me", "enc", "0");
        when(userRepository.findById(1)).thenReturn(Optional.of(sender));

        User receiver = buildUser(2, "friend@example.com", "Friend", "enc", "0");
        when(userRepository.findByEmail("friend@example.com")).thenReturn(Optional.of(receiver));

        when(walletService.transferP2P(eq(1), eq(2), eq(new BigDecimal("5.00")),
                eq("Transfer to friend@example.com"))).thenReturn(new Transaction());

        service.transfer(1, "friend@example.com", new BigDecimal("5.00"), null);

        verify(userRepository).findById(1);
        verify(userRepository).findByEmail("friend@example.com");
        verify(walletService).transferP2P(eq(1), eq(2), eq(new BigDecimal("5.00")),
                eq("Transfer to friend@example.com"));
    }

    // Missing receiver or sender should throw
    @Test
    void transfer_missingArguments_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transfer(null, "a@b.c", new BigDecimal("1.00"), "x"));
        assertThrows(IllegalArgumentException.class,
                () -> service.transfer(1, null, new BigDecimal("1.00"), "x"));
        verifyNoInteractions(userRepository, walletService);
    }

    // Receiver not found should throw
    @Test
    void transfer_receiverNotFound_throws() {
        User sender = buildUser(1, "me@example.com", "Me", "enc", "0");
        when(userRepository.findById(1)).thenReturn(Optional.of(sender));

        when(userRepository.findByEmail("missing@e.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.transfer(1, " missing@E.com ", new BigDecimal("1.00"), "x"));

        verify(userRepository).findById(1);
        verify(userRepository).findByEmail("missing@e.com");
        verifyNoInteractions(walletService);
    }

    @Test
    void transfer_senderIsBank_throws() {
        User bank = buildBankUser(100, "bank@gmail.com");
        when(userRepository.findById(100)).thenReturn(Optional.of(bank));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(100, "friend@example.com", new BigDecimal("10.00"), "x"));
        assertTrue(ex.getMessage().toLowerCase().contains("bank user"));

        verify(userRepository).findById(100);
        verify(userRepository, never()).findByEmail(anyString());
        verifyNoInteractions(walletService);
    }

    @Test
    void transfer_receiverIsBank_throws() {
        User sender = buildUser(1, "me@example.com", "Me", "enc", "0");
        when(userRepository.findById(1)).thenReturn(Optional.of(sender));

        User bank = buildBankUser(100, "bank@gmail.com");
        when(userRepository.findByEmail("bank@gmail.com")).thenReturn(Optional.of(bank));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(1, "bank@gmail.com", new BigDecimal("10.00"), "x"));
        assertTrue(ex.getMessage().toLowerCase().contains("bank user"));

        verify(userRepository).findById(1);
        verify(userRepository).findByEmail("bank@gmail.com");
        verifyNoInteractions(walletService);
    }

    // CONNECTIONS

    // Success adds friend and saves owner
    @Test
    void addConnection_success_addsAndSaves() {
        User me = buildUser(1, "me@e.com", "Me", "enc", "0");
        User friend = buildUser(2, "friend@e.com", "Friend", "enc", "0");
        when(userRepository.findByEmail("me@e.com")).thenReturn(Optional.of(me));
        when(userRepository.findByEmail("friend@e.com")).thenReturn(Optional.of(friend));

        service.addConnection("  ME@e.com ", " FRIEND@e.com ");

        assertTrue(me.getConnections().contains(friend));
        verify(userRepository, times(2)).findByEmail(anyString());
        verify(userRepository).save(me);
    }

    // Adding yourself should throw
    @Test
    void addConnection_self_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addConnection("me@e.com", "  ME@E.com  "));
        verifyNoInteractions(userRepository);
    }

    // Friend already in connections should throw
    @Test
    void addConnection_duplicate_throws() {
        User me = buildUser(1, "me@e.com", "Me", "enc", "0");
        User friend = buildUser(2, "friend@e.com", "Friend", "enc", "0");
        me.getConnections().add(friend);

        when(userRepository.findByEmail("me@e.com")).thenReturn(Optional.of(me));
        when(userRepository.findByEmail("friend@e.com")).thenReturn(Optional.of(friend));

        assertThrows(IllegalArgumentException.class,
                () -> service.addConnection("me@e.com", "friend@e.com"));
        verify(userRepository, times(2)).findByEmail(anyString());
        verify(userRepository, never()).save(any());
    }

    // Success removes friend and saves owner
    @Test
    void removeConnection_success_removesAndSaves() {
        User me = buildUser(1, "me@e.com", "Me", "enc", "0");
        User friend = buildUser(2, "friend@e.com", "Friend", "enc", "0");
        me.getConnections().add(friend);

        when(userRepository.findByEmail("me@e.com")).thenReturn(Optional.of(me));
        when(userRepository.findByEmail("friend@e.com")).thenReturn(Optional.of(friend));

        service.removeConnection(" ME@e.com ", " FRIEND@e.com ");

        assertFalse(me.getConnections().contains(friend));
        verify(userRepository, times(2)).findByEmail(anyString());
        verify(userRepository).save(me);
    }

    // Removing yourself should throw
    @Test
    void removeConnection_self_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.removeConnection("me@e.com", " ME@E.com "));
        verifyNoInteractions(userRepository);
    }

    // PROFILE UPDATE

    // Change username and email when available, then save
    @Test
    void updateProfile_changeUsernameAndEmail_saves() {
        User me = buildUser(1, "old@e.com", "Old", "oldPwd", "0");
        when(userRepository.findByEmail("old@e.com")).thenReturn(Optional.of(me));
        when(userRepository.findByEmail("new@e.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.updateProfile("old@e.com", "  New Name  ", "  new@E.com ", null, null, null);

        assertEquals("New Name", saved.getUsername());
        assertEquals("new@e.com", saved.getEmail());
        assertEquals("oldPwd", saved.getPassword()); // unchanged
        verify(userRepository).save(me);
    }

    // Changing email to one already used by another user should throw
    @Test
    void updateProfile_emailAlreadyExists_throws() {
        User me = buildUser(1, "me@e.com", "Me", "oldPwd", "0");
        User other = buildUser(2, "other@e.com", "Other", "enc", "0");
        when(userRepository.findByEmail("me@e.com")).thenReturn(Optional.of(me));
        when(userRepository.findByEmail("other@e.com")).thenReturn(Optional.of(other));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateProfile("me@e.com", null, " other@E.com ", null, null, null));
        verify(userRepository, never()).save(any());
    }

    // Change password with correct current, matching confirmation, and length >= 8
    @Test
    void updateProfile_changePassword_success() {
        User me = buildUser(1, "me@e.com", "Me", "oldPwd", "0");
        when(userRepository.findByEmail("me@e.com")).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("current", "oldPwd")).thenReturn(true);
        when(passwordEncoder.encode("new-strong")).thenReturn("ENC(new-strong)");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.updateProfile("me@e.com", null, null,
                "current", "new-strong", "new-strong");

        assertEquals("ENC(new-strong)", saved.getPassword());
        verify(userRepository).save(me);
    }

    // Missing current password when requesting a change should throw
    @Test
    void updateProfile_missingCurrentPassword_throws() {
        User me = buildUser(1, "me@e.com", "Me", "oldPwd", "0");
        when(userRepository.findByEmail("me@e.com")).thenReturn(Optional.of(me));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateProfile("me@e.com", null, null,
                        null, "new-strong", "new-strong"));
        verify(userRepository, never()).save(any());
    }

    // Wrong current password should throw
    @Test
    void updateProfile_wrongCurrentPassword_throws() {
        User me = buildUser(1, "me@e.com", "Me", "oldPwd", "0");
        when(userRepository.findByEmail("me@e.com")).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("bad", "oldPwd")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateProfile("me@e.com", null, null,
                        "bad", "new-strong", "new-strong"));
        verify(userRepository, never()).save(any());
    }

    // Confirmation mismatch should throw
    @Test
    void updateProfile_passwordConfirmationMismatch_throws() {
        User me = buildUser(1, "me@e.com", "Me", "oldPwd", "0");
        when(userRepository.findByEmail("me@e.com")).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("current", "oldPwd")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateProfile("me@e.com", null, null,
                        "current", "new-strong", "DIFFERENT"));
        verify(userRepository, never()).save(any());
    }

    // New password too short (< 8) should throw
    @Test
    void updateProfile_passwordTooShort_throws() {
        User me = buildUser(1, "me@e.com", "Me", "oldPwd", "0");
        when(userRepository.findByEmail("me@e.com")).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("current", "oldPwd")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateProfile("me@e.com", null, null,
                        "current", "short", "short"));
        verify(userRepository, never()).save(any());
    }
}
