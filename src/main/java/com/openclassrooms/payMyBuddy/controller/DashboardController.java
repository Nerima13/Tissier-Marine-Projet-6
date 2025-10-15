package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.FriendForm;
import com.openclassrooms.payMyBuddy.dto.TransferForm;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.TransactionService;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class DashboardController {

    private final UserService userService;
    private final TransactionService transactionService;

    public DashboardController(UserService userService, TransactionService transactionService) {
        this.userService = userService;
        this.transactionService = transactionService;
    }

    // Dashboard page (balance, friends, history)
    @GetMapping("/transfer")
    public String showDashboard(Authentication authentication, Model model) {
        String email = resolveEmail(authentication);
        if (email == null) return "redirect:/login";

        User me = userService.getUserByEmail(email).orElse(null);
        if (me == null) return "redirect:/login";

        model.addAttribute("me", me);
        model.addAttribute("friends", me.getConnections());
        model.addAttribute("feed", transactionService.getFeedForUser(me.getId()));
        model.addAttribute("friendForm", new FriendForm());
        model.addAttribute("transferForm", new TransferForm());
        return "dashboard"; // => templates/dashboard.html
    }

    // Add a friend by email
    @PostMapping("/connections")
    public String addFriend(@ModelAttribute("friendForm") FriendForm form,
                            Authentication authentication,
                            org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {

        String currentEmail = resolveEmail(authentication);
        if (currentEmail == null) return "redirect:/login";

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
        String currentEmail = resolveEmail(authentication);
        if (currentEmail == null) return "redirect:/login";

        User me = userService.getUserByEmail(currentEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + currentEmail));

        model.addAttribute("me", me);
        if (!model.containsAttribute("friendForm")) {
            model.addAttribute("friendForm", new FriendForm());
        }
        model.addAttribute("active", "add");

        return "connections";
    }

    // Resolve the user's email depending on the authentication type (form login or OAuth2)
    private String resolveEmail(Authentication authentication) {
        if (authentication == null) return null;
        if (authentication instanceof OAuth2AuthenticationToken) {
            Map<String, Object> attrs = ((OAuth2AuthenticationToken) authentication).getPrincipal().getAttributes();
            Object emailObj = attrs.get("email");
            if (emailObj instanceof String) {
                String e = ((String) emailObj).trim().toLowerCase();
                if (!e.isEmpty()) return e;
            }
            return null;
        } else {
            String name = authentication.getName(); // for form login this is the email
            return name == null ? null : name.trim().toLowerCase();
        }
    }
}
