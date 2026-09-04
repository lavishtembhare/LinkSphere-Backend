package com.main.LinkSphere_Backend.controller;

import com.main.LinkSphere_Backend.models.ClickEvent;
import com.main.LinkSphere_Backend.models.UrlMapping;
import com.main.LinkSphere_Backend.repo.ClickEventRepository;
import com.main.LinkSphere_Backend.repo.UrlMappingRepository;
import com.main.LinkSphere_Backend.security.BotDetectionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@AllArgsConstructor
public class RedirectController {

    private UrlMappingRepository urlMappingRepository;
    private ClickEventRepository clickEventRepository;
    private BotDetectionService botDetectionService;

    @GetMapping("/{shortUrl}")
    public ResponseEntity<?> redirect(@PathVariable String shortUrl, HttpServletRequest request) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping == null) {
            return ResponseEntity.notFound().build();
        }

        if (!urlMapping.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "This link has been disabled due to suspicious activity."));
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

        if (botDetectionService.isUnderBurstAttack(urlMapping)) {
            urlMapping.setActive(false);
        }

        urlMappingRepository.save(urlMapping);

        if (!urlMapping.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "This link has been disabled due to suspicious activity."));
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, urlMapping.getOriginalUrl())
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}