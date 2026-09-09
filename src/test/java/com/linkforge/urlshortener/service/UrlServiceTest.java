package com.linkforge.urlshortener.service;

import com.linkforge.urlshortener.dto.AnalyticsPoint;
import com.linkforge.urlshortener.dto.CreateUrlRequest;
import com.linkforge.urlshortener.dto.UrlResponse;
import com.linkforge.urlshortener.exception.DuplicateAliasException;
import com.linkforge.urlshortener.exception.InvalidPasswordException;
import com.linkforge.urlshortener.exception.InvalidRequestException;
import com.linkforge.urlshortener.exception.UrlExpiredException;
import com.linkforge.urlshortener.exception.UrlNotFoundException;
import com.linkforge.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UrlServiceTest {

    @Autowired
    private UrlService urlService;

    @Autowired
    private UrlMappingRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void createsShortUrlWithGeneratedCode() {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://example.com/very/long/path");

        UrlResponse response = urlService.createShortUrl(request);

        assertThat(response.getShortCode()).isNotBlank();
        assertThat(response.getShortUrl()).endsWith(response.getShortCode());
        assertThat(response.getClickCount()).isZero();
    }

    @Test
    void createsShortUrlWithCustomAlias() {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://example.com");
        request.setCustomAlias("my-alias");

        UrlResponse response = urlService.createShortUrl(request);

        assertThat(response.getShortCode()).isEqualTo("my-alias");
    }

    @Test
    void rejectsDuplicateAlias() {
        CreateUrlRequest first = new CreateUrlRequest();
        first.setOriginalUrl("https://example.com/one");
        first.setCustomAlias("taken");
        urlService.createShortUrl(first);

        CreateUrlRequest second = new CreateUrlRequest();
        second.setOriginalUrl("https://example.com/two");
        second.setCustomAlias("taken");

        assertThatThrownBy(() -> urlService.createShortUrl(second))
                .isInstanceOf(DuplicateAliasException.class);
    }

    @Test
    void rejectsExpiryInThePast() {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://example.com");
        request.setExpiresAt(LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> urlService.createShortUrl(request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void resolveAndTrackIncrementsClickCount() {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://example.com/track-me");
        UrlResponse created = urlService.createShortUrl(request);

        String resolved = urlService.resolveAndTrack(created.getShortCode());
        String resolvedAgain = urlService.resolveAndTrack(created.getShortCode());

        assertThat(resolved).isEqualTo("https://example.com/track-me");
        assertThat(resolvedAgain).isEqualTo("https://example.com/track-me");
        assertThat(urlService.getStats(created.getShortCode()).getClickCount()).isEqualTo(2L);
    }

    @Test
    void resolvingUnknownCodeThrows() {
        assertThatThrownBy(() -> urlService.resolveAndTrack("doesNotExist"))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void resolvingExpiredLinkThrows() {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://example.com/expiring");
        request.setExpiresAt(LocalDateTime.now().plusSeconds(1));
        UrlResponse created = urlService.createShortUrl(request);

        try {
            Thread.sleep(1200);
        } catch (InterruptedException ignored) {
        }

        assertThatThrownBy(() -> urlService.resolveAndTrack(created.getShortCode()))
                .isInstanceOf(UrlExpiredException.class);
    }

    @Test
    void deletesUrl() {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://example.com/to-delete");
        UrlResponse created = urlService.createShortUrl(request);

        urlService.deleteUrl(created.getShortCode());

        assertThatThrownBy(() -> urlService.getStats(created.getShortCode()))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void createsPasswordProtectedLink() {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://example.com/secret");
        request.setPassword("hunter2");

        UrlResponse created = urlService.createShortUrl(request);

        assertThat(created.isPasswordProtected()).isTrue();
    }

    @Test
    void unlockingWithCorrectPasswordReturnsOriginalUrl() {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://example.com/secret");
        request.setPassword("hunter2");
        UrlResponse created = urlService.createShortUrl(request);

        String resolved = urlService.unlockAndTrack(created.getShortCode(), "hunter2");

        assertThat(resolved).isEqualTo("https://example.com/secret");
        assertThat(urlService.getStats(created.getShortCode()).getClickCount()).isEqualTo(1L);
    }

    @Test
    void unlockingWithWrongPasswordThrows() {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://example.com/secret");
        request.setPassword("hunter2");
        UrlResponse created = urlService.createShortUrl(request);

        assertThatThrownBy(() -> urlService.unlockAndTrack(created.getShortCode(), "wrong-password"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void clicksOverTimeIncludesTodaysClick() {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setOriginalUrl("https://example.com/analytics");
        UrlResponse created = urlService.createShortUrl(request);

        urlService.resolveAndTrack(created.getShortCode());
        urlService.resolveAndTrack(created.getShortCode());

        long totalClicksInWindow = urlService.getClicksOverTime(created.getShortCode(), 7).stream()
                .mapToLong(AnalyticsPoint::getClicks)
                .sum();

        assertThat(totalClicksInWindow).isEqualTo(2L);
    }
}
