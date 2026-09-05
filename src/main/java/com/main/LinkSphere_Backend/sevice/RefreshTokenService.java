package com.main.LinkSphere_Backend.sevice;

import com.main.LinkSphere_Backend.exception.TokenRefreshException;
import com.main.LinkSphere_Backend.models.RefreshToken;
import com.main.LinkSphere_Backend.models.User;
import com.main.LinkSphere_Backend.repo.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    @Value("${jwt.refresh.expiration}")
    private long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(LocalDateTime.now().plus(Duration.ofMillis(refreshTokenDurationMs)));
        refreshToken.setToken(UUID.randomUUID().toString());
        return refreshTokenRepository.save(refreshToken);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException("Refresh token has expired. Please sign in again.");
        }
        return token;
    }
    public void updateAccessToken(RefreshToken refreshToken, String newAccessToken) {
        refreshToken.setCurrentAccessToken(newAccessToken);
        refreshTokenRepository.save(refreshToken);
    }

    public boolean isCurrentAccessToken(String username, String accessToken) {
        List<RefreshToken> sessions = refreshTokenRepository.findByUserUsername(username);
        return sessions.stream().anyMatch(rt -> accessToken.equals(rt.getCurrentAccessToken()));
    }

    public void delete(RefreshToken token) {
        refreshTokenRepository.delete(token);
    }
}