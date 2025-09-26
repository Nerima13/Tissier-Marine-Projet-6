package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.service.TransactionService;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginController.class)
class LoginControllerIT {

    @Autowired MockMvc mvc;

    @MockBean UserService userService;

    @MockBean TransactionService transactionService;

    @Test
    void home_anonymous_redirects_to_login() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isFound())              // 302 exact
                .andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(userService);
    }

    @Test
    void home_oauth2User_createsOrGetsUser_and_redirects_transfer() throws Exception {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "123");
        attrs.put("email", "John.Doe@example.com");
        attrs.put("given_name", "John");
        attrs.put("family_name", "Doe");

        List<GrantedAuthority> roles = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        mvc.perform(get("/")
                        .with(oauth2Login()
                                .attributes(a -> a.putAll(attrs))
                                .authorities(roles)))
                .andExpect(status().isFound())              // 302 exact
                .andExpect(redirectedUrl("/transfer"));

        verify(userService).getOrCreateOAuth2User("john.doe@example.com", "John Doe");
    }
}
