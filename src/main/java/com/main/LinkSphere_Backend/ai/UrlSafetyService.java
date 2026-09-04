package com.main.LinkSphere_Backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class UrlSafetyService {

    private final GroqService groqService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UrlSafetyService(GroqService groqService) {
        this.groqService = groqService;
    }

    public boolean isSuspicious(String url) {
        String prompt = "Analyze this URL for signs of phishing, scams, or malicious intent "
                + "based on its structure, domain, and patterns (typosquatting, urgency "
                + "keywords, mismatched domains, suspicious TLD abuse). Respond with ONLY "
                + "valid JSON in this exact format: {\"suspicious\": true or false, "
                + "\"reason\": \"short explanation\"}\n\nURL: " + url;

        String response = groqService.generateText(prompt);
        if (response == null) {
            System.err.println("Safety check failed for: " + url + " — allowing by default");
            return false;
        }

        try {
            JsonNode node = objectMapper.readTree(extractJson(response));
            return node.path("suspicious").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1) throw new IllegalArgumentException("No JSON in response");
        return text.substring(start, end + 1);
    }
}