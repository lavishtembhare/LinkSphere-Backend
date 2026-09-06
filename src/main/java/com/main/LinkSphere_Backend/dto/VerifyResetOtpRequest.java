package com.main.LinkSphere_Backend.dto;
import lombok.Data;
@Data
public class VerifyResetOtpRequest {
    private String usernameOrEmail;
    private String otp;
}