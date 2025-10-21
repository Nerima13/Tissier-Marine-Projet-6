package com.openclassrooms.payMyBuddy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    private final CurrentUserService service = new CurrentUserService();

    // Null authentication -> throws IllegalStateException
    @Test
    void requireEmail_nullAuth_throws() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.requireEmail(null));
        assertEquals("Cannot find user email.", ex.getMessage());
    }

    // Form login: returns name lower-cased and trimmed
    @Test
    void requireEmail_formLogin_ok() {
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken("  JOHN.DOE@Example.com ", "pwd");
        String email = service.requireEmail(auth);
        assertEquals("john.doe@example.com", email);
    }

    // Form login: blank name -> throws
    @Test
    void requireEmail_formLogin_blank_throws() {
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken("   ", "pwd");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.requireEmail(auth));
        assertEquals("Cannot find user email.", ex.getMessage());
    }

    // OAuth2: email attribute present -> normalized email
    @Test
    void requireEmail_oauth2_withEmail_ok() {
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Map<String, Object> attrs = Map.of(
                "sub", "123",
                "email", "  Jane.DOE@Example.com ");
        DefaultOAuth2User principal =
                new DefaultOAuth2User(authorities, attrs, "sub");
        OAuth2AuthenticationToken token =
                new OAuth2AuthenticationToken(principal, authorities, "google");

        String email = service.requireEmail(token);
        assertEquals("jane.doe@example.com", email);
    }

    // OAuth2: missing/blank email attribute -> throws
    @Test
    void requireEmail_oauth2_missingEmail_throws() {
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Map<String, Object> attrs = Map.of("sub", "123"); // no "email"
        DefaultOAuth2User principal =
                new DefaultOAuth2User(authorities, attrs, "sub");
        OAuth2AuthenticationToken token =
                new OAuth2AuthenticationToken(principal, authorities, "google");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.requireEmail(token));
        assertEquals("Cannot find user email.", ex.getMessage());
    }
}