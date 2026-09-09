package com.linkforge.urlshortener.dto;

import java.time.LocalDateTime;

public class UrlResponse {

    private Long id;
    private String originalUrl;
    private String shortCode;
    private String shortUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastAccessedAt;
    private Long clickCount;
    private boolean expired;
    private boolean passwordProtected;

    public UrlResponse() {
    }

    public UrlResponse(Long id, String originalUrl, String shortCode, String shortUrl,
                        LocalDateTime createdAt, LocalDateTime expiresAt,
                        LocalDateTime lastAccessedAt, Long clickCount, boolean expired,
                        boolean passwordProtected) {
        this.id = id;
        this.originalUrl = originalUrl;
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastAccessedAt = lastAccessedAt;
        this.clickCount = clickCount;
        this.expired = expired;
        this.passwordProtected = passwordProtected;
    }

    public Long getId() {
        return id;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public Long getClickCount() {
        return clickCount;
    }

    public boolean isExpired() {
        return expired;
    }

    public boolean isPasswordProtected() {
        return passwordProtected;
    }
}
