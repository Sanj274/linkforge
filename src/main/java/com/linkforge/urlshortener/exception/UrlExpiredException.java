package com.linkforge.urlshortener.exception;

public class UrlExpiredException extends RuntimeException {
    public UrlExpiredException(String shortCode) {
        super("The short link '" + shortCode + "' has expired");
    }
}
