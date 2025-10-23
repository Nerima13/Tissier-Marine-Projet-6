package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.ProfileDTO;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.UserService;
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

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    // Display the profile page with a pre-filled form
    @GetMapping("/profile")
    public String getProfil(Model model, Principal principal) {
        User me = userService.getUserByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + principal.getName()));
        model.addAttribute("me", me);

        // If coming back from a redirect with errors/success, keep the existing form
        if (!model.containsAttribute("profileForm")) {
            ProfileDTO dto = new ProfileDTO();
            dto.setUsername(me.getUsername());
            dto.setEmail(me.getEmail());
            model.addAttribute("profileForm", dto);
        }
        model.addAttribute("active", "profile");

        return "profile"; // templates/profile.html
    }

    // Handle profile update
    @PostMapping("/profile")
    public String postProfil(@ModelAttribute("profileForm") ProfileDTO dto, Principal principal, RedirectAttributes ra) {
        try {
            userService.updateProfile(
                    principal.getName(),
                    dto.getUsername(),
                    dto.getEmail(),
                    dto.getCurrentPassword(),
                    dto.getNewPassword(),
                    dto.getConfirmPassword());

            if (dto.getEmail() != null && !dto.getEmail().trim().equalsIgnoreCase(principal.getName())) {
                ra.addFlashAttribute("success", "Profile updated. If you changed your email, please sign in again.");
            } else {
                ra.addFlashAttribute("success", "Profile updated.");
            }
        } catch (IllegalArgumentException ex) {
            ProfileDTO safe = new ProfileDTO();
            safe.setUsername(dto.getUsername());
            safe.setEmail(dto.getEmail());

            ra.addFlashAttribute("error", ex.getMessage());
            ra.addFlashAttribute("profileForm", safe);
        }
        return "redirect:/profile";
    }
}
