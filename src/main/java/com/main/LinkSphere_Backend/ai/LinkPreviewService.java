package com.main.LinkSphere_Backend.ai;

import com.main.LinkSphere_Backend.models.UrlMapping;
import com.main.LinkSphere_Backend.repo.UrlMappingRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;

@Service
public class LinkPreviewService {

    private final GroqService groqService;
    private final UrlMappingRepository urlMappingRepository;

    public LinkPreviewService(GroqService groqService, UrlMappingRepository urlMappingRepository) {
        this.groqService = groqService;
        this.urlMappingRepository = urlMappingRepository;
    }

    public record LinkPreview(String title, String description) {}

    @Async
    public void generateAndSavePreview(Long urlMappingId) {
        UrlMapping mapping = urlMappingRepository.findById(urlMappingId).orElse(null);
        if (mapping == null) return;

        LinkPreview preview = fetchPreview(mapping.getOriginalUrl());
        mapping.setPreviewTitle(preview.title());
        mapping.setPreviewDescription(preview.description());
        urlMappingRepository.save(mapping);
    }

    @Async
    public void upgradeToSmartSlug(Long urlMappingId, String originalUrl) {
        String prompt = "Generate a short, memorable, URL-safe slug (2-4 words, lowercase, "
                + "hyphen-separated, no special characters) that represents the destination "
                + "of this URL. Respond with ONLY the slug, nothing else.\n\nURL: " + originalUrl;

        String raw = groqService.generateText(prompt);
        if (raw == null || raw.isBlank()) return;

        String cleaned = raw.trim().toLowerCase().replaceAll("[^a-z0-9-]", "").replaceAll("-{2,}", "-");
        if (cleaned.isBlank() || cleaned.length() > 30) return;

        UrlMapping mapping = urlMappingRepository.findById(urlMappingId).orElse(null);
        if (mapping == null) return;

        if (urlMappingRepository.findByShortUrl(cleaned) == null) {
            mapping.setShortUrl(cleaned);
            urlMappingRepository.save(mapping);
        }
    }

    public LinkPreview fetchPreview(String url) {
        if (!isSafeToFetch(url)) {
            return new LinkPreview(null, null);
        }
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(5000)
                    .maxBodySize(2 * 1024 * 1024)
                    .followRedirects(true)
                    .userAgent("Mozilla/5.0 (compatible; LinkSphereBot/1.0)")
                    .get();

            String title = doc.title();
            String description = doc.select("meta[name=description]").attr("content");

            if (description.isBlank()) {
                description = summarizeWithGroq(doc.body() != null ? doc.body().text() : "", title);
            }

            return new LinkPreview(
                    title.isBlank() ? null : truncate(title, 100),
                    description.isBlank() ? null : truncate(description, 200)
            );
        } catch (Exception e) {
            System.err.println("Preview fetch failed for " + url + ": " + e.getMessage());
            return new LinkPreview(null, null);
        }
    }

    private String summarizeWithGroq(String pageText, String title) {
        if (pageText.isBlank()) return "";
        String prompt = "Summarize what this webpage is about in one concise sentence "
                + "(under 25 words). Respond with ONLY the sentence.\n\nTitle: " + title
                + "\nContent: " + truncate(pageText, 2000);
        String result = groqService.generateText(prompt);
        return result == null ? "" : result;
    }

    private String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    private boolean isSafeToFetch(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            InetAddress address = InetAddress.getByName(uri.getHost());
            return !(address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isAnyLocalAddress());
        } catch (Exception e) {
            return false;
        }
    }
}