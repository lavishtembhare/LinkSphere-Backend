package com.main.LinkSphere_Backend.repo;

import com.main.LinkSphere_Backend.models.RefreshToken;
import com.main.LinkSphere_Backend.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findByUserUsername(String username);

    @Modifying
    int deleteByUser(User user);
}