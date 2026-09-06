package com.main.LinkSphere_Backend.repo;

import com.main.LinkSphere_Backend.models.OtpPurpose;
import com.main.LinkSphere_Backend.models.OtpVerification;
import com.main.LinkSphere_Backend.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findTopByUserAndPurposeOrderByIdDesc(User user, OtpPurpose purpose);
    Optional<OtpVerification> findByResetTokenAndPurpose(String resetToken, OtpPurpose purpose);
}