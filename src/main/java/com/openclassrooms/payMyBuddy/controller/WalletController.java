package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.DepositForm;
import com.openclassrooms.payMyBuddy.dto.TransferForm;
import com.openclassrooms.payMyBuddy.dto.WithdrawForm;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.UserService;
import com.openclassrooms.payMyBuddy.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserService userService;

    // Get the currently authenticated user based on their email address.
    private User me(Authentication auth) {
        return userService.getUserByEmail(auth.getName()).orElseThrow();
    }

    /**
     * Handle money deposit into the user's wallet.
     * A 0.5% fee is applied — only the net amount (after fees) is credited.
     */
    @PostMapping("/deposit")
    public String deposit(@ModelAttribute DepositForm form, Authentication auth, RedirectAttributes ra) {
        try {
            User u = me(auth);
            walletService.topUp(
                    u.getId(),
                    form.getAmount(),
                    UUID.randomUUID().toString(),   // unique key to prevent duplicate transactions
                    form.getDescription());
            ra.addFlashAttribute("success", "Deposit completed (0.5% fee applied).");
        } catch (Exception e) {
            log.warn("Deposit failed: {}", e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/transfer";
    }

    /**
     * Handle withdrawal from the user's wallet to their bank account.
     * A 0.5% fee is applied — the user pays (amount + fee).
     */
    @PostMapping("/withdraw")
    public String withdraw(@ModelAttribute WithdrawForm form,
                           Authentication auth,
                           RedirectAttributes ra) {
        try {
            User u = me(auth);
            walletService.withdrawToBank(
                    u.getId(),
                    form.getAmount(),
                    UUID.randomUUID().toString(),
                    form.getDescription()
            );
            ra.addFlashAttribute("success", "Withdrawal initiated (0.5% fee applied).");
        } catch (Exception e) {
            log.warn("Withdraw failed: {}", e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/transfer";
    }

    /**
     * Handle peer-to-peer (P2P) money transfers between users.
     * The sender pays the 0.5% fee, and the receiver gets the full amount.
     */
    @PostMapping("/transfer")
    public String transfer(@ModelAttribute TransferForm form,
                           Authentication auth,
                           RedirectAttributes ra) {
        try {
            User sender = me(auth);
            User receiver = userService.getUserByEmail(form.getReceiverEmail())
                    .orElseThrow(() -> new IllegalArgumentException("Receiver not found"));

            walletService.transferP2P(
                    sender.getId(),
                    receiver.getId(),
                    form.getAmount(),
                    UUID.randomUUID().toString(),
                    form.getDescription()
            );
            ra.addFlashAttribute("success", "Transfer sent (0.5% fee paid by the sender).");
        } catch (Exception e) {
            log.warn("Transfer failed: {}", e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/transfer";
    }
}