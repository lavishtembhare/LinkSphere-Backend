package com.main.LinkSphere_Backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.LinkSphere_Backend.exception.SafetyCheckUnavailableException;
import com.main.LinkSphere_Backend.exception.UnsafeUrlException;
import org.springframework.stereotype.Service;

@Service
public class UrlSafetyService {

    private final GroqService groqService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UrlSafetyService(GroqService groqService) {
        this.groqService = groqService;
    }

    public void assertSafe(String url) {
        String prompt = "Analyze this URL for signs of phishing, scams, or malicious intent "
                + "based on its structure, domain, and patterns (typosquatting, urgency "
                + "keywords, mismatched domains, suspicious TLD abuse). Respond with ONLY "
                + "valid JSON in this exact format: {\"suspicious\": true or false, "
                + "\"reason\": \"short explanation\"}\n\nURL: " + url;

        String response = groqService.generateText(prompt);
        if (response == null) {
            throw new SafetyCheckUnavailableException(
                    "Unable to verify this URL's safety right now. Please try again in a moment.");
        }

        try {
            JsonNode node = objectMapper.readTree(extractJson(response));
            boolean suspicious = node.path("suspicious").asBoolean(false);
            if (suspicious) {
                String reason = node.path("reason").asText("it matched patterns associated with phishing or scams");
                throw new UnsafeUrlException("This URL was flagged as potentially unsafe: " + reason);
            }
        } catch (UnsafeUrlException e) {
            throw e;
        } catch (Exception e) {
            throw new SafetyCheckUnavailableException(
                    "Unable to verify this URL's safety right now. Please try again in a moment.");
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1) throw new IllegalArgumentException("No JSON in response");
        return text.substring(start, end + 1);
    }
}