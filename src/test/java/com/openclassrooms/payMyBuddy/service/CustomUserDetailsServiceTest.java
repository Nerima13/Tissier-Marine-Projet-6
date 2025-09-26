package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    CustomUserDetailsService service;

    @Test
    void loadUserByUsername_ok_returnsSpringUserWithRoleUser() {
        User u = new User();
        u.setEmail("user@example.com");
        u.setPassword("hash");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));

        UserDetails details = service.loadUserByUsername("user@example.com");

        verify(userRepository).findByEmail("user@example.com");

        assertEquals("user@example.com", details.getUsername());
        assertEquals("hash", details.getPassword());

        assertEquals(1, details.getAuthorities().size());
        assertTrue(details.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));

        assertTrue(details.isAccountNonExpired());
        assertTrue(details.isAccountNonLocked());
        assertTrue(details.isCredentialsNonExpired());
        assertTrue(details.isEnabled());
    }

    @Test
    void loadUserByUsername_nullEmail_throwsUsernameNotFoundException() {
        UsernameNotFoundException ex = assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername(null));

        assertEquals("User not found : null", ex.getMessage());

        verifyNoInteractions(userRepository);
    }

    @Test
    void loadUserByUsername_emailIsTrimmedAndLowercased_thenUserFound() {
        User u = new User();
        u.setEmail("user@example.com");
        u.setPassword("hash");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));

        UserDetails details = service.loadUserByUsername("  USER@Example.COM  ");

        verify(userRepository).findByEmail("user@example.com");
        assertEquals("user@example.com", details.getUsername());
        assertEquals("hash", details.getPassword());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void loadUserByUsername_userNotFoundAfterNormalization_throws() {
        when(userRepository.findByEmail("normalized@example.com")).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername("  Normalized@Example.Com  "));

        assertEquals("User not found : normalized@example.com", ex.getMessage());
        verify(userRepository).findByEmail("normalized@example.com");
    }
}