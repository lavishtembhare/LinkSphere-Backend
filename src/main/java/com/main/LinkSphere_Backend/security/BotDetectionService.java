package com.main.LinkSphere_Backend.security;

import com.main.LinkSphere_Backend.models.UrlMapping;
import com.main.LinkSphere_Backend.repo.ClickEventRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BotDetectionService {

    private static final List<String> BOT_MARKERS = List.of(
            "bot", "crawler", "spider", "curl", "wget", "python-requests", "scrapy", "headlesschrome"
    );
    private static final int RATE_WINDOW_SECONDS = 60;
    private static final int RATE_MAX_CLICKS = 5;
    private static final int BURST_WINDOW_SECONDS = 30;
    private static final int BURST_THRESHOLD = 20;

    private final ClickEventRepository clickEventRepository;

    public BotDetectionService(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    public boolean isSuspicious(UrlMapping urlMapping, String ipAddress, String userAgent) {
        if (looksLikeBot(userAgent)) return true;
        return exceedsRateLimit(urlMapping, ipAddress);
    }

    /** DDoS-style flood check: total click volume regardless of source IP. */
    public boolean isUnderBurstAttack(UrlMapping urlMapping) {
        LocalDateTime windowStart = LocalDateTime.now().minusSeconds(BURST_WINDOW_SECONDS);
        return clickEventRepository.countByUrlMappingAndClickDateAfter(urlMapping, windowStart) >= BURST_THRESHOLD;
    }

    private boolean looksLikeBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return true;
        String lower = userAgent.toLowerCase();
        return BOT_MARKERS.stream().anyMatch(lower::contains);
    }

    private boolean exceedsRateLimit(UrlMapping urlMapping, String ipAddress) {
        if (ipAddress == null) return false;
        LocalDateTime windowStart = LocalDateTime.now().minusSeconds(RATE_WINDOW_SECONDS);
        return clickEventRepository.countByUrlMappingAndIpAddressAndClickDateAfter(urlMapping, ipAddress, windowStart)
                >= RATE_MAX_CLICKS;
    }
}