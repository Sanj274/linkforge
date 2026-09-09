package com.linkforge.urlshortener.dto;

import java.time.LocalDate;

public class AnalyticsPoint {

    private LocalDate date;
    private long clicks;

    public AnalyticsPoint(LocalDate date, long clicks) {
        this.date = date;
        this.clicks = clicks;
    }

    public LocalDate getDate() {
        return date;
    }

    public long getClicks() {
        return clicks;
    }
}
