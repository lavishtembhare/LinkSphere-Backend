package com.main.LinkSphere_Backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UrlMappingDTO {
    private Long id;
    private String originalUrl;
    private String shortUrl;
    private int clickCount;
    private LocalDateTime createdDate;
    private String username;
    private String previewTitle;
    private String previewDescription;
    private boolean active;
    private String disabledReason;
}