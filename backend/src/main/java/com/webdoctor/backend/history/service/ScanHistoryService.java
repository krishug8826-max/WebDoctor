package com.webdoctor.backend.history.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.webdoctor.backend.auth.entity.AppUser;
import com.webdoctor.backend.history.dto.ScanHistoryDetail;
import com.webdoctor.backend.history.dto.ScanHistorySummary;
import com.webdoctor.backend.history.entity.ScanHistory;
import com.webdoctor.backend.history.repository.ScanHistoryRepository;
import com.webdoctor.backend.scanner.dto.ScanResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.webdoctor.backend.history.dto.ScanHistoryPageResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
@Service
public class ScanHistoryService {

    private final ScanHistoryRepository repository;
    private final ObjectMapper objectMapper;

    public ScanHistoryService(
            ScanHistoryRepository repository,
            ObjectMapper objectMapper) {

        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScanHistorySummary saveScan(ScanResponse response, AppUser user) {

        try {

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

            // Calculate and persist Health Score for new scans.
            history.setHealthScore(calculateHealthScore(response));

            ScanHistory saved = repository.save(history);

            return toSummary(saved);

        } catch (JsonProcessingException e) {

            throw new IllegalStateException(
                    "Could not serialize scan result", e
            );
        }
    }

    @Transactional
    public List<ScanHistorySummary> getRecentHistory(AppUser user) {

        List<ScanHistory> historyRecords =
                repository.findTop50ByUserOrderByScannedAtDesc(user);

        List<ScanHistorySummary> summaries = new ArrayList<>();

        for (ScanHistory history : historyRecords) {

            // Backfill Health Scores for existing authenticated records.
            if (history.getHealthScore() == null) {

                ScanResponse response = readScanResponse(history);

                history.setHealthScore(
                        calculateHealthScore(response)
                );
            }

            summaries.add(toSummary(history));
        }

        return summaries;
    }
    @Transactional
public ScanHistoryPageResponse getHistoryPage(
        AppUser user,
        int page,
        int size,
        String search,
        String status,
        String dateRange,
        String sortBy,
        String sortDirection) {

    // Validate pagination
    page = Math.max(page, 0);

    if (size != 10 && size != 20 && size != 50) {
        size = 10;
    }

    // URL search
    String searchTerm = (search == null || search.isBlank())
            ? null
            : search.trim();

    // Status filter
    Boolean reachable = null;

    if ("successful".equalsIgnoreCase(status)) {
        reachable = true;
    } else if ("failed".equalsIgnoreCase(status)) {
        reachable = false;
    }

    // Date filters
    LocalDateTime fromDate = null;

    if ("today".equalsIgnoreCase(dateRange)) {
        fromDate = LocalDate.now().atStartOfDay();

    } else if ("7days".equalsIgnoreCase(dateRange)) {
        fromDate = LocalDate.now()
                .minusDays(6)
                .atStartOfDay();

    } else if ("30days".equalsIgnoreCase(dateRange)) {
        fromDate = LocalDate.now()
                .minusDays(29)
                .atStartOfDay();
    }

    // Whitelisted sorting
    String sortProperty;

    if ("healthScore".equalsIgnoreCase(sortBy)) {
        sortProperty = "healthScore";
    } else {
        sortProperty = "scannedAt";
    }

    Sort.Direction direction =
            "asc".equalsIgnoreCase(sortDirection)
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;

    Sort sorting = Sort.by(direction, sortProperty)
            .and(Sort.by(Sort.Direction.DESC, "id"));

    Pageable pageable = PageRequest.of(page, size, sorting);

    Page<ScanHistory> historyPage =
            repository.findHistoryPage(
                    user,
                    searchTerm,
                    reachable,
                    fromDate,
                    null,
                    pageable
            );

    // Backfill missing scores only for the authenticated user's records.
    for (ScanHistory history : historyPage.getContent()) {

        if (history.getHealthScore() == null) {

            ScanResponse response = readScanResponse(history);

            history.setHealthScore(
                    calculateHealthScore(response)
            );
        }
    }

    List<ScanHistorySummary> content =
            historyPage.getContent()
                    .stream()
                    .map(this::toSummary)
                    .toList();

    return new ScanHistoryPageResponse(
            content,
            historyPage.getNumber(),
            historyPage.getSize(),
            historyPage.getTotalElements(),
            historyPage.getTotalPages(),
            historyPage.hasNext(),
            historyPage.hasPrevious()
    );
}

    @Transactional(readOnly = true)
    public Optional<ScanHistoryDetail> getScanById(Long id, AppUser user) {

        return repository.findByIdAndUser(id, user)
                .map(history -> {

                    ScanResponse response = readScanResponse(history);

                    return new ScanHistoryDetail(
                            history.getId(),
                            history.getScannedAt(),
                            response
                    );
                });
    }

    @Transactional
    public boolean deleteScan(Long id, AppUser user) {

        Optional<ScanHistory> history =
                repository.findByIdAndUser(id, user);

        if (history.isEmpty()) {
            return false;
        }

        repository.delete(history.get());

        return true;
    }

    private ScanHistorySummary toSummary(ScanHistory history) {

        return new ScanHistorySummary(
                history.getId(),
                history.getUrl(),
                history.isReachable(),
                history.getStatusCode(),
                history.getResponseTimeMs(),
                history.getScannedAt(),
                history.getHealthScore()
        );
    }

    private ScanResponse readScanResponse(ScanHistory history) {

        try {

            return objectMapper.readValue(
                    history.getResponseJson(),
                    ScanResponse.class
            );

        } catch (JsonProcessingException e) {

            throw new IllegalStateException(
                    "Could not read saved scan result", e
            );
        }
    }

    private Integer calculateHealthScore(ScanResponse response) {

        if (response == null) {
            return null;
        }

        List<Integer> scores = new ArrayList<>();

        if (response.performanceAnalysis() != null) {
            scores.add(
                    response.performanceAnalysis().performanceScore()
            );
        }

        if (response.securityAnalysis() != null) {
            scores.add(
                    response.securityAnalysis().securityScore()
            );
        }

        if (response.seoAnalysis() != null) {
            scores.add(
                    response.seoAnalysis().seoScore()
            );
        }

        if (response.mobileAnalysis() != null) {
            scores.add(
                    response.mobileAnalysis().mobileScore()
            );
        }

        if (response.apiAnalysis() != null
                && !"NOT_ASSESSED".equalsIgnoreCase(
                        response.apiAnalysis().healthStatus()
                )) {

            scores.add(
                    response.apiAnalysis().apiHealthScore()
            );
        }

        if (scores.isEmpty()) {
            return null;
        }

        double average = scores.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);

        return (int) Math.round(average);
    }
}