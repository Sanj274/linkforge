package com.linkforge.urlshortener.controller;

import com.google.zxing.WriterException;
import com.linkforge.urlshortener.dto.*;
import com.linkforge.urlshortener.service.UrlService;
import com.linkforge.urlshortener.util.QrCodeGenerator;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/urls")
public class UrlController {

    private final UrlService urlService;
    private final QrCodeGenerator qrCodeGenerator;

    public UrlController(UrlService urlService,
                          QrCodeGenerator qrCodeGenerator,
                          @Value("${app.base-url}") String baseUrl) {
        this.urlService = urlService;
        this.qrCodeGenerator = qrCodeGenerator;
    }

    @PostMapping
    public ResponseEntity<UrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request) {
        UrlResponse response = urlService.createShortUrl(request);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping
    public ResponseEntity<List<UrlResponse>> listUrls() {
        return ResponseEntity.ok(urlService.getAllUrls());
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<UrlResponse> getStats(@PathVariable String shortCode) {
        return ResponseEntity.ok(urlService.getStats(shortCode));
    }

    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode) {
        urlService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{shortCode}/unlock")
    public ResponseEntity<UnlockResponse> unlock(@PathVariable String shortCode,
                                                  @Valid @RequestBody UnlockRequest request) {
        String originalUrl = urlService.unlockAndTrack(shortCode, request.getPassword());
        return ResponseEntity.ok(new UnlockResponse(originalUrl));
    }

    @GetMapping(value = "/{shortCode}/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(@PathVariable String shortCode) throws WriterException, IOException {
        UrlResponse stats = urlService.getStats(shortCode);
        byte[] png = qrCodeGenerator.generatePng(stats.getShortUrl());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    @GetMapping("/{shortCode}/analytics")
    public ResponseEntity<List<AnalyticsPoint>> getAnalytics(@PathVariable String shortCode,
                                                               @RequestParam(defaultValue = "14") int days) {
        int clamped = Math.max(1, Math.min(days, 90));
        return ResponseEntity.ok(urlService.getClicksOverTime(shortCode, clamped));
    }

    @GetMapping("/analytics/summary")
    public ResponseEntity<List<AnalyticsPoint>> getGlobalAnalytics(@RequestParam(defaultValue = "14") int days) {
        int clamped = Math.max(1, Math.min(days, 90));
        return ResponseEntity.ok(urlService.getGlobalClicksOverTime(clamped));
    }

    @GetMapping("/stats/summary")
    public ResponseEntity<Map<String, Long>> summary() {
        Map<String, Long> summary = new HashMap<>();
        summary.put("totalUrls", urlService.getTotalUrlCount());
        summary.put("totalClicks", urlService.getTotalClickCount());
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv() {
        byte[] csv = urlService.exportToCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"linkforge-export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResult> importCsv(@RequestParam("file") MultipartFile file) {
        ImportResult result = urlService.importFromCsv(file);
        return ResponseEntity.ok(result);
    }
}
