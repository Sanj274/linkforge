package com.linkforge.urlshortener.dto;

public class UnlockResponse {

    private String originalUrl;

    public UnlockResponse(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }
}
