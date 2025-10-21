package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.CurrentUserService;
import com.openclassrooms.payMyBuddy.service.TransactionService;
import com.openclassrooms.payMyBuddy.service.UserService;
import com.openclassrooms.payMyBuddy.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WalletController.class)
class WalletControllerIT {

    @Autowired MockMvc mvc;

    @MockBean WalletService walletService;
    @MockBean UserService userService;
    @MockBean CurrentUserService currentUserService;

    // DEPOSIT TESTS

    // POST /deposit: happy path -> calls service, flashes success, redirects to /transfer
    @Test
    void deposit_success() throws Exception {
        when(currentUserService.requireEmail(any())).thenReturn("user@example.com");
        User me = new User(); me.setId(1); me.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        mvc.perform(post("/deposit")
                        .with(user("user@example.com").roles("USER"))
                        .with(csrf())
                        .param("amount", "100.00")
                        .param("description", "Top up"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/transfer"))
                .andExpect(flash().attribute("success", "Deposit completed (0.5% fee applied)."));

        verify(currentUserService).requireEmail(any());
        verify(userService).getUserByEmail("user@example.com");
        verify(walletService).topUp(eq(1), eq(new BigDecimal("100.00")), anyString(), eq("Top up"));
    }

    // POST /deposit: service throws -> flashes error and redirects to /transfer
    @Test
    void deposit_failure() throws Exception {
        when(currentUserService.requireEmail(any())).thenReturn("user@example.com");
        User me = new User(); me.setId(1); me.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));
        doThrow(new IllegalArgumentException("Deposit failed"))
                .when(walletService).topUp(eq(1), eq(new BigDecimal("50.00")), anyString(), eq("Fail"));

        mvc.perform(post("/deposit")
                        .with(user("user@example.com").roles("USER"))
                        .with(csrf())
                        .param("amount", "50.00")
                        .param("description", "Fail"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/transfer"))
                .andExpect(flash().attribute("error", "Deposit failed"));

        verify(walletService).topUp(eq(1), eq(new BigDecimal("50.00")), anyString(), eq("Fail"));
    }

    // POST /deposit: defensive check -> if CurrentUserService throws, controller catches and flashes the error
    @Test
    void deposit_current_user_resolution_fails() throws Exception {
        doThrow(new IllegalStateException("Not authenticated")).when(currentUserService).requireEmail(any());

        mvc.perform(post("/deposit")
                        .with(user("ignored@example.com").roles("USER"))
                        .with(csrf())
                        .param("amount", "10.00")
                        .param("description", "No auth"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/transfer"))
                .andExpect(flash().attribute("error", "Not authenticated"));

        verify(currentUserService).requireEmail(any());
        verifyNoInteractions(userService, walletService);
    }

    // WITHDRAW TESTS

    // POST /withdraw: happy path -> calls service, flashes success, redirects to /transfer
    @Test
    void withdraw_success() throws Exception {
        when(currentUserService.requireEmail(any())).thenReturn("user@example.com");
        User me = new User(); me.setId(2); me.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        mvc.perform(post("/withdraw")
                        .with(user("user@example.com").roles("USER"))
                        .with(csrf())
                        .param("amount", "20.00")
                        .param("description", "Cash out"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/transfer"))
                .andExpect(flash().attribute("success", "Withdrawal initiated (0.5% fee applied)."));

        verify(walletService).withdrawToBank(eq(2), eq(new BigDecimal("20.00")), anyString(), eq("Cash out"));
    }

    // POST /withdraw: service throws -> flashes error and redirects to /transfer
    @Test
    void withdraw_failure() throws Exception {
        when(currentUserService.requireEmail(any())).thenReturn("user@example.com");
        User me = new User(); me.setId(2); me.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));
        doThrow(new IllegalArgumentException("Withdrawal failed"))
                .when(walletService).withdrawToBank(eq(2), eq(new BigDecimal("200.00")), anyString(), eq("Too much"));

        mvc.perform(post("/withdraw")
                        .with(user("user@example.com").roles("USER"))
                        .with(csrf())
                        .param("amount", "200.00")
                        .param("description", "Too much"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/transfer"))
                .andExpect(flash().attribute("error", "Withdrawal failed"));

        verify(walletService).withdrawToBank(eq(2), eq(new BigDecimal("200.00")), anyString(), eq("Too much"));
    }

    // TRANSFER TESTS

    // POST /transfer: happy path -> resolves sender & receiver, calls service, flashes success, redirects
    @Test
    void transfer_success() throws Exception {
        when(currentUserService.requireEmail(any())).thenReturn("sender@example.com");
        User sender = new User(); sender.setId(10); sender.setEmail("sender@example.com");
        User receiver = new User(); receiver.setId(20); receiver.setEmail("friend@example.com");
        when(userService.getUserByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userService.getUserByEmail("friend@example.com")).thenReturn(Optional.of(receiver));

        mvc.perform(post("/transfer")
                        .with(user("sender@example.com").roles("USER"))
                        .with(csrf())
                        .param("receiverEmail", "friend@example.com")
                        .param("amount", "15.75")
                        .param("description", "Thanks"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/transfer"))
                .andExpect(flash().attribute("success", "Transfer sent (0.5% fee paid by the sender)."));

        verify(userService).getUserByEmail("sender@example.com");
        verify(userService).getUserByEmail("friend@example.com");
        verify(walletService).transferP2P(eq(10), eq(20), eq(new BigDecimal("15.75")), anyString(), eq("Thanks"));
    }

    // POST /transfer: receiver not found -> flashes "Receiver not found" and redirects
    @Test
    void transfer_receiver_not_found() throws Exception {
        when(currentUserService.requireEmail(any())).thenReturn("sender@example.com");
        User sender = new User(); sender.setId(10); sender.setEmail("sender@example.com");
        when(userService.getUserByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userService.getUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        mvc.perform(post("/transfer")
                        .with(user("sender@example.com").roles("USER"))
                        .with(csrf())
                        .param("receiverEmail", "missing@example.com")
                        .param("amount", "5.00")
                        .param("description", "Test"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/transfer"))
                .andExpect(flash().attribute("error", "Receiver not found"));

        verify(userService).getUserByEmail("missing@example.com");
        verifyNoInteractions(walletService);
    }
}