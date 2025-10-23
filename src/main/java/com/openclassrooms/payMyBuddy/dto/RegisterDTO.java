package com.openclassrooms.payMyBuddy.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class RegisterDTO {

    private String email;

    private String password;

    private String confirmPassword;

    private String username;
}
