package com.webdoctor.backend.history.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webdoctor.backend.auth.entity.AppUser;
import com.webdoctor.backend.history.dto.ScanHistoryDetail;
import com.webdoctor.backend.history.dto.ScanHistorySummary;
import com.webdoctor.backend.history.entity.ScanHistory;
import com.webdoctor.backend.history.repository.ScanHistoryRepository;
import com.webdoctor.backend.scanner.dto.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScanHistoryServiceTest {

    private ScanHistoryRepository repository;
    private ObjectMapper objectMapper;
    private ScanHistoryService service;

    private AppUser user;

    @BeforeEach
    void setUp() {

        repository = mock(ScanHistoryRepository.class);
        objectMapper = new ObjectMapper();

        service = new ScanHistoryService(repository, objectMapper);

        user = new AppUser(
                "Test User",
                "test@example.com",
                "password-hash"
        );
    }

    @Test
    void shouldSaveScanAndCalculateHealthScore() {

        ScanResponse response = createScanResponse();

        when(repository.save(any(ScanHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScanHistorySummary result =
                service.saveScan(response, user);

        assertEquals("https://example.com", result.url());
        assertTrue(result.reachable());
        assertEquals(200, result.statusCode());

        // Average: (80 + 70 + 90 + 60) / 4 = 75
        assertEquals(75, result.healthScore());

        verify(repository, times(1)).save(any(ScanHistory.class));
    }

    @Test
    void shouldBackfillMissingHealthScoreInRecentHistory() throws Exception {

        ScanResponse response = createScanResponse();

        String responseJson =
                objectMapper.writeValueAsString(response);

        ScanHistory history = new ScanHistory(
                response.url(),
                response.reachable(),
                response.statusCode(),
                response.responseTimeMs(),
                LocalDateTime.now(),
                responseJson,
                user
        );

        when(repository.findTop50ByUserOrderByScannedAtDesc(user))
                .thenReturn(List.of(history));

        List<ScanHistorySummary> result =
                service.getRecentHistory(user);

        assertEquals(1, result.size());
        assertEquals(75, result.get(0).healthScore());

        // Verify score was backfilled.
        assertEquals(75, history.getHealthScore());
    }

    @Test
    void shouldRetrieveScanDetailsForAuthenticatedUser() throws Exception {

        ScanResponse response = createScanResponse();

        String responseJson =
                objectMapper.writeValueAsString(response);

        ScanHistory history = new ScanHistory(
                response.url(),
                response.reachable(),
                response.statusCode(),
                response.responseTimeMs(),
                LocalDateTime.now(),
                responseJson,
                user
        );

        when(repository.findByIdAndUser(1L, user))
                .thenReturn(Optional.of(history));

        Optional<ScanHistoryDetail> result =
                service.getScanById(1L, user);

        assertTrue(result.isPresent());
        assertEquals("https://example.com",
                result.get().scanResponse().url());

        verify(repository).findByIdAndUser(1L, user);
    }

    @Test
    void shouldDeleteScanOnlyWhenItBelongsToAuthenticatedUser() {

        ScanHistory history = new ScanHistory(
                "https://example.com",
                true,
                200,
                500,
                LocalDateTime.now(),
                "{}",
                user
        );

        when(repository.findByIdAndUser(1L, user))
                .thenReturn(Optional.of(history));

        boolean deleted = service.deleteScan(1L, user);

        assertTrue(deleted);

        verify(repository).delete(history);
    }

    @Test
    void shouldNotDeleteScanWhenItDoesNotBelongToAuthenticatedUser() {

        when(repository.findByIdAndUser(2L, user))
                .thenReturn(Optional.empty());

        boolean deleted = service.deleteScan(2L, user);

        assertFalse(deleted);

        verify(repository, never()).delete(any(ScanHistory.class));
    }

    private ScanResponse createScanResponse() {

        PerformanceAnalysisResponse performance =
                new PerformanceAnalysisResponse(
                        80, "A", 500, 100000,
                        "text/html", "gzip", List.of(), "OK"
                );

        SecurityAnalysisResponse security =
                new SecurityAnalysisResponse(
                        70, "B", List.of(), "OK"
                );

        SeoAnalysisResponse seo =
                new SeoAnalysisResponse(
                        90, "A", "Example", "Description",
                        "Heading", 5, 0, List.of(), "OK"
                );

        MobileAnalysisResponse mobile =
                new MobileAnalysisResponse(
                        60, "D", true, true,
                        5, 0, 0, 0, List.of(), "OK"
                );

        return new ScanResponse(
                "https://example.com",
                true,
                200,
                500,
                "Scan completed",
                null,
                security,
                performance,
                seo,
                null,
                null,
                mobile,
                null,
                null
        );
    }
}
