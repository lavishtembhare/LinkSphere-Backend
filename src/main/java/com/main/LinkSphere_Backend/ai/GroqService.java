package com.main.LinkSphere_Backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${groq.api.url}")
    private String groqApiUrl;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.model:openai/gpt-oss-120b}")
    private String groqModel;

    public GroqService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @PostConstruct
    public void logConfig() {
        System.out.println("Groq API URL configured as: " + groqApiUrl);
        System.out.println("Groq model configured as: " + groqModel);
    }

    public String generateText(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", groqModel,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "reasoning_effort", "low"
        );

        try {
            String rawResponse = webClient.post()
                    .uri(groqApiUrl)
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .retryWhen(Retry.backoff(1, Duration.ofMillis(500)).filter(this::isRetryable))
                    .block();

            return extractGroqText(rawResponse);
        } catch (WebClientResponseException e) {
            System.err.println("Groq API call failed: HTTP " + e.getStatusCode()
                    + " calling " + groqApiUrl + " — response body: " + e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            System.err.println("Groq API call failed: " + e.getMessage());
            return null;
        }
    }

    private boolean isRetryable(Throwable throwable) {
        String message = throwable.getMessage();
        return message != null && (
                message.contains("429") || message.contains("503") ||
                        message.toLowerCase().contains("timeout")
        );
    }

    private String extractGroqText(String rawResponse) {
        if (rawResponse == null) return null;
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode textNode = root.path("choices").get(0).path("message").path("content");
            return textNode.isMissingNode() ? null : textNode.asText().trim();
        } catch (Exception e) {
            System.err.println("Failed to parse Groq response: " + e.getMessage());
            return null;
        }
    }
}