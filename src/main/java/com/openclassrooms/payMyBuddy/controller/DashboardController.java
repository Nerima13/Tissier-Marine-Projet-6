package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.FriendForm;
import com.openclassrooms.payMyBuddy.dto.TransferForm;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.CurrentUserService;
import com.openclassrooms.payMyBuddy.service.TransactionService;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DashboardController {

    private final UserService userService;
    private final TransactionService transactionService;
    private final CurrentUserService currentUserService;

    public DashboardController(UserService userService,
                               TransactionService transactionService,
                               CurrentUserService currentUserService) {
        this.userService = userService;
        this.transactionService = transactionService;
        this.currentUserService = currentUserService;
    }

    // Dashboard page (balance, friends, history)
    @GetMapping("/transfer")
    public String showDashboard(Authentication authentication, Model model) {
        String email = currentUserService.requireEmail(authentication);
        User me = userService.getUserByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        model.addAttribute("me", me);
        model.addAttribute("friends", me.getConnections());
        model.addAttribute("feed", transactionService.getFeedForUser(me.getId()));
        model.addAttribute("friendForm", new FriendForm());
        model.addAttribute("transferForm", new TransferForm());
        return "dashboard";
    }

    // Add a friend by email
    @PostMapping("/connections")
    public String addFriend(@ModelAttribute("friendForm") FriendForm form,
                            Authentication authentication,
                            RedirectAttributes ra) {

        String currentEmail = currentUserService.requireEmail(authentication);

        if (form.getEmail() == null || form.getEmail().trim().isEmpty()) {
            ra.addFlashAttribute("error", "Please enter a friend's email.");
            return "redirect:/connections";
        }

        String friendEmail = form.getEmail().trim().toLowerCase();
        if (friendEmail.equalsIgnoreCase(currentEmail)) {
            ra.addFlashAttribute("error", "You cannot add yourself.");
            return "redirect:/connections";
        }

        try {
            userService.addConnection(currentEmail, friendEmail);
            ra.addFlashAttribute("success", "Friend added !");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/connections";
    }

    @GetMapping("/connections")
    public String showAddFriend(Authentication authentication, Model model) {
        String currentEmail = currentUserService.requireEmail(authentication);
        User me = userService.getUserByEmail(currentEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + currentEmail));

        model.addAttribute("me", me);
        if (!model.containsAttribute("friendForm")) {
            model.addAttribute("friendForm", new FriendForm());
        }
        model.addAttribute("active", "add");

        return "connections";
    }
}
