package com.main.LinkSphere_Backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/health")
    public String getUrlAnalytics(){
        System.out.println("Server Running Properly");
        return "Server Running Properly";
    }
}
