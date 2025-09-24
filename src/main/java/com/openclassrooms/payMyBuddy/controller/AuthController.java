package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.RegisterForm;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    // Show the registration form
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "register"; // => templates/register.html
    }

    // Handle the registration
    @PostMapping("/register")
    public String register(@ModelAttribute("form") RegisterForm form, Model model) {

        // 1) Basic required fields
        if (form.getEmail() == null || form.getPassword() == null || form.getConfirmPassword() == null) {
            model.addAttribute("error", "Please fill in all required fields.");
            model.addAttribute("form", form);
            return "register";
        }
        String email = form.getEmail().trim().toLowerCase();
        String pwd   = form.getPassword().trim();
        String pwd2  = form.getConfirmPassword().trim();
        String username = form.getUsername() == null ? null : form.getUsername().trim();

        if (email.isEmpty() || pwd.isEmpty() || pwd2.isEmpty()) {
            model.addAttribute("error", "Please fill in all required fields.");
            model.addAttribute("form", form);
            return "register";
        }

        // 2) Passwords must match
        if (!pwd.equals(pwd2)) {
            model.addAttribute("error", "Passwords do not match.");
            model.addAttribute("form", form);
            return "register";
        }

        // 3) Build User and delegate to the existing service
        User u = new User();
        u.setEmail(email);
        u.setPassword(pwd);        // Will be encoded by userService.registerUser(...)
        u.setUsername(username);   // Optional

        try {
            userService.registerUser(u); // checks email uniqueness, sets balance=0.00, encodes password
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage()); // "Email already used"
            model.addAttribute("form", form);
            return "register";
        }

        // 4) Success = go to default Spring login page
        return "redirect:/login";
    }
}
