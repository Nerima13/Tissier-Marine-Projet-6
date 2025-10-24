package com.openclassrooms.payMyBuddy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class CurrentUserService {

    private String maskEmail(String email) {
        return (email == null) ? "unknown" : email.trim().toLowerCase().replaceAll("(^.).*(@.*$)", "$1***$2");
    }

    // Return the user's email (form login or OAuth2), or throw if not found
    public String requireEmail(Authentication auth) {
        if (auth == null) {
            log.warn("CurrentUserService.requireEmail - null authentication");
            throw new IllegalStateException("Cannot find user email.");
        }

        // OAuth2: read "email" from user attributes
        if (auth instanceof OAuth2AuthenticationToken oauth) {
            Map<String, Object> attrs = oauth.getPrincipal().getAttributes();
            Object value = attrs.get("email");
            if (value instanceof String s) {
                String email = s.trim().toLowerCase();
                if (!email.isEmpty()) {
                    log.info("CurrentUserService.requireEmail - OAuth2 resolved email={}", maskEmail(email));
                    return email;
                }
            }
            log.warn("CurrentUserService.requireEmail - OAuth2 email not found");
            throw new IllegalStateException("Cannot find user email.");
        }

        // Form login: username is the email
        String name = auth.getName();
        if (name == null || name.trim().isEmpty()) {
            log.warn("CurrentUserService.requireEmail - form login email not found");
            throw new IllegalStateException("Cannot find user email.");
        }
        String email = name.trim().toLowerCase();
        log.info("CurrentUserService.requireEmail - form resolved email={}", maskEmail(email));
        return email;
    }
}