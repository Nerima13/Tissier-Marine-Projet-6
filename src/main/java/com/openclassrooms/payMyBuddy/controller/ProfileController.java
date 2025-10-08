package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.ProfileForm;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
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
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + principal.getName()));
        model.addAttribute("me", me);

        // If coming back from a redirect with errors/success, keep the existing form
        if (!model.containsAttribute("profileForm")) {
            ProfileForm form = new ProfileForm();
            form.setUsername(me.getUsername());
            form.setEmail(me.getEmail());
            model.addAttribute("profileForm", form);
        }
        model.addAttribute("active", "profile");

        return "profile"; // templates/profile.html
    }

    // Handle profile update
    @PostMapping("/profile")
    public String postProfil(@ModelAttribute("profileForm") ProfileForm form, Principal principal, RedirectAttributes ra) {
        try {
            userService.updateProfile(
                    principal.getName(),
                    form.getUsername(),
                    form.getEmail(),
                    form.getCurrentPassword(),
                    form.getNewPassword(),
                    form.getConfirmPassword());

            if (form.getEmail() != null && !form.getEmail().trim().equalsIgnoreCase(principal.getName())) {
                ra.addFlashAttribute("success", "Profile updated. If you changed your email, please sign in again.");
            } else {
                ra.addFlashAttribute("success", "Profile updated.");
            }
        } catch (IllegalArgumentException ex) {
            ProfileForm safe = new ProfileForm();
            safe.setUsername(form.getUsername());
            safe.setEmail(form.getEmail());

            ra.addFlashAttribute("error", ex.getMessage());
            ra.addFlashAttribute("profileForm", safe);
        }
        return "redirect:/profile";
    }
}
