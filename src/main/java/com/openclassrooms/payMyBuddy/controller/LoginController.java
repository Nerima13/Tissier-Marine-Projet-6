package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.service.UserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class LoginController {

    private final UserService userService;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    // Login page
    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/transfer";
        }
        return "login"; // => templates/login.html
    }

    // Home page after login
    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        // OAuth2 (Facebook)
        if (authentication instanceof OAuth2AuthenticationToken token) {
            Map<String, Object> attrs = token.getPrincipal().getAttributes();

            // Required email (needed to create or find the user)
            String email = null;
            Object emailAttr = attrs.get("email");
            if (emailAttr instanceof String e) {
                e = e.trim().toLowerCase();
                if (!e.isEmpty()) {
                    email = e;
                }
            }
            if (email == null) { // without email we cannot create a user
                return "redirect:/login";
            }

            // Display name (Facebook: "name" or "first_name"/"last_name")
            String name = null;
            if (attrs.get("name") instanceof String n && !n.trim().isEmpty()) {
                name = n.trim();
            } else {
                String first = (attrs.get("first_name") instanceof String f) ? f.trim() : null;
                String last  = (attrs.get("last_name")  instanceof String l) ? l.trim() : null;
                if ((first != null && !first.isEmpty()) || (last != null && !last.isEmpty())) {
                    name = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                }
            }
            if (name == null || name.isEmpty()) {
                name = email; // safe fallback
            }

            // Create or retrieve the user
            userService.getOrCreateOAuth2User(email, name);
        }

        return "redirect:/transfer";
    }
}