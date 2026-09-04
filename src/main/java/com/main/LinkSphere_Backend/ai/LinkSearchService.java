package com.main.LinkSphere_Backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.LinkSphere_Backend.models.UrlMapping;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LinkSearchService {

    private final GroqService groqService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LinkSearchService(GroqService groqService) {
        this.groqService = groqService;
    }

    public List<UrlMapping> search(String query, List<UrlMapping> candidates) {
        if (candidates.isEmpty()) return List.of();

        String linkList = candidates.stream()
                .map(m -> m.getShortUrl() + " | " + m.getOriginalUrl()
                        + (m.getPreviewTitle() != null ? " | " + m.getPreviewTitle() : ""))
                .collect(Collectors.joining("\n"));

        String prompt = "Here is a user's shortened links, one per line, format "
                + "\"shortCode | originalUrl | title\":\n\n" + linkList
                + "\n\nSearch request: \"" + query + "\"\nReturn ONLY a JSON array of the "
                + "matching shortCode values, ordered by relevance. Empty array if nothing matches.";

        String response = groqService.generateText(prompt);
        if (response == null) return List.of();

        Set<String> matchedCodes = parseMatchedCodes(response);
        List<UrlMapping> results = new ArrayList<>();
        for (String code : matchedCodes) {
            candidates.stream().filter(m -> m.getShortUrl().equals(code)).findFirst().ifPresent(results::add);
        }
        return results;
    }

    private Set<String> parseMatchedCodes(String response) {
        try {
            String jsonArray = response.substring(response.indexOf('['), response.lastIndexOf(']') + 1);
            JsonNode arrayNode = objectMapper.readTree(jsonArray);
            Set<String> codes = new LinkedHashSet<>();
            arrayNode.forEach(node -> codes.add(node.asText()));
            return codes;
        } catch (Exception e) {
            return Set.of();
        }
    }
}