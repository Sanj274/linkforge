package com.linkforge.urlshortener.service;

import com.linkforge.urlshortener.dto.AnalyticsPoint;
import com.linkforge.urlshortener.dto.CreateUrlRequest;
import com.linkforge.urlshortener.dto.ImportResult;
import com.linkforge.urlshortener.dto.UrlResponse;
import com.linkforge.urlshortener.entity.ClickEvent;
import com.linkforge.urlshortener.entity.UrlMapping;
import com.linkforge.urlshortener.exception.DuplicateAliasException;
import com.linkforge.urlshortener.exception.InvalidPasswordException;
import com.linkforge.urlshortener.exception.InvalidRequestException;
import com.linkforge.urlshortener.exception.UrlExpiredException;
import com.linkforge.urlshortener.exception.UrlNotFoundException;
import com.linkforge.urlshortener.repository.ClickEventRepository;
import com.linkforge.urlshortener.repository.UrlMappingRepository;
import com.linkforge.urlshortener.util.Base62Encoder;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UrlService {

    private static final int SHORT_CODE_LENGTH = 7;
    private static final int MAX_GENERATION_ATTEMPTS = 8;

    private final UrlMappingRepository repository;
    private final ClickEventRepository clickEventRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();
    private final String baseUrl;

    public UrlService(UrlMappingRepository repository,
                       ClickEventRepository clickEventRepository,
                       @Value("${app.base-url}") String baseUrl) {
        this.repository = repository;
        this.clickEventRepository = clickEventRepository;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Transactional
    public UrlResponse createShortUrl(CreateUrlRequest request) {
        UrlMapping saved = buildAndSave(request);
        return toResponse(saved);
    }

    private UrlMapping buildAndSave(CreateUrlRequest request) {
        String originalUrl = request.getOriginalUrl().trim();

        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRequestException("Expiration date must be in the future");
        }

        String shortCode;
        boolean isCustom = false;

        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            shortCode = request.getCustomAlias().trim();
            if (repository.existsByShortCode(shortCode)) {
                throw new DuplicateAliasException(shortCode);
            }
            isCustom = true;
        } else {
            shortCode = generateUniqueShortCode();
        }

        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(originalUrl);
        mapping.setShortCode(shortCode);
        mapping.setCustomAlias(isCustom);
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setExpiresAt(request.getExpiresAt());
        mapping.setClickCount(0L);

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            mapping.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        return repository.save(mapping);
    }

    /**
     * Resolves a short code for redirect. Throws if the link is unknown, expired,
     * or requires a password (callers should check {@link #getRawByShortCode}
     * first when they need to branch on password-protection before tracking a click).
     */
    @Transactional
    public String resolveAndTrack(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (mapping.isExpired()) {
            throw new UrlExpiredException(shortCode);
        }

        recordClick(mapping);
        return mapping.getOriginalUrl();
    }

    @Transactional
    public String unlockAndTrack(String shortCode, String password) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (mapping.isExpired()) {
            throw new UrlExpiredException(shortCode);
        }
        if (mapping.isPasswordProtected() && !passwordEncoder.matches(password, mapping.getPasswordHash())) {
            throw new InvalidPasswordException();
        }

        recordClick(mapping);
        return mapping.getOriginalUrl();
    }

    private void recordClick(UrlMapping mapping) {
        LocalDateTime now = LocalDateTime.now();
        mapping.setClickCount(mapping.getClickCount() + 1);
        mapping.setLastAccessedAt(now);
        repository.save(mapping);
        clickEventRepository.save(new ClickEvent(mapping.getId(), now));
    }

    @Transactional(readOnly = true)
    public UrlResponse getStats(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        return toResponse(mapping);
    }

    @Transactional(readOnly = true)
    public UrlMapping getRawByShortCode(String shortCode) {
        return repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
    }

    @Transactional(readOnly = true)
    public List<UrlResponse> getAllUrls() {
        return repository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteUrl(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        clickEventRepository.deleteByUrlMappingId(mapping.getId());
        repository.delete(mapping);
    }

    @Transactional(readOnly = true)
    public long getTotalUrlCount() {
        return repository.count();
    }

    @Transactional(readOnly = true)
    public long getTotalClickCount() {
        return repository.findAll().stream()
                .mapToLong(UrlMapping::getClickCount)
                .sum();
    }

    // ---------- analytics ----------

    @Transactional(readOnly = true)
    public List<AnalyticsPoint> getClicksOverTime(String shortCode, int days) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        LocalDateTime since = LocalDateTime.now().minusDays(days - 1L).toLocalDate().atStartOfDay();
        List<ClickEvent> events = clickEventRepository.findByUrlMappingIdAndClickedAtAfter(mapping.getId(), since);
        return bucketByDay(events, days);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsPoint> getGlobalClicksOverTime(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days - 1L).toLocalDate().atStartOfDay();
        List<ClickEvent> events = clickEventRepository.findByClickedAtAfter(since);
        return bucketByDay(events, days);
    }

    private List<AnalyticsPoint> bucketByDay(List<ClickEvent> events, int days) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            counts.put(start.plusDays(i), 0L);
        }
        for (ClickEvent event : events) {
            LocalDate day = event.getClickedAt().toLocalDate();
            counts.merge(day, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(e -> new AnalyticsPoint(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    // ---------- CSV import / export ----------

    @Transactional(readOnly = true)
    public byte[] exportToCsv() {
        List<UrlMapping> mappings = repository.findAll();
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("shortCode", "originalUrl", "shortUrl", "createdAt", "expiresAt",
                        "clickCount", "passwordProtected")
                .build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (UrlMapping m : mappings) {
                printer.printRecord(
                        m.getShortCode(),
                        m.getOriginalUrl(),
                        baseUrl + "/" + m.getShortCode(),
                        m.getCreatedAt(),
                        m.getExpiresAt() == null ? "" : m.getExpiresAt(),
                        m.getClickCount(),
                        m.isPasswordProtected()
                );
            }
        } catch (IOException e) {
            throw new InvalidRequestException("Failed to generate CSV export");
        }
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public ImportResult importFromCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Please choose a CSV file to import");
        }

        ImportResult result = new ImportResult();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = new CSVParser(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8), format)) {
            int rowNumber = 1;
            for (CSVRecord record : parser) {
                rowNumber++;
                try {
                    String originalUrl = getField(record, "originalUrl", "url", "originalurl");
                    if (originalUrl == null || originalUrl.isBlank()) {
                        result.addError(rowNumber, "Missing originalUrl column");
                        result.incrementSkipped();
                        continue;
                    }
                    if (!originalUrl.matches("^(https?://)[\\w.-]+(:\\d+)?([/?#].*)?$")) {
                        result.addError(rowNumber, "Invalid URL: " + originalUrl);
                        result.incrementSkipped();
                        continue;
                    }

                    CreateUrlRequest request = new CreateUrlRequest();
                    request.setOriginalUrl(originalUrl);

                    String alias = getField(record, "customAlias", "alias", "customalias");
                    if (alias != null && !alias.isBlank()) {
                        request.setCustomAlias(alias);
                    }

                    buildAndSave(request);
                    result.incrementCreated();
                } catch (DuplicateAliasException | InvalidRequestException e) {
                    result.addError(rowNumber, e.getMessage());
                    result.incrementSkipped();
                } catch (Exception e) {
                    result.addError(rowNumber, "Unexpected error: " + e.getMessage());
                    result.incrementSkipped();
                }
            }
        } catch (IOException e) {
            throw new InvalidRequestException("Could not read the uploaded CSV file");
        }

        return result;
    }

    private String getField(CSVRecord record, String... possibleHeaders) {
        for (String header : possibleHeaders) {
            if (record.isMapped(header)) {
                String value = record.get(header);
                if (value != null) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    // ---------- code generation ----------

    private String generateUniqueShortCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            long randomSeed = secureRandom.nextLong() & Long.MAX_VALUE;
            String encoded = Base62Encoder.encode(randomSeed);
            String code = encoded.length() >= SHORT_CODE_LENGTH
                    ? encoded.substring(0, SHORT_CODE_LENGTH)
                    : String.format("%1$" + SHORT_CODE_LENGTH + "s", encoded).replace(' ', '0');
            if (!repository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new InvalidRequestException("Could not generate a unique short code, please try again");
    }

    private UrlResponse toResponse(UrlMapping mapping) {
        String shortUrl = baseUrl + "/" + mapping.getShortCode();
        return new UrlResponse(
                mapping.getId(),
                mapping.getOriginalUrl(),
                mapping.getShortCode(),
                shortUrl,
                mapping.getCreatedAt(),
                mapping.getExpiresAt(),
                mapping.getLastAccessedAt(),
                mapping.getClickCount(),
                mapping.isExpired(),
                mapping.isPasswordProtected()
        );
    }
}
