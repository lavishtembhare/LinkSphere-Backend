package com.main.LinkSphere_Backend.dto;
import lombok.Data;
@Data
public class VerifyRegistrationOtpRequest {
    private String username;
    private String otp;
}