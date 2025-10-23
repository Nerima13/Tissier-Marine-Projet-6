package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.DepositDTO;
import com.openclassrooms.payMyBuddy.dto.TransferDTO;
import com.openclassrooms.payMyBuddy.dto.WithdrawDTO;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.CurrentUserService;
import com.openclassrooms.payMyBuddy.service.UserService;
import com.openclassrooms.payMyBuddy.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock WalletService walletService;
    @Mock UserService userService;
    @Mock CurrentUserService currentUserService;

    @InjectMocks WalletController controller;

    // DEPOSIT

    // Deposit success → service called, flash success message, redirect to /transfer
    @Test
    void deposit_success() {
        Authentication auth = new TestingAuthenticationToken("user@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        User user = new User(); user.setId(1); user.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(user));

        DepositDTO form = new DepositDTO();
        form.setAmount(new BigDecimal("100"));
        form.setDescription("Deposit test");

        String view = controller.deposit(form, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Deposit completed (0.5% fee applied).", ra.getFlashAttributes().get("success"));
        verify(walletService).topUp(eq(1), eq(new BigDecimal("100")), eq("Deposit test"));
    }

    // Deposit failure → service throws, flash error message, redirect to /transfer
    @Test
    void deposit_failure() {
        Authentication auth = new TestingAuthenticationToken("user@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        User user = new User(); user.setId(1); user.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(user));

        DepositDTO form = new DepositDTO();
        form.setAmount(new BigDecimal("50"));
        form.setDescription("Fail test");

        doThrow(new IllegalArgumentException("Deposit failed"))
                .when(walletService).topUp(eq(1), eq(new BigDecimal("50")), eq("Fail test"));

        String view = controller.deposit(form, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Deposit failed", ra.getFlashAttributes().get("error"));
    }

    // WITHDRAW

    // Withdraw success → service called, flash success message, redirect to /transfer
    @Test
    void withdraw_success() {
        Authentication auth = new TestingAuthenticationToken("user@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        User user = new User(); user.setId(2); user.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(user));

        WithdrawDTO form = new WithdrawDTO();
        form.setAmount(new BigDecimal("20"));
        form.setDescription("Withdraw test");

        String view = controller.withdraw(form, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Withdrawal initiated (0.5% fee applied).", ra.getFlashAttributes().get("success"));
        verify(walletService).withdrawToBank(eq(2), eq(new BigDecimal("20")), eq("Withdraw test"));
    }

    // Withdraw failure → service throws, flash error message
    @Test
    void withdraw_failure() {
        Authentication auth = new TestingAuthenticationToken("user@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        User user = new User(); user.setId(2); user.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(user));

        WithdrawDTO form = new WithdrawDTO();
        form.setAmount(new BigDecimal("200"));
        form.setDescription("Fail withdraw");

        doThrow(new IllegalArgumentException("Withdraw failed"))
                .when(walletService).withdrawToBank(eq(2), eq(new BigDecimal("200")), eq("Fail withdraw"));

        String view = controller.withdraw(form, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Withdraw failed", ra.getFlashAttributes().get("error"));
    }

    // TRANSFER

    // Transfer success → service called, flash success message, redirect
    @Test
    void transfer_success() {
        Authentication auth = new TestingAuthenticationToken("sender@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("sender@example.com");
        User sender = new User(); sender.setId(1); sender.setEmail("sender@example.com");
        User receiver = new User(); receiver.setId(2); receiver.setEmail("receiver@example.com");

        when(userService.getUserByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userService.getUserByEmail("receiver@example.com")).thenReturn(Optional.of(receiver));

        TransferDTO form = new TransferDTO();
        form.setReceiverEmail("receiver@example.com");
        form.setAmount(new BigDecimal("10"));
        form.setDescription("Transfer test");

        String view = controller.transfer(form, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Transfer sent (0.5% fee paid by the sender).", ra.getFlashAttributes().get("success"));
        verify(walletService).transferP2P(eq(1), eq(2), eq(new BigDecimal("10")), eq("Transfer test"));
    }

    // Transfer failure → receiver not found → flash "Receiver not found"
    @Test
    void transfer_receiverNotFound() {
        Authentication auth = new TestingAuthenticationToken("sender@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("sender@example.com");
        User sender = new User(); sender.setId(1); sender.setEmail("sender@example.com");
        when(userService.getUserByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userService.getUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        TransferDTO form = new TransferDTO();
        form.setReceiverEmail("missing@example.com");
        form.setAmount(new BigDecimal("5"));
        form.setDescription("Receiver missing");

        String view = controller.transfer(form, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Receiver not found", ra.getFlashAttributes().get("error"));
        verify(userService).getUserByEmail("missing@example.com");
        verifyNoInteractions(walletService);
    }
}