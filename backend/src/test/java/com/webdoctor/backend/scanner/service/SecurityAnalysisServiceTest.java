package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.SecurityAnalysisResponse;
import com.webdoctor.backend.scanner.dto.SecurityIssue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAnalysisServiceTest {

    private SecurityAnalysisService securityAnalysisService;

    @BeforeEach
    void setUp() {
        securityAnalysisService = new SecurityAnalysisService();
    }

    // TEST 1:
    // All important security headers are missing.

    @Test
    void shouldDetectMissingSecurityHeaders() {

        Map<String, List<String>> headers = new HashMap<>();

        SecurityAnalysisResponse response =
                securityAnalysisService.analyze(
                        "https://example.com",
                        headers
                );

        assertEquals(40, response.securityScore());
        assertEquals("D", response.grade());

        assertFalse(response.issues().isEmpty());

        assertTrue(hasIssue(response, "Content-Security-Policy"));
        assertTrue(hasIssue(response, "Clickjacking Protection"));
        assertTrue(hasIssue(response, "X-Content-Type-Options"));
        assertTrue(hasIssue(response, "Strict-Transport-Security"));
        assertTrue(hasIssue(response, "Referrer-Policy"));
        assertTrue(hasIssue(response, "Permissions-Policy"));
    }


    // TEST 2:
    // All security headers are correctly configured.

    @Test
    void shouldGiveFullScoreWhenSecurityHeadersAreValid() {

        Map<String, List<String>> headers = validSecurityHeaders();

        SecurityAnalysisResponse response =
                securityAnalysisService.analyze(
                        "https://example.com",
                        headers
                );

        assertEquals(100, response.securityScore());
        assertEquals("A", response.grade());

        assertTrue(response.issues().isEmpty());
    }


    // TEST 3:
    // Multiple cookies should not create repeated penalties
    // for the same missing cookie attribute.

    @Test
    void shouldApplyCookiePenaltiesOnlyOnceForMultipleCookies() {

        Map<String, List<String>> headers = validSecurityHeaders();

        headers.put("Set-Cookie", List.of(
                "session=abc123; Path=/",
                "user=krishna; Path=/",
                "token=xyz789; Path=/"
        ));

        SecurityAnalysisResponse response =
                securityAnalysisService.analyze(
                        "https://example.com",
                        headers
                );

        // Secure missing: -5
        // HttpOnly missing: -5
        // SameSite missing: -5
        // Total penalty: -15

        assertEquals(85, response.securityScore());
        assertEquals("B", response.grade());

        assertEquals(
                1,
                countIssues(response, "Cookie Secure Flag")
        );

        assertEquals(
                1,
                countIssues(response, "Cookie HttpOnly Flag")
        );

        assertEquals(
                1,
                countIssues(response, "Cookie SameSite Flag")
        );
    }


    // TEST 4:
    // HTTP websites should not receive HTTPS-only HSTS
    // or Secure-cookie penalties.

    @Test
    void shouldNotPenalizeHttpsOnlyFeaturesForHttpWebsite() {

        Map<String, List<String>> headers = validSecurityHeaders();

        headers.remove("Strict-Transport-Security");

        headers.put("Set-Cookie", List.of(
                "session=abc123; HttpOnly; SameSite=Lax"
        ));

        SecurityAnalysisResponse response =
                securityAnalysisService.analyze(
                        "http://example.com",
                        headers
                );

        // HTTPS penalty: -25
        // HSTS penalty: 0
        // Secure cookie penalty: 0

        assertEquals(75, response.securityScore());
        assertEquals("B", response.grade());

        assertFalse(
                hasIssue(response, "Strict-Transport-Security")
        );

        assertFalse(
                hasIssue(response, "Cookie Secure Flag")
        );
    }


    // TEST 5:
    // Incorrect security header values should be detected.

    @Test
    void shouldDetectIncorrectSecurityHeaderValues() {

        Map<String, List<String>> headers = validSecurityHeaders();

        headers.put(
                "X-Content-Type-Options",
                List.of("invalid-value")
        );

        headers.put(
                "Referrer-Policy",
                List.of("invalid-policy")
        );

        SecurityAnalysisResponse response =
                securityAnalysisService.analyze(
                        "https://example.com",
                        headers
                );

        assertTrue(
                hasIssue(response, "X-Content-Type-Options")
        );

        assertTrue(
                hasIssue(response, "Referrer-Policy")
        );

        assertEquals(85, response.securityScore());
    }


    // HELPER:
    // Provides a valid set of security headers.

    private Map<String, List<String>> validSecurityHeaders() {

        Map<String, List<String>> headers = new HashMap<>();

        headers.put(
                "Content-Security-Policy",
                List.of("default-src 'self'; frame-ancestors 'self'")
        );

        headers.put(
                "X-Content-Type-Options",
                List.of("nosniff")
        );

        headers.put(
                "Strict-Transport-Security",
                List.of("max-age=31536000; includeSubDomains")
        );

        headers.put(
                "Referrer-Policy",
                List.of("strict-origin-when-cross-origin")
        );

        headers.put(
                "Permissions-Policy",
                List.of("camera=(), microphone=(), geolocation=()")
        );

        headers.put(
                "Set-Cookie",
                List.of(
                        "session=abc123; Secure; HttpOnly; SameSite=Lax"
                )
        );

        return headers;
    }


    // HELPER:
    // Checks whether an issue exists.

    private boolean hasIssue(
            SecurityAnalysisResponse response,
            String issueName
    ) {

        return response.issues()
                .stream()
                .anyMatch(issue -> issue.name().equals(issueName));
    }


    // HELPER:
    // Counts occurrences of a particular issue.

    private long countIssues(
            SecurityAnalysisResponse response,
            String issueName
    ) {

        return response.issues()
                .stream()
                .filter(issue -> issue.name().equals(issueName))
                .count();
    }
}