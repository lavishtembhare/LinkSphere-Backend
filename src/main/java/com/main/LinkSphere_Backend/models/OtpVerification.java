package com.main.LinkSphere_Backend.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "otp_verification")
public class OtpVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String targetEmail;
    private String otpHash;

    @Enumerated(EnumType.STRING)
    private OtpPurpose purpose;

    private LocalDateTime expiryDate;
    private boolean verified = false;
    private String resetToken;
}