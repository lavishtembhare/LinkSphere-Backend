package com.main.LinkSphere_Backend.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class KeepAliveScheduler {

    private final WebClient webClient;

    @Value("${app.self-ping.url}")
    private String selfPingUrl;

    public KeepAliveScheduler(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void pingSelf() {
        if (selfPingUrl == null || selfPingUrl.isBlank()) {
            return;
        }
        try {
            webClient.get().uri(selfPingUrl).retrieve().toBodilessEntity().block();
            System.out.println("Server pinged: " + selfPingUrl);
        } catch (Exception e) {
            System.err.println("Self-ping failed: " + e.getMessage());
        }
    }
}