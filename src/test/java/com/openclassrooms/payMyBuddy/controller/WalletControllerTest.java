package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.TransferDTO;
import com.openclassrooms.payMyBuddy.model.TransactionType;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock WalletService walletService;
    @Mock UserService userService;
    @Mock CurrentUserService currentUserService;

    @InjectMocks WalletController controller;

    // TOP UP

    @Test
    void handleTransfer_topUp_success() {
        Authentication auth = new TestingAuthenticationToken("user@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        User me = new User(); me.setId(1); me.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        TransferDTO dto = new TransferDTO();
        dto.setAmount(new BigDecimal("100"));
        dto.setDescription("Deposit test");

        String view = controller.handleTransfer(TransactionType.TOP_UP, dto, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Deposit completed (0.5% fee applied).", ra.getFlashAttributes().get("success"));
        verify(walletService).topUp(eq(1), eq(new BigDecimal("100")), eq("Deposit test"));
        verifyNoMoreInteractions(walletService);
    }

    @Test
    void handleTransfer_topUp_businessError() {
        Authentication auth = new TestingAuthenticationToken("user@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        User me = new User(); me.setId(1); me.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        TransferDTO dto = new TransferDTO();
        dto.setAmount(new BigDecimal("50"));
        dto.setDescription("Fail deposit");

        doThrow(new IllegalArgumentException("Deposit failed"))
                .when(walletService).topUp(eq(1), eq(new BigDecimal("50")), eq("Fail deposit"));

        String view = controller.handleTransfer(TransactionType.TOP_UP, dto, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Deposit failed", ra.getFlashAttributes().get("error"));
    }

    @Test
    void handleTransfer_topUp_unexpectedError() {
        Authentication auth = new TestingAuthenticationToken("user@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        User me = new User(); me.setId(1); me.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        TransferDTO dto = new TransferDTO();
        dto.setAmount(new BigDecimal("10"));
        dto.setDescription("Boom");

        doThrow(new RuntimeException("boom"))
                .when(walletService).topUp(eq(1), eq(new BigDecimal("10")), eq("Boom"));

        String view = controller.handleTransfer(TransactionType.TOP_UP, dto, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Unexpected error, please try again.", ra.getFlashAttributes().get("error"));
    }

    // WITHDRAWAL

    @Test
    void handleTransfer_withdraw_success() {
        Authentication auth = new TestingAuthenticationToken("user@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        User me = new User(); me.setId(2); me.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        TransferDTO dto = new TransferDTO();
        dto.setAmount(new BigDecimal("20"));
        dto.setDescription("Withdraw test");

        String view = controller.handleTransfer(TransactionType.WITHDRAWAL, dto, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Withdrawal initiated (0.5% fee applied).", ra.getFlashAttributes().get("success"));
        verify(walletService).withdrawToBank(eq(2), eq(new BigDecimal("20")), eq("Withdraw test"));
        verifyNoMoreInteractions(walletService);
    }

    @Test
    void handleTransfer_withdraw_businessError() {
        Authentication auth = new TestingAuthenticationToken("user@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        User me = new User(); me.setId(2); me.setEmail("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        TransferDTO dto = new TransferDTO();
        dto.setAmount(new BigDecimal("200"));
        dto.setDescription("Fail withdraw");

        doThrow(new IllegalArgumentException("Withdraw failed"))
                .when(walletService).withdrawToBank(eq(2), eq(new BigDecimal("200")), eq("Fail withdraw"));

        String view = controller.handleTransfer(TransactionType.WITHDRAWAL, dto, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Withdraw failed", ra.getFlashAttributes().get("error"));
    }

    // P2P TRANSFER

    @Test
    void handleTransfer_p2p_success_whenTypeNull() {
        Authentication auth = new TestingAuthenticationToken("sender@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("sender@example.com");

        User sender = new User(); sender.setId(1); sender.setEmail("sender@example.com");
        User receiver = new User(); receiver.setId(2); receiver.setEmail("receiver@example.com");

        when(userService.getUserByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userService.getUserByEmail("receiver@example.com")).thenReturn(Optional.of(receiver));

        TransferDTO dto = new TransferDTO();
        dto.setReceiverEmail("receiver@example.com");
        dto.setAmount(new BigDecimal("10"));
        dto.setDescription("Transfer test");

        // type = null -> P2P
        String view = controller.handleTransfer(null, dto, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Transfer sent (0.5% fee paid by the sender).", ra.getFlashAttributes().get("success"));
        verify(walletService).transferP2P(eq(1), eq(2), eq(new BigDecimal("10")), eq("Transfer test"));
        verifyNoMoreInteractions(walletService);
    }

    @Test
    void handleTransfer_p2p_missingReceiverEmail() {
        Authentication auth = new TestingAuthenticationToken("sender@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("sender@example.com");
        User sender = new User(); sender.setId(1); sender.setEmail("sender@example.com");
        when(userService.getUserByEmail("sender@example.com")).thenReturn(Optional.of(sender));

        TransferDTO dto = new TransferDTO();
        dto.setReceiverEmail(null); // manquant
        dto.setAmount(new BigDecimal("5"));
        dto.setDescription("No receiver");

        String view = controller.handleTransfer(null, dto, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Receiver email is required for a P2P transfer.", ra.getFlashAttributes().get("error"));

        // The walletService should not be called
        verifyNoInteractions(walletService);
    }

    @Test
    void handleTransfer_p2p_receiverNotFound() {
        Authentication auth = new TestingAuthenticationToken("sender@example.com", "pwd");
        RedirectAttributes ra = new RedirectAttributesModelMap();

        when(currentUserService.requireEmail(auth)).thenReturn("sender@example.com");
        User sender = new User(); sender.setId(1); sender.setEmail("sender@example.com");
        when(userService.getUserByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(userService.getUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        TransferDTO dto = new TransferDTO();
        dto.setReceiverEmail("missing@example.com");
        dto.setAmount(new BigDecimal("5"));
        dto.setDescription("Receiver missing");

        String view = controller.handleTransfer(null, dto, auth, ra);

        assertEquals("redirect:/transfer", view);
        assertEquals("Receiver not found", ra.getFlashAttributes().get("error"));
        verify(userService).getUserByEmail("missing@example.com");
        verifyNoInteractions(walletService);
    }
}