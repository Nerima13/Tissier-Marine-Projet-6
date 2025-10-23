package com.openclassrooms.payMyBuddy.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class ProfileDTO {

    private String username;

    private String email;

    private String currentPassword;

    private String newPassword;

    private String confirmPassword;
}
