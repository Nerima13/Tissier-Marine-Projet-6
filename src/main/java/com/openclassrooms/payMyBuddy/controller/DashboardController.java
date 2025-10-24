package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.FriendDTO;
import com.openclassrooms.payMyBuddy.dto.TransferDTO;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.CurrentUserService;
import com.openclassrooms.payMyBuddy.service.TransactionService;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final UserService userService;
    private final TransactionService transactionService;
    private final CurrentUserService currentUserService;

    public DashboardController(UserService userService, TransactionService transactionService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.transactionService = transactionService;
        this.currentUserService = currentUserService;
    }

    // Dashboard page (balance, friends, history)
    @GetMapping("/transfer")
    public String showDashboard(Authentication authentication, Model model) {
        String email = currentUserService.requireEmail(authentication);
        String maskedEmail = (email == null) ? "unknown" : email.replaceAll("(^.).*(@.*$)", "$1***$2");
        log.info("GET /transfer - loading dashboard for email={}", maskedEmail);

        try {
            User me = userService.getUserByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + maskedEmail));

            model.addAttribute("me", me);
            model.addAttribute("friends", me.getConnections());
            model.addAttribute("feed", transactionService.getFeedForUser(me.getId()));
            model.addAttribute("friendForm", new FriendDTO());
            model.addAttribute("transferForm", new TransferDTO());

            log.info("GET /transfer - dashboard loaded userId={} friends={} feedItems={}",
                    me.getId(),
                    me.getConnections() == null ? 0 : me.getConnections().size(),
                    (transactionService.getFeedForUser(me.getId()) == null ? 0 : transactionService.getFeedForUser(me.getId()).size()));
            return "dashboard";

        } catch (IllegalArgumentException ex) {
            log.error("GET /transfer - user not found email={}", maskedEmail, ex);
            throw ex;

        } catch (Exception ex) {
            log.error("GET /transfer - unexpected error email={}", maskedEmail, ex);
            throw ex;
        }
    }

    // Add a friend by email
    @PostMapping("/connections")
    public String addFriend(@ModelAttribute("friendForm") FriendDTO dto,
                            Authentication authentication,
                            RedirectAttributes ra) {

        String currentEmail = currentUserService.requireEmail(authentication);
        String maskedCurrent = (currentEmail == null) ? "unknown" : currentEmail.replaceAll("(^.).*(@.*$)", "$1***$2");
        String friendEmailRaw = dto.getEmail();
        String maskedFriend = (friendEmailRaw == null) ? "unknown" : friendEmailRaw.replaceAll("(^.).*(@.*$)", "$1***$2");

        log.info("POST /connections - add friend attempt by={} friend={}", maskedCurrent, maskedFriend);

        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            log.info("POST /connections - missing friend email by={}", maskedCurrent);
            ra.addFlashAttribute("error", "Please enter a friend's email.");
            return "redirect:/connections";
        }

        String friendEmail = dto.getEmail().trim().toLowerCase();
        if (friendEmail.equalsIgnoreCase(currentEmail)) {
            log.info("POST /connections - tried to add self by={}", maskedCurrent);
            ra.addFlashAttribute("error", "You cannot add yourself.");
            return "redirect:/connections";
        }

        try {
            userService.addConnection(currentEmail, friendEmail);
            log.info("POST /connections - friend added by={} friend={}", maskedCurrent, maskedFriend);
            ra.addFlashAttribute("success", "Friend added !");

        } catch (IllegalArgumentException ex) {
            log.info("POST /connections - business error by={} friend={} reason={}",
                    maskedCurrent, maskedFriend, ex.getMessage());
            ra.addFlashAttribute("error", ex.getMessage());

        } catch (Exception ex) {
            log.error("POST /connections - unexpected error by={} friend={}", maskedCurrent, maskedFriend, ex);
            ra.addFlashAttribute("error", "Unexpected error, please try again.");
        }

        return "redirect:/connections";
    }

    @GetMapping("/connections")
    public String showAddFriend(Authentication authentication, Model model) {
        String currentEmail = currentUserService.requireEmail(authentication);
        String maskedCurrent = (currentEmail == null) ? "unknown" : currentEmail.replaceAll("(^.).*(@.*$)", "$1***$2");
        log.info("GET /connections - show add friend page for={}", maskedCurrent);

        try {
            User me = userService.getUserByEmail(currentEmail)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + maskedCurrent));

            model.addAttribute("me", me);
            if (!model.containsAttribute("friendForm")) {
                model.addAttribute("friendForm", new FriendDTO());
            }
            model.addAttribute("active", "add");

            log.info("GET /connections - page ready userId={} friends={}",
                    me.getId(),
                    me.getConnections() == null ? 0 : me.getConnections().size());
            return "connections";

        } catch (IllegalArgumentException ex) {
            log.error("GET /connections - user not found email={}", maskedCurrent, ex);
            throw ex;

        } catch (Exception ex) {
            log.error("GET /connections - unexpected error email={}", maskedCurrent, ex);
            throw ex;
        }
    }
}
