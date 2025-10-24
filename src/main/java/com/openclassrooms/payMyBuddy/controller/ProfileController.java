package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.ProfileDTO;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    // Display the profile page with a pre-filled form
    @GetMapping("/profile")
    public String getProfil(Model model, Principal principal) {
        String currentEmail = principal.getName();
        String maskedCurrent = (currentEmail == null) ? "unknown"
                : currentEmail.replaceAll("(^.).*(@.*$)", "$1***$2");
        log.info("GET /profile - load profile for email={}", maskedCurrent);

        User me = userService.getUserByEmail(currentEmail).orElse(null);
        if (me == null) {
            log.error("GET /profile - user not found email={}", maskedCurrent);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + maskedCurrent);
        }

        model.addAttribute("me", me);

        // If coming back from a redirect with errors/success, keep the existing form
        if (!model.containsAttribute("profileForm")) {
            ProfileDTO dto = new ProfileDTO();
            dto.setUsername(me.getUsername());
            dto.setEmail(me.getEmail());
            model.addAttribute("profileForm", dto);
        }
        model.addAttribute("active", "profile");

        log.info("GET /profile - page ready userId={}", me.getId());
        return "profile"; // templates/profile.html
    }

    // Handle profile update
    @PostMapping("/profile")
    public String postProfil(@ModelAttribute("profileForm") ProfileDTO dto, Principal principal, RedirectAttributes ra) {
        String currentEmail = principal.getName();
        String maskedCurrent = (currentEmail == null) ? "unknown"
                : currentEmail.replaceAll("(^.).*(@.*$)", "$1***$2");
        String newEmail = dto.getEmail();
        String maskedNew = (newEmail == null) ? "unknown"
                : newEmail.replaceAll("(^.).*(@.*$)", "$1***$2");

        log.info("POST /profile - update attempt by={} newEmail={}", maskedCurrent, maskedNew);

        try {
            userService.updateProfile(
                    currentEmail,
                    dto.getUsername(),
                    dto.getEmail(),
                    dto.getCurrentPassword(),
                    dto.getNewPassword(),
                    dto.getConfirmPassword());

            if (dto.getEmail() != null && !dto.getEmail().trim().equalsIgnoreCase(currentEmail)) {
                log.info("POST /profile - update success (email changed) by={} newEmail={}", maskedCurrent, maskedNew);
                ra.addFlashAttribute("success", "Profile updated. If you changed your email, please sign in again.");

            } else {
                log.info("POST /profile - update success (no email change) by={}", maskedCurrent);
                ra.addFlashAttribute("success", "Profile updated.");
            }

        } catch (IllegalArgumentException ex) {
            log.info("POST /profile - business error by={} reason={}", maskedCurrent, ex.getMessage());
            ProfileDTO safe = new ProfileDTO();
            safe.setUsername(dto.getUsername());
            safe.setEmail(dto.getEmail());

            ra.addFlashAttribute("error", ex.getMessage());
            ra.addFlashAttribute("profileForm", safe);

        } catch (Exception ex) {
            log.error("POST /profile - unexpected error by={}", maskedCurrent, ex);
            ra.addFlashAttribute("error", "Unexpected error, please try again.");
        }

        return "redirect:/profile";
    }
}