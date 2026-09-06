package com.main.LinkSphere_Backend.sevice;

import com.main.LinkSphere_Backend.exception.OtpException;
import com.main.LinkSphere_Backend.models.OtpPurpose;
import com.main.LinkSphere_Backend.models.OtpVerification;
import com.main.LinkSphere_Backend.models.User;
import com.main.LinkSphere_Backend.repo.OtpVerificationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OtpService {

    private static final int OTP_VALIDITY_MINUTES = 10;
    private final SecureRandom random = new SecureRandom();

    private final OtpVerificationRepository otpVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public OtpService(OtpVerificationRepository otpVerificationRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.otpVerificationRepository = otpVerificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    public void generateAndSendOtp(User user, String targetEmail, OtpPurpose purpose, String purposeDescription) {
        String otp = String.format("%06d", random.nextInt(1_000_000));

        OtpVerification record = new OtpVerification();
        record.setUser(user);
        record.setTargetEmail(targetEmail);
        record.setOtpHash(passwordEncoder.encode(otp));
        record.setPurpose(purpose);
        record.setExpiryDate(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
        record.setVerified(false);
        otpVerificationRepository.save(record);

        emailService.sendOtpEmail(targetEmail, otp, purposeDescription);
    }

    public OtpVerification verifyOtp(User user, OtpPurpose purpose, String submittedOtp) {
        OtpVerification record = otpVerificationRepository
                .findTopByUserAndPurposeOrderByIdDesc(user, purpose)
                .orElseThrow(() -> new OtpException("No verification code was requested. Please request a new one."));

        if (record.isVerified()) {
            throw new OtpException("This code has already been used.");
        }
        if (record.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new OtpException("This code has expired. Please request a new one.");
        }
        if (submittedOtp == null || !passwordEncoder.matches(submittedOtp, record.getOtpHash())) {
            throw new OtpException("Incorrect verification code.");
        }

        record.setVerified(true);
        if (purpose == OtpPurpose.PASSWORD_RESET) {
            record.setResetToken(UUID.randomUUID().toString());
        }
        return otpVerificationRepository.save(record);
    }
}