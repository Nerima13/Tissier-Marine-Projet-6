package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.TransactionRepository;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceAccountTest {

    @Mock UserRepository userRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserService userService;

    // Basic reads

    @Test
    public void getUsers_returnsAll() {
        User u1 = new User(); u1.setEmail("a@example.com");
        User u2 = new User(); u2.setEmail("b@example.com");
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        Iterable<User> users = userService.getUsers();

        verify(userRepository).findAll();
        assertIterableEquals(List.of(u1, u2), users);
    }

    @Test
    public void getUserById_ok() {
        User u = new User(); u.setId(2);
        when(userRepository.findById(2)).thenReturn(Optional.of(u));

        Optional<User> res = userService.getUserById(2);

        verify(userRepository).findById(2);
        assertTrue(res.isPresent());
        assertEquals(2, res.get().getId());
    }

    @Test
    public void getUserById_null_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.getUserById(null));
        assertEquals("Id must not be null.", ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    public void getUserByEmail_ok() {
        User u = new User(); u.setEmail("a@example.com");
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(u));

        Optional<User> res = userService.getUserByEmail("a@example.com");

        verify(userRepository).findByEmail("a@example.com");
        assertTrue(res.isPresent());
    }

    @Test
    public void getUserByEmail_null_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.getUserByEmail(null));
        assertEquals("Email must not be null.", ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    // registerUser

    @Test
    public void registerUser_ok_hashesPassword_setsBalanceScale_andNormalizesEmail() {
        User input = new User();
        input.setEmail("  JOHN.DOE@EXAMPLE.COM ");
        input.setPassword("pwd");
        input.setBalance(null);

        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pwd")).thenReturn("hash");
        when(userRepository.save(any(User.class))).then(returnsFirstArg());

        User saved = userService.registerUser(input);

        verify(userRepository).findByEmail("john.doe@example.com");
        verify(passwordEncoder).encode("pwd");
        verify(userRepository).save(any(User.class));

        assertEquals("john.doe@example.com", saved.getEmail());
        assertEquals("hash", saved.getPassword());
        assertEquals(new BigDecimal("0.00"), saved.getBalance());
    }

    @Test
    public void registerUser_existingEmail_throws() {
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(new User()));

        User input = new User();
        input.setEmail(" A@Example.com ");
        input.setPassword("pwd");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.registerUser(input));
        assertEquals("This email already exists.", ex.getMessage());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    public void registerUser_nullInputs_throw() {
        assertThrows(IllegalArgumentException.class, () -> userService.registerUser(null));

        User u1 = new User(); u1.setPassword("pwd");
        assertThrows(IllegalArgumentException.class, () -> userService.registerUser(u1));

        User u2 = new User(); u2.setEmail("a@example.com");
        assertThrows(IllegalArgumentException.class, () -> userService.registerUser(u2));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    public void registerUser_keepsProvidedBalanceButScales() {
        User input = new User();
        input.setEmail("a@example.com");
        input.setPassword("pwd");
        input.setBalance(new BigDecimal("12"));

        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pwd")).thenReturn("hash");
        when(userRepository.save(any(User.class))).then(returnsFirstArg());

        User saved = userService.registerUser(input);

        assertEquals(new BigDecimal("12.00"), saved.getBalance());
    }

    // getOrCreateOAuth2User

    @Test
    public void getOrCreateOAuth2User_existing_returnsIt() {
        User existing = new User(); existing.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));

        User res = userService.getOrCreateOAuth2User(" USER@EXAMPLE.COM ", "Name");

        assertSame(existing, res);
        verify(userRepository).findByEmail("user@example.com");
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(passwordEncoder, transactionRepository);
    }

    @Test
    public void getOrCreateOAuth2User_new_createsWithFallbackName_andEncodesPasswordViaRegister() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).then(returnsFirstArg());

        User created = userService.getOrCreateOAuth2User("  NEW@Example.com  ", "   ");

        assertEquals("new@example.com", created.getEmail());
        assertEquals("new@example.com", created.getUsername()); // fallback to email
        assertEquals("hash", created.getPassword());
        verify(userRepository, atLeastOnce()).findByEmail("new@example.com");
        verify(passwordEncoder).encode(anyString());
        verify(userRepository).save(any(User.class));
    }

    @Test
    public void getOrCreateOAuth2User_nullEmail_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.getOrCreateOAuth2User(null, "Name"));
        assertEquals("Email must not be null.", ex.getMessage());
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    // updateProfile

    @Test
    public void updateProfile_success_noEmailChange_updatesUsername_andPassword() {
        User me = new User();
        me.setId(1);
        me.setEmail("user@example.com");
        me.setUsername("Old");
        me.setPassword("hash_old");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("curr", "hash_old")).thenReturn(true);
        when(passwordEncoder.encode("newPwd")).thenReturn("hash_new");
        when(userRepository.save(any(User.class))).then(returnsFirstArg());

        User saved = userService.updateProfile(
                "user@example.com",
                "  New Name  ",
                " user@example.com ",
                "curr",
                "newPwd",
                "newPwd");

        assertEquals("New Name", saved.getUsername());
        assertEquals("user@example.com", saved.getEmail());
        assertEquals("hash_new", saved.getPassword());
        verify(userRepository).save(me);
    }

    @Test
    public void updateProfile_success_emailChanged_whenUnique() {
        User me = new User(); me.setId(1); me.setEmail("user@example.com"); me.setPassword("hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).then(returnsFirstArg());

        User saved = userService.updateProfile("user@example.com", null, " NEW@Example.com ", null, null, null);

        assertEquals("new@example.com", saved.getEmail());
        verify(userRepository).findByEmail("new@example.com");
    }

    @Test
    public void updateProfile_emailConflict_throws() {
        User me = new User(); me.setId(1); me.setEmail("user@example.com");
        User other = new User(); other.setId(2); other.setEmail("other@example.com");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(other));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.updateProfile("user@example.com", null, " OTHER@example.com ", null, null, null));

        assertEquals("This email already exists.", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    public void updateProfile_passwordRules() {
        User me = new User(); me.setId(1); me.setEmail("user@example.com"); me.setPassword("hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(me));

        // Wants change but missing current password
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                userService.updateProfile("user@example.com", null, null, null, "newPwd", "newPwd"));
        assertEquals("Please enter your current password.", ex1.getMessage());

        // Wrong current password
        when(passwordEncoder.matches("curr", "hash")).thenReturn(false);
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                userService.updateProfile("user@example.com", null, null, "curr", "newPwd", "newPwd"));
        assertEquals("Current password is incorrect.", ex2.getMessage());

        // Confirmation mismatch
        when(passwordEncoder.matches("curr", "hash")).thenReturn(true);
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class, () ->
                userService.updateProfile("user@example.com", null, null, "curr", "newPwd", "other"));
        assertEquals("Password confirmation does not match.", ex3.getMessage());

        // Too short
        IllegalArgumentException ex4 = assertThrows(IllegalArgumentException.class, () ->
                userService.updateProfile("user@example.com", null, null, "curr", "short", "short"));
        assertEquals("New password must be at least 8 characters.", ex4.getMessage());

        verify(userRepository, times(4)).findByEmail("user@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    public void updateProfile_nullCurrentEmail_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.updateProfile(null, null, null, null, null, null));
        assertEquals("Email must not be null.", ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    public void updateProfile_userNotFound_throws() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.updateProfile("missing@example.com", null, null, null, null, null));
        assertEquals("User not found: missing@example.com", ex.getMessage());
    }
}
