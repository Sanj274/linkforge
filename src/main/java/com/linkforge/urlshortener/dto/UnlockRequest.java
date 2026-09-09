package com.linkforge.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;

public class UnlockRequest {

    @NotBlank(message = "Password is required")
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
