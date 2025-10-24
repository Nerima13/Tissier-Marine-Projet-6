package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.TransferDTO;
import com.openclassrooms.payMyBuddy.model.TransactionType;
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
import org.springframework.web.bind.annotation.RequestParam;
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

    // Resolve the current user from Authentication (works for form login and OAuth2)
    private User me(Authentication auth) {
        String email = currentUserService.requireEmail(auth);
        return userService.getUserByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    /**
     * Single entry point for TOP_UP/DEPOSIT (wallet top-up), WITHDRAWAL (to bank) and P2P transfers.
     * - type=TOP_UP -> walletService.topUp(...)
     * - type=WITHDRAWAL -> walletService.withdrawToBank(...)
     * - type omitted/other -> P2P (requires receiverEmail)
     */
    @PostMapping("/transfer")
    public String handleTransfer(
            @RequestParam(name = "type", required = false) TransactionType type,
            @ModelAttribute TransferDTO dto,
            Authentication auth,
            RedirectAttributes ra) {
        String email = currentUserService.requireEmail(auth);
        String masked = maskEmail(email);
        log.info("POST /transfer - type={} by={} amount={} to={}", type, masked, dto.getAmount(), maskEmail(dto.getReceiverEmail()));

        try {
            User current = me(auth);

            if (type == TransactionType.TOP_UP) {
                // Wallet top-up
                walletService.topUp(current.getId(), dto.getAmount(), dto.getDescription());
                log.info("POST /transfer - TOP_UP success userId={} amount={}", current.getId(), dto.getAmount());
                ra.addFlashAttribute("success", "Deposit completed (0.5% fee applied).");

            } else if (type == TransactionType.WITHDRAWAL) {
                // Withdraw to bank
                walletService.withdrawToBank(current.getId(), dto.getAmount(), dto.getDescription());
                log.info("POST /transfer - WITHDRAWAL success userId={} amount={}", current.getId(), dto.getAmount());
                ra.addFlashAttribute("success", "Withdrawal initiated (0.5% fee applied).");

            } else {
                // Default: P2P transfer
                if (dto.getReceiverEmail() == null || dto.getReceiverEmail().isBlank()) {
                    throw new IllegalArgumentException("Receiver email is required for a P2P transfer.");
                }
                User receiver = userService.getUserByEmail(dto.getReceiverEmail())
                        .orElseThrow(() -> new IllegalArgumentException("Receiver not found"));

                walletService.transferP2P(current.getId(), receiver.getId(), dto.getAmount(), dto.getDescription());
                log.info("POST /transfer - P2P success fromId={} toId={} amount={}",
                        current.getId(), receiver.getId(), dto.getAmount());
                ra.addFlashAttribute("success", "Transfer sent (0.5% fee paid by the sender).");
            }

        } catch (IllegalArgumentException e) {
            log.warn("POST /transfer - business error type={} by={} reason={}", type, masked, e.getMessage());
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("POST /transfer - unexpected error type={} by={}", type, masked, e);
            ra.addFlashAttribute("error", "Unexpected error, please try again.");
        }

        return "redirect:/transfer"; // dashboard
    }
}