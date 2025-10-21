package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.CurrentUserService;
import com.openclassrooms.payMyBuddy.service.TransactionService;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
class DashboardControllerIT {

    @Autowired MockMvc mvc;

    @MockBean UserService userService;
    @MockBean TransactionService transactionService;
    @MockBean CurrentUserService currentUserService;

    // GET /connections: happy path -> renders "connections" view with required attributes    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void get_connections_renders_view_and_fills_model() throws Exception {
        when(currentUserService.requireEmail(any())).thenReturn("user@example.com");

        User me = new User();
        me.setId(1);
        me.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        mvc.perform(get("/connections"))
                .andExpect(status().isOk())
                .andExpect(view().name("connections"))
                .andExpect(model().attributeExists("me"))
                .andExpect(model().attributeExists("friendForm"))
                .andExpect(model().attribute("active", "add"));

        verify(currentUserService).requireEmail(any());
        verify(userService).getUserByEmail("user@example.com");
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(transactionService);
    }

    // POST /connections: success -> redirects to /connections with success message    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void post_connections_success_redirects_and_calls_service() throws Exception {
        when(currentUserService.requireEmail(any())).thenReturn("user@example.com");

        mvc.perform(post("/connections")
                        .param("email", "friend@example.com")
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/connections"))
                .andExpect(flash().attribute("success", "Friend added !"));

        verify(currentUserService).requireEmail(any());
        verify(userService).addConnection("user@example.com", "friend@example.com");
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(transactionService);
    }

    // POST /connections: blank email -> error + redirect to /connections
    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void post_connections_blank_email_redirects_with_error() throws Exception {
        when(currentUserService.requireEmail(any())).thenReturn("user@example.com");

        mvc.perform(post("/connections")
                        .param("email", "   ")
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/connections"))
                .andExpect(flash().attribute("error", "Please enter a friend's email."));

        verify(currentUserService).requireEmail(any());
        verify(userService, never()).addConnection(anyString(), anyString());
        verifyNoInteractions(transactionService);
    }

    // POST /connections: trying to add yourself -> error + redirect to /connections    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void post_connections_adding_self_redirects_with_error() throws Exception {
        when(currentUserService.requireEmail(any())).thenReturn("user@example.com");

        mvc.perform(post("/connections")
                        .param("email", " USER@EXAMPLE.COM ")
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/connections"))
                .andExpect(flash().attribute("error", "You cannot add yourself."));

        verify(currentUserService).requireEmail(any());
        verify(userService, never()).addConnection(anyString(), anyString());
        verifyNoInteractions(transactionService);
    }

    // POST /connections: service throws exception -> error message + redirect to /connections    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void post_connections_service_throws_redirects_with_service_message() throws Exception {
        when(currentUserService.requireEmail(any())).thenReturn("user@example.com");
        doThrow(new IllegalArgumentException("User not found"))
                .when(userService).addConnection("user@example.com", "friend@example.com");

        mvc.perform(post("/connections")
                        .param("email", "friend@example.com")
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/connections"))
                .andExpect(flash().attribute("error", "User not found"));

        verify(currentUserService).requireEmail(any());
        verify(userService).addConnection("user@example.com", "friend@example.com");
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(transactionService);
    }
}