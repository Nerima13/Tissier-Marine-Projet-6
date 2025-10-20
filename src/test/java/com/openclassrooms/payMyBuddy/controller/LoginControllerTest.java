package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.ui.Model;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LoginControllerTest {

    @Mock
    UserService userService;

    @InjectMocks
    LoginController controller;

    // 1) GET /login => returns "login" when NOT authenticated
    @Test
    public void login_returnsLoginView_whenNotAuthenticated() {
        String view = controller.login(null); // no auth
        assertEquals("login", view);
        verifyNoInteractions(userService);
    }

    // 1bis) GET /login => redirects to /transfer when ALREADY authenticated
    @Test
    public void login_redirectsToTransfer_whenAuthenticated() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "pass");
        auth.setAuthenticated(true); // not an AnonymousAuthenticationToken

        String view = controller.login(auth);

        assertEquals("redirect:/transfer", view);
        verifyNoInteractions(userService);
    }

    // 2) GET / with null auth => redirects to /login
    @Test
    public void home_whenAuthIsNull_redirectsToLogin() {
        String view = controller.home(null);
        assertEquals("redirect:/login", view);
        verifyNoInteractions(userService);
    }

    // 3) GET / with NON-OAuth2 auth => redirects to /transfer (and does not call the service)
    @Test
    public void home_whenNotOAuth2_redirectsToTransfer() {
        TestingAuthenticationToken nonOAuth2 = new TestingAuthenticationToken("u", "p");
        String view = controller.home(nonOAuth2);
        assertEquals("redirect:/transfer", view);
        verifyNoInteractions(userService);
    }

    // 4) GET / with OAuth2 BUT without email => redirects to /login (no user creation)
    @Test
    public void home_oauth2_withoutEmail_redirectsToLogin() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "123");              // required key for DefaultOAuth2User
        attrs.put("name", "John Doe");       // but NO email

        DefaultOAuth2User principal = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, "id");

        OAuth2AuthenticationToken token =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");

        String view = controller.home(token);

        assertEquals("redirect:/login", view);
        verifyNoInteractions(userService);
    }

    // 5) GET / with OAuth2 and email present => calls the service then redirects to /transfer
    @Test
    public void home_oauth2_withEmail_callsService_andRedirectsToTransfer() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "123");                                 // required key
        attrs.put("email", "  JOHN.DOE@EXAMPLE.COM ");          // will be trimmed + lowercased
        attrs.put("name", "John Doe");                          // direct name

        DefaultOAuth2User principal = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, "id");

        OAuth2AuthenticationToken token =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");

        String view = controller.home(token);

        assertEquals("redirect:/transfer", view);
        verify(userService).getOrCreateOAuth2User("john.doe@example.com", "John Doe");
        verifyNoMoreInteractions(userService);
    }
}
