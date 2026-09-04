package com.main.LinkSphere_Backend.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class UrlMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String originalUrl;
    private String shortUrl;
    private int clickCount=0;
    private LocalDateTime createdDate;
    private String previewTitle;
    private String previewDescription;
    private boolean active = true;
    private String disabledReason;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}