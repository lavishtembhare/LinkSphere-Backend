package com.main.LinkSphere_Backend.dto;
import lombok.Data;
@Data
public class ForgotPasswordRequest {
    private String username;
    private String email;
    private String newPassword;
}