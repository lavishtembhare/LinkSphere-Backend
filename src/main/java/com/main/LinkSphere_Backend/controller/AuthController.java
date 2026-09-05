package com.main.LinkSphere_Backend.controller;

import com.main.LinkSphere_Backend.dto.LoginRequest;
import com.main.LinkSphere_Backend.dto.RegisterRequest;
import com.main.LinkSphere_Backend.exception.TokenRefreshException;
import com.main.LinkSphere_Backend.models.RefreshToken;
import com.main.LinkSphere_Backend.models.User;
import com.main.LinkSphere_Backend.security.jwt.JwtUtils;
import com.main.LinkSphere_Backend.sevice.RefreshTokenService;
import com.main.LinkSphere_Backend.sevice.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/public")
@AllArgsConstructor
public class AuthController {
    private UserService userService;
    private RefreshTokenService refreshTokenService;
    private JwtUtils jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest){
        return ResponseEntity.ok(userService.authenticateUser(loginRequest));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest){
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(registerRequest.getPassword());
        user.setRole("ROLE_USER");
        userService.registerUser(user);
        return ResponseEntity.ok("Registration Successful");
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(HttpServletRequest request) {
        String requestRefreshToken = jwtUtils.getJwtFromHeader(request);
        if (requestRefreshToken == null) {
            throw new TokenRefreshException("Refresh token missing — provide it as 'Authorization: Bearer <refreshToken>'.");
        }

        RefreshToken refreshToken = refreshTokenService.findByToken(requestRefreshToken)
                .orElseThrow(() -> new TokenRefreshException("Refresh token is not in database."));

        refreshTokenService.verifyExpiration(refreshToken);

        User user = refreshToken.getUser();
        String newAccessToken = jwtUtils.generateTokenFromUsername(user.getUsername(), user.getRole());
        refreshTokenService.updateAccessToken(refreshToken, newAccessToken);

        return ResponseEntity.ok(Map.of("token", newAccessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String requestRefreshToken = jwtUtils.getJwtFromHeader(request);
        if (requestRefreshToken != null) {
            refreshTokenService.findByToken(requestRefreshToken).ifPresent(refreshTokenService::delete);
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }
}