package com.webdoctor.backend.history.controller;

import com.webdoctor.backend.auth.entity.AppUser;
import com.webdoctor.backend.auth.repository.AppUserRepository;
import com.webdoctor.backend.history.dto.ScanHistoryDetail;
import com.webdoctor.backend.history.dto.ScanHistoryPageResponse;
import com.webdoctor.backend.history.dto.ScanHistorySummary;
import com.webdoctor.backend.history.service.ScanHistoryService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scan/history")
@CrossOrigin(origins = "http://localhost:5173")
public class ScanHistoryController {

    private final ScanHistoryService scanHistoryService;
    private final AppUserRepository userRepository;

    public ScanHistoryController(
            ScanHistoryService scanHistoryService,
            AppUserRepository userRepository) {

        this.scanHistoryService = scanHistoryService;
        this.userRepository = userRepository;
    }

    private AppUser getAuthenticatedUser(Jwt jwt) {
        return userRepository.findByEmail(jwt.getSubject())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user no longer exists"
                ));
    }

    @GetMapping
    public ResponseEntity<List<ScanHistorySummary>> getHistory(
            @AuthenticationPrincipal Jwt jwt) {

        AppUser user = getAuthenticatedUser(jwt);

        return ResponseEntity.ok(
                scanHistoryService.getRecentHistory(user)
        );
    }
    @GetMapping("/page")
public ResponseEntity<ScanHistoryPageResponse> getHistoryPage(

        @AuthenticationPrincipal Jwt jwt,

        @RequestParam(defaultValue = "0") int page,

        @RequestParam(defaultValue = "10") int size,

        @RequestParam(required = false) String search,

        @RequestParam(defaultValue = "all") String status,

        @RequestParam(defaultValue = "all") String dateRange,

        @RequestParam(defaultValue = "scannedAt") String sortBy,

        @RequestParam(defaultValue = "desc") String sortDirection) {

    AppUser user = getAuthenticatedUser(jwt);

    return ResponseEntity.ok(
            scanHistoryService.getHistoryPage(
                    user,
                    page,
                    size,
                    search,
                    status,
                    dateRange,
                    sortBy,
                    sortDirection
            )
    );
}

    @GetMapping("/{id}")
    public ResponseEntity<ScanHistoryDetail> getScanDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        AppUser user = getAuthenticatedUser(jwt);

        return scanHistoryService.getScanById(id, user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScan(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        AppUser user = getAuthenticatedUser(jwt);

        boolean deleted = scanHistoryService.deleteScan(id, user);

        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}