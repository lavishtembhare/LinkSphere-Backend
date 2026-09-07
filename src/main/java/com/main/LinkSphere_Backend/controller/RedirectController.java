package com.main.LinkSphere_Backend.controller;

import com.main.LinkSphere_Backend.ai.ThreatAnalysisService;
import com.main.LinkSphere_Backend.models.ClickEvent;
import com.main.LinkSphere_Backend.models.UrlMapping;
import com.main.LinkSphere_Backend.repo.ClickEventRepository;
import com.main.LinkSphere_Backend.repo.UrlMappingRepository;
import com.main.LinkSphere_Backend.security.BotDetectionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@RestController
public class RedirectController {

    private final UrlMappingRepository urlMappingRepository;
    private final ClickEventRepository clickEventRepository;
    private final BotDetectionService botDetectionService;
    private final ThreatAnalysisService threatAnalysisService;
    private final String frontendUrl;

    public RedirectController(
            UrlMappingRepository urlMappingRepository,
            ClickEventRepository clickEventRepository,
            BotDetectionService botDetectionService,
            ThreatAnalysisService threatAnalysisService,
            @Value("${frontend.url:http://localhost:5173}") String frontendUrl) {
        this.urlMappingRepository = urlMappingRepository;
        this.clickEventRepository = clickEventRepository;
        this.botDetectionService = botDetectionService;
        this.threatAnalysisService = threatAnalysisService;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    @GetMapping("/{shortUrl}")
    public ResponseEntity<?> redirect(@PathVariable String shortUrl, HttpServletRequest request) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping == null) {
            return buildErrorRedirect("404", "Link Not Found", "We can't seem to find the link you're looking for.");
        }

        // 1. Initial active check
        if (!urlMapping.isActive()) {
            return buildErrorRedirect("403", "Security Shield Triggered", disabledMessage(urlMapping));
        }

        String ipAddress = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        boolean suspicious = botDetectionService.isSuspicious(urlMapping, ipAddress, userAgent);

        ClickEvent clickEvent = new ClickEvent();
        clickEvent.setUrlMapping(urlMapping);
        clickEvent.setClickDate(LocalDateTime.now());
        clickEvent.setIpAddress(ipAddress);
        clickEvent.setUserAgent(userAgent);
        clickEvent.setSuspicious(suspicious);
        clickEventRepository.save(clickEvent);

        if (!suspicious) {
            urlMapping.setClickCount(urlMapping.getClickCount() + 1);
        }

        // 2. DDoS burst check
        if (botDetectionService.isUnderBurstAttack(urlMapping)) {
            urlMapping.setActive(false);
            threatAnalysisService.analyzeAndExplain(urlMapping.getId());
        }

        urlMappingRepository.save(urlMapping);

        // 3. Post-burst evaluation
        if (!urlMapping.isActive()) {
            return buildErrorRedirect("403", "Security Shield Triggered", disabledMessage(urlMapping));
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, urlMapping.getOriginalUrl())
                .build();
    }

    private ResponseEntity<?> buildErrorRedirect(String code, String title, String message) {
        String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);

        String redirectUri = String.format("%s/error?code=%s&title=%s&message=%s",
                frontendUrl, code, encodedTitle, encodedMessage);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUri)
                .build();
    }

    private String disabledMessage(UrlMapping urlMapping) {
        return urlMapping.getDisabledReason() != null
                ? urlMapping.getDisabledReason()
                : "This link has been disabled due to suspicious activity.";
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}