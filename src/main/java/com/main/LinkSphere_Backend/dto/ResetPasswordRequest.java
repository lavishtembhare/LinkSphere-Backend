package com.main.LinkSphere_Backend.dto;
import lombok.Data;
@Data
public class ResetPasswordRequest {
    private String resetToken;
    private String newPassword;
}