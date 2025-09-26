package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.TransactionService;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
class DashboardControllerIT {

    @Autowired MockMvc mvc;

    @MockBean UserService userService;
    @MockBean TransactionService transactionService; // dépendance du controller

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void transfer_success_redirects_to_transfer_and_calls_service() throws Exception {
        User me = new User(); me.setId(1); me.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(userService.transfer(1, "friend@example.com", new BigDecimal("10.00"), "Coffee"))
                .thenReturn(null); // le controller ne réutilise pas la valeur

        mvc.perform(post("/transfer")
                        .param("receiverEmail", "friend@example.com")
                        .param("amount", "10.00")
                        .param("description", "Coffee")
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/transfer"));

        verify(userService).getUserByEmail("user@example.com");
        verify(userService).transfer(1, "friend@example.com", new BigDecimal("10.00"), "Coffee");
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(transactionService);
    }

    @Test
    void add_connection_success_redirects_to_transfer_and_calls_service() throws Exception {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("email", "user@example.com");
        attrs.put("sub", "user@example.com"); // ✅ getName() vaudra ceci

        List<GrantedAuthority> roles = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        mvc.perform(post("/connections")
                        .param("email", "friend@example.com")
                        .with(csrf())
                        .with(oauth2Login()
                                .attributes(a -> a.putAll(attrs))
                                .authorities(roles)))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/transfer"));

        verify(userService).addConnection("user@example.com", "friend@example.com");
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(transactionService);
    }
}