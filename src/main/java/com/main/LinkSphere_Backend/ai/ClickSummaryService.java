package com.main.LinkSphere_Backend.ai;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ClickSummaryService {

    private final GroqService groqService;

    public ClickSummaryService(GroqService groqService) {
        this.groqService = groqService;
    }

    public String summarize(Map<LocalDate, Long> clicksByDate) {
        if (clicksByDate == null || clicksByDate.isEmpty()) {
            return "No click activity in this period yet.";
        }

        String dataAsText = clicksByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ": " + e.getValue() + " clicks")
                .collect(Collectors.joining("\n"));

        String prompt = "Here is daily click data for a user's shortened links. Write a "
                + "brief natural-language summary (2-3 sentences) highlighting the overall "
                + "trend and any notable peak days. Don't just restate the numbers.\n\n" + dataAsText;

        String summary = groqService.generateText(prompt);
        return summary != null ? summary : "Summary unavailable right now — here's your raw data instead.";
    }
}