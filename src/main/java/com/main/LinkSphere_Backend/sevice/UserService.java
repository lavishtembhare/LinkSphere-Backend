package com.main.LinkSphere_Backend.sevice;

import com.main.LinkSphere_Backend.dto.LoginRequest;
import com.main.LinkSphere_Backend.exception.DuplicateEmailException;
import com.main.LinkSphere_Backend.exception.DuplicateUsernameException;
import com.main.LinkSphere_Backend.exception.InvalidCredentialsException;
import com.main.LinkSphere_Backend.models.RefreshToken;
import com.main.LinkSphere_Backend.models.UrlMapping;
import com.main.LinkSphere_Backend.models.User;
import com.main.LinkSphere_Backend.repo.ClickEventRepository;
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

import java.util.List;

@Service
@AllArgsConstructor
public class UserService {
    private PasswordEncoder passwordEncoder;
    private UserRepository userRepository;
    private AuthenticationManager authenticationManager;
    private JwtUtils jwtUtils;
    private RefreshTokenService refreshTokenService;
    private UrlMappingRepository urlMappingRepository;
    private ClickEventRepository clickEventRepository;

    @Transactional
    public User registerUser(User user){
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new DuplicateUsernameException("Username '" + user.getUsername() + "' is already taken.");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateEmailException("An account with that email address already exists.");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setEnabled(true);
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateUsernameException("Username '" + user.getUsername() + "' is already taken.");
        }
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

    @Transactional
    public void updateEmail(String username, String newEmail, String password) {
        User user = findByUsername(username);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Incorrect password.");
        }
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new DuplicateEmailException("That's already your current email address.");
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new DuplicateEmailException("That email address is already in use.");
        }

        user.setEmail(newEmail);
        userRepository.save(user);
    }

    // ---------- Forgot password — no email sent, matches username + email on file ----------

    public void resetPasswordWithoutEmail(String username, String email, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Username and email do not match our records."));

        if (email == null || !email.equalsIgnoreCase(user.getEmail())) {
            throw new InvalidCredentialsException("Username and email do not match our records.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.deleteAllForUser(user);
    }

    // ---------- Account deletion — password confirmation only ----------

    @Transactional
    public void deleteAccount(String username, String password) {
        User user = findByUsername(username);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Incorrect password.");
        }

        List<UrlMapping> urlMappings = urlMappingRepository.findByUser(user);
        for (UrlMapping mapping : urlMappings) {
            clickEventRepository.deleteByUrlMapping(mapping);
        }
        urlMappingRepository.deleteAll(urlMappings);

        refreshTokenService.deleteAllForUser(user);

        userRepository.delete(user);
    }
}