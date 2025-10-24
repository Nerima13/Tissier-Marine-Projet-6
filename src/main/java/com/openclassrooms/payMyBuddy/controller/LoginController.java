package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

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
            log.info("GET /login - already authenticated, redirect to /transfer");
            return "redirect:/transfer";
        }
        log.info("GET /login - show login page");
        return "login"; // => templates/login.html
    }

    // Home page after login
    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication == null) {
            log.info("GET / - no authentication, redirect to /login");
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
            String maskedEmail = (email == null) ? "unknown" : email.replaceAll("(^.).*(@.*$)", "$1***$2");

            if (email == null) { // without email we cannot create a user
                log.info("GET / - OAuth2 login without email, redirect to /login");
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

            try {
                log.info("GET / - OAuth2 user getOrCreate email={} name={}", maskedEmail, name);
                userService.getOrCreateOAuth2User(email, name);
                log.info("GET / - OAuth2 user ready email={}", maskedEmail);

            } catch (Exception ex) {
                log.error("GET / - OAuth2 processing failed email={}", maskedEmail, ex);
                return "redirect:/login";
            }

        } else {
            log.info("GET / - non-OAuth2 authentication, redirect to /transfer");
        }

        return "redirect:/transfer";
    }
}