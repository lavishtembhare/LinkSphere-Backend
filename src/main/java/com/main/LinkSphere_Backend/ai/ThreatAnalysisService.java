package com.main.LinkSphere_Backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.LinkSphere_Backend.models.ClickEvent;
import com.main.LinkSphere_Backend.models.UrlMapping;
import com.main.LinkSphere_Backend.repo.ClickEventRepository;
import com.main.LinkSphere_Backend.repo.UrlMappingRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ThreatAnalysisService {

    private final GroqService groqService;
    private final ClickEventRepository clickEventRepository;
    private final UrlMappingRepository urlMappingRepository;

    public ThreatAnalysisService(GroqService groqService, ClickEventRepository clickEventRepository,
                                 UrlMappingRepository urlMappingRepository) {
        this.groqService = groqService;
        this.clickEventRepository = clickEventRepository;
        this.urlMappingRepository = urlMappingRepository;
    }

    /** Fires once, right after a burst trips the link off — not per click. */
    @Async
    public void analyzeAndExplain(Long urlMappingId) {
        UrlMapping mapping = urlMappingRepository.findById(urlMappingId).orElse(null);
        if (mapping == null) return;

        LocalDateTime windowStart = LocalDateTime.now().minusSeconds(60);
        List<ClickEvent> recentClicks = clickEventRepository
                .findByUrlMappingAndClickDateBetween(mapping, windowStart, LocalDateTime.now());
        if (recentClicks.isEmpty()) return;

        Set<String> distinctIps = recentClicks.stream()
                .map(ClickEvent::getIpAddress).filter(ip -> ip != null).collect(Collectors.toSet());
        Set<String> distinctUserAgents = recentClicks.stream()
                .map(ClickEvent::getUserAgent).filter(ua -> ua != null).collect(Collectors.toSet());

        String prompt = "A short link received " + recentClicks.size() + " clicks in the last 60 "
                + "seconds, from " + distinctIps.size() + " distinct IP address(es) and "
                + distinctUserAgents.size() + " distinct User-Agent string(s). Classify this as "
                + "either a likely automated attack (low IP/UA diversity relative to click count "
                + "suggests scripted requests) or a likely legitimate traffic spike (high diversity "
                + "suggests real, distinct visitors). Respond with ONLY valid JSON: "
                + "{\"verdict\": \"likely attack\" or \"likely legitimate\", \"explanation\": \"one short sentence\"}";

        String response = groqService.generateText(prompt);
        mapping.setDisabledReason(extractExplanation(response, recentClicks.size(), distinctIps.size()));
        urlMappingRepository.save(mapping);
    }

    private String extractExplanation(String response, int clickCount, int ipCount) {
        if (response == null) {
            return "Auto-disabled: " + clickCount + " clicks in 60s from " + ipCount + " IP(s). AI analysis unavailable.";
        }
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            JsonNode node = new ObjectMapper().readTree(response.substring(start, end + 1));
            return "Auto-disabled (" + node.path("verdict").asText("unknown") + "): "
                    + node.path("explanation").asText("");
        } catch (Exception e) {
            return "Auto-disabled: " + clickCount + " clicks in 60s from " + ipCount + " IP(s).";
        }
    }
}