package com.main.LinkSphere_Backend.sevice;

import com.main.LinkSphere_Backend.dto.LoginRequest;
import com.main.LinkSphere_Backend.exception.DuplicateEmailException;
import com.main.LinkSphere_Backend.exception.DuplicateUsernameException;
import com.main.LinkSphere_Backend.exception.InvalidCredentialsException;
import com.main.LinkSphere_Backend.exception.OtpException;
import com.main.LinkSphere_Backend.models.OtpPurpose;
import com.main.LinkSphere_Backend.models.OtpVerification;
import com.main.LinkSphere_Backend.models.RefreshToken;
import com.main.LinkSphere_Backend.models.UrlMapping;
import com.main.LinkSphere_Backend.models.User;
import com.main.LinkSphere_Backend.repo.ClickEventRepository;
import com.main.LinkSphere_Backend.repo.OtpVerificationRepository;
import com.main.LinkSphere_Backend.repo.UrlMappingRepository;
import com.main.LinkSphere_Backend.repo.UserRepository;
import com.main.LinkSphere_Backend.security.jwt.JwtAuthenticationResponse;
import com.main.LinkSphere_Backend.security.jwt.JwtUtils;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class UserService {
    private PasswordEncoder passwordEncoder;
    private UserRepository userRepository;
    private AuthenticationManager authenticationManager;
    private JwtUtils jwtUtils;
    private RefreshTokenService refreshTokenService;
    private OtpService otpService;
    private OtpVerificationRepository otpVerificationRepository;
    private UrlMappingRepository urlMappingRepository;
    private ClickEventRepository clickEventRepository;

    @Transactional
    public User registerUser(User user){
        Optional<User> existing = userRepository.findByUsername(user.getUsername());

        if (existing.isPresent()) {
            User existingUser = existing.get();
            if (existingUser.isEnabled()) {
                throw new DuplicateUsernameException("Username '" + user.getUsername() + "' is already taken.");
            }
            if (!existingUser.getEmail().equalsIgnoreCase(user.getEmail())
                    && userRepository.existsByEmail(user.getEmail())) {
                throw new DuplicateEmailException("An account with that email address already exists.");
            }
            existingUser.setEmail(user.getEmail());
            existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
            User saved = userRepository.save(existingUser);
            otpService.generateAndSendOtp(saved, saved.getEmail(), OtpPurpose.REGISTRATION, "verifying your email address");
            return saved;
        }

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateEmailException("An account with that email address already exists.");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setEnabled(false);
        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateUsernameException("Username '" + user.getUsername() + "' is already taken.");
        }
        otpService.generateAndSendOtp(saved, saved.getEmail(), OtpPurpose.REGISTRATION, "verifying your email address");
        return saved;
    }

    @Transactional
    public void verifyRegistrationOtp(String username, String otp) {
        User user = findByUsername(username);
        if (user.isEnabled()) {
            throw new OtpException("This account is already verified — please log in.");
        }
        otpService.verifyOtp(user, OtpPurpose.REGISTRATION, otp);
        user.setEnabled(true);
        userRepository.save(user);
    }

    public JwtAuthenticationResponse authenticateUser(LoginRequest loginRequest){
        Authentication authentication=authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(),loginRequest.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails= (UserDetailsImpl) authentication.getPrincipal();
        String jwt=jwtUtils.generateToken(userDetails);

        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        refreshTokenService.updateAccessToken(refreshToken, jwt);

        return new JwtAuthenticationResponse(jwt, refreshToken.getToken());
    }

    public User findByUsername(String name) {
        return userRepository.findByUsername(name).orElseThrow(
                ()->new UsernameNotFoundException("User not Found")
        );
    }

    // ---------- Profile management ----------

    @Transactional
    public JwtAuthenticationResponse updateUsername(String currentUsername, String newUsername) {
        if (currentUsername.equals(newUsername)) {
            throw new DuplicateUsernameException("That's already your current username.");
        }
        if (userRepository.existsByUsername(newUsername)) {
            throw new DuplicateUsernameException("Username '" + newUsername + "' is already taken.");
        }

        User user = findByUsername(currentUsername);
        user.setUsername(newUsername);
        userRepository.save(user);

        refreshTokenService.deleteAllForUser(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        String newAccessToken = jwtUtils.generateTokenFromUsername(newUsername, user.getRole());
        refreshTokenService.updateAccessToken(refreshToken, newAccessToken);

        return new JwtAuthenticationResponse(newAccessToken, refreshToken.getToken());
    }

    public void requestEmailChange(String username, String newEmail) {
        User user = findByUsername(username);
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new OtpException("That's already your current email address.");
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new OtpException("That email address is already in use.");
        }
        otpService.generateAndSendOtp(user, newEmail, OtpPurpose.EMAIL_CHANGE, "changing your email address");
    }

    @Transactional
    public void confirmEmailChange(String username, String otp) {
        User user = findByUsername(username);
        OtpVerification record = otpService.verifyOtp(user, OtpPurpose.EMAIL_CHANGE, otp);
        user.setEmail(record.getTargetEmail());
        userRepository.save(user);
    }

    // ---------- Forgot password (no login required) ----------

    public void initiatePasswordReset(String usernameOrEmail) {
        userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail).ifPresent(user ->
                otpService.generateAndSendOtp(user, user.getEmail(), OtpPurpose.PASSWORD_RESET, "resetting your password")
        );
    }

    public String verifyPasswordResetOtp(String usernameOrEmail, String otp) {
        User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new OtpException("Incorrect verification code."));
        OtpVerification record = otpService.verifyOtp(user, OtpPurpose.PASSWORD_RESET, otp);
        return record.getResetToken();
    }

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        OtpVerification record = otpVerificationRepository
                .findByResetTokenAndPurpose(resetToken, OtpPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new OtpException("Invalid or expired reset token."));

        if (!record.isVerified() || record.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new OtpException("Invalid or expired reset token.");
        }

        User user = record.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenService.deleteAllForUser(user);

        record.setResetToken(null);
        otpVerificationRepository.save(record);
    }

    // ---------- Account deletion ----------

    public void requestAccountDeletion(String username, String password) {
        User user = findByUsername(username);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Incorrect password.");
        }
        otpService.generateAndSendOtp(user, user.getEmail(), OtpPurpose.ACCOUNT_DELETION, "confirming account deletion");
    }

    @Transactional
    public void confirmAccountDeletion(String username, String otp) {
        User user = findByUsername(username);
        otpService.verifyOtp(user, OtpPurpose.ACCOUNT_DELETION, otp);

        // Deepest dependency first — ClickEvent references UrlMapping,
        // UrlMapping references User, same ordering deleteUrl() already
        // relies on elsewhere in this project.
        List<UrlMapping> urlMappings = urlMappingRepository.findByUser(user);
        for (UrlMapping mapping : urlMappings) {
            clickEventRepository.deleteByUrlMapping(mapping);
        }
        urlMappingRepository.deleteAll(urlMappings);

        refreshTokenService.deleteAllForUser(user);
        otpVerificationRepository.deleteByUser(user);

        userRepository.delete(user);
    }
}