package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.DepositDTO;
import com.openclassrooms.payMyBuddy.dto.TransferDTO;
import com.openclassrooms.payMyBuddy.dto.WithdrawDTO;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.CurrentUserService;
import com.openclassrooms.payMyBuddy.service.UserService;
import com.openclassrooms.payMyBuddy.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserService userService;
    private final CurrentUserService currentUserService;

    // Resolve the current user from Authentication (works for form login and OAuth2).
    private User me(Authentication auth) {
        String email = currentUserService.requireEmail(auth);
        return userService.getUserByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    // Deposit money to the wallet (0.5% fee)
    @PostMapping("/deposit")
    public String deposit(@ModelAttribute DepositDTO dto, Authentication auth, RedirectAttributes ra) {
        try {
            User u = me(auth);
            walletService.topUp(
                    u.getId(),
                    dto.getAmount(),
                    dto.getDescription());
            ra.addFlashAttribute("success", "Deposit completed (0.5% fee applied).");
        } catch (Exception e) {
            log.warn("Deposit failed: {}", e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/transfer";
    }

    // Withdraw money to the bank (0.5% fee)
    @PostMapping("/withdraw")
    public String withdraw(@ModelAttribute WithdrawDTO dto, Authentication auth, RedirectAttributes ra) {
        try {
            User u = me(auth);
            walletService.withdrawToBank(
                    u.getId(),
                    dto.getAmount(),
                    dto.getDescription());
            ra.addFlashAttribute("success", "Withdrawal initiated (0.5% fee applied).");
        } catch (Exception e) {
            log.warn("Withdraw failed: {}", e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/transfer";
    }

    // P2P transfer (sender pays the 0.5% fee; receiver gets the full amount)
    @PostMapping("/transfer")
    public String transfer(@ModelAttribute TransferDTO dto, Authentication auth, RedirectAttributes ra) {
        try {
            User sender = me(auth);
            User receiver = userService.getUserByEmail(dto.getReceiverEmail())
                    .orElseThrow(() -> new IllegalArgumentException("Receiver not found"));

            walletService.transferP2P(
                    sender.getId(),
                    receiver.getId(),
                    dto.getAmount(),
                    dto.getDescription());
            ra.addFlashAttribute("success", "Transfer sent (0.5% fee paid by the sender).");
        } catch (Exception e) {
            log.warn("Transfer failed: {}", e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/transfer";
    }
}
