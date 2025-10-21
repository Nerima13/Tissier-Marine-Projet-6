package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.security.SpringSecurityConfig;
import com.openclassrooms.payMyBuddy.service.TransactionService;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginController.class)
@AutoConfigureMockMvc
@Import(SpringSecurityConfig.class)
public class LoginControllerIT {

    @Autowired MockMvc mvc;

    @MockBean UserService userService;

    @MockBean TransactionService transactionService;

    // GET /login when NOT authenticated -> returns "login" view
    @Test
    void login_anonymous_renders_login_view() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
        verifyNoInteractions(userService, transactionService);
    }

    // GET /login when already authenticated -> redirects to /transfer
    @Test
    void login_authenticated_redirects_to_transfer() throws Exception {
        mvc.perform(get("/login").with(user("user@example.com").roles("USER")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/transfer"));
        verifyNoInteractions(userService, transactionService);
    }

    // GET / (home) anonymous -> redirects to /login
    @Test
    void home_anonymous_redirects_to_login() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(userService, transactionService);
    }

    // GET / (home) OAuth2 with email & name -> creates/gets user then redirects to /transfer
    @Test
    void home_oauth2User_createsOrGetsUser_and_redirects_transfer() throws Exception {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "123");                    // principal name attribute
        attrs.put("email", "John.Doe@example.com");
        attrs.put("name", "John Doe");             // neutre (pas spécifique à Google)

        List<GrantedAuthority> roles = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        mvc.perform(get("/").with(oauth2Login().attributes(a -> a.putAll(attrs)).authorities(roles)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/transfer"));

        verify(userService).getOrCreateOAuth2User("john.doe@example.com", "John Doe");
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(transactionService);
    }

    // GET / (home) OAuth2 but WITHOUT email -> redirects to /login and does NOT call service
    @Test
    void home_oauth2_without_email_redirects_to_login() throws Exception {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "123");
        attrs.put("name", "Someone"); // no email provided

        mvc.perform(get("/").with(oauth2Login().attributes(a -> a.putAll(attrs))))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(userService, transactionService);
    }
}
