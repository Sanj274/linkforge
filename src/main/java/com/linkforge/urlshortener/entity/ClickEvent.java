package com.linkforge.urlshortener.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One row per redirect. Kept separate from UrlMapping.clickCount (which is a
 * fast running total) so we can chart click volume over time without
 * recomputing it from scratch on every dashboard load.
 */
@Entity
@Table(name = "click_event", indexes = {
        @Index(name = "idx_click_event_mapping_id", columnList = "url_mapping_id"),
        @Index(name = "idx_click_event_clicked_at", columnList = "clickedAt")
})
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_mapping_id", nullable = false)
    private Long urlMappingId;

    @Column(nullable = false)
    private LocalDateTime clickedAt;

    public ClickEvent() {
    }

    public ClickEvent(Long urlMappingId, LocalDateTime clickedAt) {
        this.urlMappingId = urlMappingId;
        this.clickedAt = clickedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUrlMappingId() {
        return urlMappingId;
    }

    public void setUrlMappingId(Long urlMappingId) {
        this.urlMappingId = urlMappingId;
    }

    public LocalDateTime getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(LocalDateTime clickedAt) {
        this.clickedAt = clickedAt;
    }
}
