package com.main.LinkSphere_Backend.controller;

import com.main.LinkSphere_Backend.dto.ClickEventDTO;
import com.main.LinkSphere_Backend.dto.UrlMappingDTO;
import com.main.LinkSphere_Backend.models.User;
import com.main.LinkSphere_Backend.sevice.UrlMappingService;
import com.main.LinkSphere_Backend.sevice.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/urls")
@AllArgsConstructor
public class UrlMappingController {
    private UrlMappingService urlMappingService;
    private UserService userService;

    @PostMapping("/shorten")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UrlMappingDTO> createShortUrl(@RequestBody Map<String,String> request, Principal principal){
        String originalUrl=request.get("originalUrl");
        User user=userService.findByUsername(principal.getName());
        return ResponseEntity.ok(urlMappingService.createShortUrl(originalUrl, user));
    }

    @GetMapping("/myurls")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<UrlMappingDTO>> getUserUrls(Principal principal){
        User user=userService.findByUsername(principal.getName());
        return ResponseEntity.ok(urlMappingService.getUrlsByUser(user));
    }

    @GetMapping("/analytics/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ClickEventDTO>> getUrlAnalytics(@PathVariable String shortUrl, @RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate){
        LocalDateTime start=LocalDateTime.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        LocalDateTime end=LocalDateTime.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        List<ClickEventDTO> result = urlMappingService.getClickEventsByDate(shortUrl, start, end);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/totalClicks")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<LocalDate, Long>> getTotalClicksByDate(Principal principal, @RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate){
        User user = userService.findByUsername(principal.getName());
        LocalDate start=LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate end=LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE);
        return ResponseEntity.ok(urlMappingService.getTotalClicksByUserAndDate(user, start, end));
    }

    @GetMapping("/totalClicks/summary")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, String>> getTotalClicksSummary(Principal principal, @RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate){
        User user = userService.findByUsername(principal.getName());
        LocalDate start=LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate end=LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE);
        return ResponseEntity.ok(Map.of("summary", urlMappingService.getClickSummary(user, start, end)));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<UrlMappingDTO>> searchUrls(@RequestParam String query, Principal principal){
        User user = userService.findByUsername(principal.getName());
        return ResponseEntity.ok(urlMappingService.searchUrls(query, user));
    }

    @PatchMapping("/{shortUrl}/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UrlMappingDTO> setLinkStatus(@PathVariable String shortUrl, @RequestBody Map<String, Boolean> request, Principal principal){
        Boolean active = request.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        User user = userService.findByUsername(principal.getName());
        UrlMappingDTO updated = urlMappingService.setActiveStatus(shortUrl, user, active);
        return updated == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{shortUrl}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortUrl, Principal principal){
        User user = userService.findByUsername(principal.getName());
        boolean deleted = urlMappingService.deleteUrl(shortUrl, user);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}