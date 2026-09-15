package com.main.LinkSphere_Backend.sevice;

import com.main.LinkSphere_Backend.ai.ClickSummaryService;
import com.main.LinkSphere_Backend.ai.LinkPreviewService;
import com.main.LinkSphere_Backend.ai.LinkSearchService;
import com.main.LinkSphere_Backend.ai.UrlSafetyService;
import com.main.LinkSphere_Backend.dto.ClickEventDTO;
import com.main.LinkSphere_Backend.dto.UrlMappingDTO;
import com.main.LinkSphere_Backend.models.ClickEvent;
import com.main.LinkSphere_Backend.models.UrlMapping;
import com.main.LinkSphere_Backend.models.User;
import com.main.LinkSphere_Backend.repo.ClickEventRepository;
import com.main.LinkSphere_Backend.repo.UrlMappingRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class UrlMappingService {
    private UrlMappingRepository urlMappingRepository;
    private ClickEventRepository clickEventRepository;
    private UrlSafetyService urlSafetyService;
    private LinkPreviewService linkPreviewService;
    private ClickSummaryService clickSummaryService;
    private LinkSearchService linkSearchService;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private final SecureRandom random = new SecureRandom();

    public UrlMappingDTO createShortUrl(String originalUrl, User user) {
        urlSafetyService.assertSafe(originalUrl);

        String shortUrl = generateShortUrl();

        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setOriginalUrl(originalUrl);
        urlMapping.setShortUrl(shortUrl);
        urlMapping.setUser(user);
        urlMapping.setCreatedDate(LocalDateTime.now());
        UrlMapping savedUrlMapping = urlMappingRepository.save(urlMapping);

        linkPreviewService.generateAndSavePreview(savedUrlMapping.getId());
        linkPreviewService.upgradeToSmartSlug(savedUrlMapping.getId(), originalUrl);

        return convertToDto(savedUrlMapping);
    }

    private String generateShortUrl() {
        StringBuilder shortUrl = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            shortUrl.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return shortUrl.toString();
    }

    private UrlMappingDTO convertToDto(UrlMapping urlMapping){
        UrlMappingDTO dto = new UrlMappingDTO();
        dto.setId(urlMapping.getId());
        dto.setOriginalUrl(urlMapping.getOriginalUrl());
        dto.setShortUrl(urlMapping.getShortUrl());
        dto.setClickCount(urlMapping.getClickCount());
        dto.setCreatedDate(urlMapping.getCreatedDate());
        dto.setUsername(urlMapping.getUser().getUsername());
        dto.setPreviewTitle(urlMapping.getPreviewTitle());
        dto.setPreviewDescription(urlMapping.getPreviewDescription());
        dto.setActive(urlMapping.isActive());
        dto.setDisabledReason(urlMapping.getDisabledReason());
        return dto;
    }

    public List<UrlMappingDTO> getUrlsByUser(User user) {
        return urlMappingRepository.findByUser(user).stream().map(this::convertToDto).toList();
    }

    public List<ClickEventDTO> getClickEventsByDate(String shortUrl, LocalDateTime start, LocalDateTime end) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
        if (urlMapping != null) {
            return clickEventRepository.findByUrlMappingAndClickDateBetween(urlMapping, start, end)
                    .stream()
                    .collect(Collectors.groupingBy(click -> click.getClickDate().toLocalDate(), Collectors.counting()))
                    .entrySet().stream()
                    .map(entry -> {
                        ClickEventDTO dto = new ClickEventDTO();
                        dto.setClickDate(entry.getKey());
                        dto.setCount(entry.getValue());
                        return dto;
                    })
                    .collect(Collectors.toList());
        }
        return null;
    }

    public Map<LocalDate, Long> getTotalClicksByUserAndDate(User user, LocalDate start, LocalDate end) {
        List<UrlMapping> urlMappings = urlMappingRepository.findByUser(user);
        List<ClickEvent> clickEvents = clickEventRepository.findByUrlMappingInAndClickDateBetween(
                urlMappings, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        return clickEvents.stream()
                .collect(Collectors.groupingBy(click -> click.getClickDate().toLocalDate(), Collectors.counting()));
    }

    public String getClickSummary(User user, LocalDate start, LocalDate end) {
        return clickSummaryService.summarize(getTotalClicksByUserAndDate(user, start, end));
    }

    public List<UrlMappingDTO> searchUrls(String query, User user) {
        List<UrlMapping> matches = linkSearchService.search(query, urlMappingRepository.findByUser(user));
        return matches.stream().map(this::convertToDto).toList();
    }

    public UrlMappingDTO setActiveStatus(String shortUrl, User user, boolean active) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrlAndUser(shortUrl, user).orElse(null);
        if (urlMapping == null) return null;
        urlMapping.setActive(active);
        if (active) {
            urlMapping.setDisabledReason(null);
        }
        return convertToDto(urlMappingRepository.save(urlMapping));
    }

    @Transactional
    public boolean deleteUrl(String shortUrl, User user) {
        UrlMapping urlMapping = urlMappingRepository.findByShortUrlAndUser(shortUrl, user).orElse(null);
        if (urlMapping == null) return false;
        clickEventRepository.deleteByUrlMapping(urlMapping);
        urlMappingRepository.delete(urlMapping);
        return true;
    }
}