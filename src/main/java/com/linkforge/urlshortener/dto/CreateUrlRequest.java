package com.linkforge.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

public class CreateUrlRequest {

    @NotBlank(message = "Original URL is required")
    @Pattern(
            regexp = "^(https?://)[\\w.-]+(:\\d+)?([/?#].*)?$",
            message = "Please provide a valid URL starting with http:// or https://"
    )
    private String originalUrl;

    @Pattern(
            regexp = "^$|^[a-zA-Z0-9_-]{3,20}$",
            message = "Custom alias must be 3-20 characters (letters, numbers, hyphen, underscore)"
    )
    private String customAlias;

    @Pattern(
            regexp = "^$|.{4,72}$",
            message = "Password must be at least 4 characters"
    )
    private String password;

    private LocalDateTime expiresAt;

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getCustomAlias() {
        return customAlias;
    }

    public void setCustomAlias(String customAlias) {
        this.customAlias = customAlias;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
