package com.webdoctor.backend.scanner.controller;

import com.webdoctor.backend.auth.entity.AppUser;
import com.webdoctor.backend.auth.repository.AppUserRepository;
import com.webdoctor.backend.scanner.dto.ScanRequest;
import com.webdoctor.backend.scanner.dto.ScanResponse;
import com.webdoctor.backend.scanner.service.WebsiteScanService;
import com.webdoctor.backend.history.service.ScanHistoryService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scan")
@CrossOrigin(origins = "http://localhost:5173")
public class ScanController {

    private final WebsiteScanService websiteScanService;
    private final ScanHistoryService scanHistoryService;
    private final AppUserRepository userRepository;

    public ScanController(
            WebsiteScanService websiteScanService,
            ScanHistoryService scanHistoryService,
            AppUserRepository userRepository) {

        this.websiteScanService = websiteScanService;
        this.scanHistoryService = scanHistoryService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> scanWebsite(
            @Valid @RequestBody ScanRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        if (request.url() == null || request.url().isBlank()) {

            return ResponseEntity.badRequest()
                    .body("Website URL is required");
        }

        try {

            AppUser user = userRepository.findByEmail(jwt.getSubject())
                    .orElseThrow(() -> new IllegalStateException(
                            "Authenticated user no longer exists"
                    ));

            ScanResponse response =
                    websiteScanService.scanWebsite(request.url());

            // Save scan against the authenticated user
            scanHistoryService.saveScan(response, user);

            // Keep existing frontend response unchanged
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }
}
