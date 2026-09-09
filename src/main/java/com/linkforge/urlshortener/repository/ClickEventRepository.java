package com.linkforge.urlshortener.repository;

import com.linkforge.urlshortener.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    List<ClickEvent> findByUrlMappingIdAndClickedAtAfter(Long urlMappingId, LocalDateTime after);

    List<ClickEvent> findByClickedAtAfter(LocalDateTime after);

    void deleteByUrlMappingId(Long urlMappingId);
}
