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

    // Page tableau de bord (solde, amis, historique)
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

    // Ajouter un ami par e-mail
    @PostMapping("/connections")
    public String addFriend(@ModelAttribute("friendForm") FriendForm form, Authentication authentication, Model model) {
        String currentEmail = resolveEmail(authentication);
        if (currentEmail == null) return "redirect:/login";

        if (form.getEmail() == null || form.getEmail().trim().isEmpty()) {
            model.addAttribute("error", "Please enter a friend's email.");
            return showDashboard(authentication, model);
        }

        String friendEmail = form.getEmail().trim().toLowerCase();
        if (friendEmail.equalsIgnoreCase(currentEmail)) {
            model.addAttribute("error", "You cannot add yourself.");
            return showDashboard(authentication, model);
        }

        try {
            userService.addConnection(currentEmail, friendEmail);
            model.addAttribute("success", "Friend added!");
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return showDashboard(authentication, model);
    }

    // Effectuer un virement
    @PostMapping("/transfer")
    public String doTransfer(@ModelAttribute("transferForm") TransferForm form,
                             Authentication authentication,
                             Model model) {
        String currentEmail = resolveEmail(authentication);
        if (currentEmail == null) return "redirect:/login";

        User me = userService.getUserByEmail(currentEmail).orElse(null);
        if (me == null) return "redirect:/login";

        if (form.getReceiverEmail() == null || form.getAmount() == null) {
            model.addAttribute("error", "Please select a receiver and enter an amount.");
            return showDashboard(authentication, model);
        }

        String receiverEmail = form.getReceiverEmail().trim().toLowerCase();
        try {
            userService.transfer(me.getId(), receiverEmail, form.getAmount(), form.getDescription());
            model.addAttribute("success", "Transfer completed.");
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return showDashboard(authentication, model);
    }

    // Récupère l'email selon le type d'authentification (form login ou OAuth2)
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
            String name = authentication.getName(); // pour le form login = email
            return name == null ? null : name.trim().toLowerCase();
        }
    }
}
