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

    // Mask email (ex: m***e@gmail.com)
    private String maskEmail(String email) {
        return (email == null) ? "unknown" : email.replaceAll("(^.).*(@.*$)", "$1***$2");
    }

    // Resolve the current user from Authentication (works for form login and OAuth2).
    private User me(Authentication auth) {
        String email = currentUserService.requireEmail(auth);
        return userService.getUserByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    // Deposit money to the wallet (0.5% fee)
    @PostMapping("/deposit")
    public String deposit(@ModelAttribute DepositDTO dto, Authentication auth, RedirectAttributes ra) {
        String email = currentUserService.requireEmail(auth);
        String masked = maskEmail(email);
        log.info("POST /deposit - start by={} amount={}", masked, dto.getAmount());

        try {
            User u = me(auth);
            walletService.topUp(u.getId(), dto.getAmount(), dto.getDescription());
            log.info("POST /deposit - success userId={} amount={}", u.getId(), dto.getAmount());
            ra.addFlashAttribute("success", "Deposit completed (0.5% fee applied).");

        } catch (IllegalArgumentException e) {
            log.warn("POST /deposit - business error by={} reason={}", masked, e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());

        } catch (Exception e) {
            log.error("POST /deposit - unexpected error by={}", masked, e);
            ra.addFlashAttribute("error", "Unexpected error, please try again.");
        }

        return "redirect:/transfer";
    }

    // Withdraw money to the bank (0.5% fee)
    @PostMapping("/withdraw")
    public String withdraw(@ModelAttribute WithdrawDTO dto, Authentication auth, RedirectAttributes ra) {
        String email = currentUserService.requireEmail(auth);
        String masked = maskEmail(email);
        log.info("POST /withdraw - start by={} amount={}", masked, dto.getAmount());

        try {
            User u = me(auth);
            walletService.withdrawToBank(u.getId(), dto.getAmount(), dto.getDescription());
            log.info("POST /withdraw - success userId={} amount={}", u.getId(), dto.getAmount());
            ra.addFlashAttribute("success", "Withdrawal initiated (0.5% fee applied).");

        } catch (IllegalArgumentException e) {
            log.warn("POST /withdraw - business error by={} reason={}", masked, e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());

        } catch (Exception e) {
            log.error("POST /withdraw - unexpected error by={}", masked, e);
            ra.addFlashAttribute("error", "Unexpected error, please try again.");
        }

        return "redirect:/transfer";
    }

    // P2P transfer (sender pays the 0.5% fee; receiver gets the full amount)
    @PostMapping("/transfer")
    public String transfer(@ModelAttribute TransferDTO dto, Authentication auth, RedirectAttributes ra) {
        String senderEmail = currentUserService.requireEmail(auth);
        String maskedSender = maskEmail(senderEmail);
        String maskedReceiver = maskEmail(dto.getReceiverEmail());
        log.info("POST /transfer - start from={} to={} amount={}", maskedSender, maskedReceiver, dto.getAmount());

        try {
            User sender = me(auth);
            User receiver = userService.getUserByEmail(dto.getReceiverEmail())
                    .orElseThrow(() -> new IllegalArgumentException("Receiver not found"));

            walletService.transferP2P(sender.getId(), receiver.getId(), dto.getAmount(), dto.getDescription());
            log.info("POST /transfer - success fromId={} toId={} amount={}", sender.getId(), receiver.getId(), dto.getAmount());
            ra.addFlashAttribute("success", "Transfer sent (0.5% fee paid by the sender).");

        } catch (IllegalArgumentException e) {
            log.warn("POST /transfer - business error from={} to={} reason={}", maskedSender, maskedReceiver, e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());

        } catch (Exception e) {
            log.error("POST /transfer - unexpected error from={} to={}", maskedSender, maskedReceiver, e);
            ra.addFlashAttribute("error", "Unexpected error, please try again.");
        }

        return "redirect:/transfer";
    }
}