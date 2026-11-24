package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.SecurityAnalysisResponse;
import com.webdoctor.backend.scanner.dto.SecurityIssue;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class SecurityAnalysisService {

    public SecurityAnalysisResponse analyze(
            String url,
            Map<String, List<String>> headers
    ) {

        List<SecurityIssue> issues = new ArrayList<>();

        int score = 100;

        boolean https = url != null &&
                url.toLowerCase(Locale.ROOT).startsWith("https://");

        // 1. HTTPS
        if (!https) {

            addIssue(
                    issues,
                    "HTTPS",
                    "HIGH",
                    "Website is not using HTTPS.",
                    "Install a valid SSL certificate and redirect HTTP traffic to HTTPS."
            );

            score -= 25;
        }

        // 2. Content Security Policy
        List<String> cspHeaders = getHeader(
                headers,
                "content-security-policy"
        );

        boolean validCsp = cspHeaders.stream()
                .anyMatch(value ->
                        value != null &&
                        !value.isBlank() &&
                        !value.toLowerCase(Locale.ROOT).contains("unsafe-eval")
                );

        if (!validCsp) {

            addIssue(
                    issues,
                    "Content-Security-Policy",
                    "HIGH",
                    "A usable Content Security Policy was not detected.",
                    "Configure a restrictive Content-Security-Policy with appropriate default-src, script-src and other directives."
            );

            score -= 20;
        }

        // 3. Clickjacking Protection
        boolean xFrameOptions = getHeader(headers, "x-frame-options")
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(value -> {
                    String normalized = value.trim().toUpperCase(Locale.ROOT);

                    return normalized.equals("DENY") ||
                            normalized.equals("SAMEORIGIN");
                });

        boolean cspFrameAncestors = cspHeaders.stream()
                .filter(Objects::nonNull)
                .anyMatch(value ->
                        value.toLowerCase(Locale.ROOT)
                                .matches("(?s).*frame-ancestors\\s+[^;]+.*")
                );

        if (!xFrameOptions && !cspFrameAncestors) {

            addIssue(
                    issues,
                    "Clickjacking Protection",
                    "MEDIUM",
                    "Neither a valid X-Frame-Options policy nor a CSP frame-ancestors directive was detected.",
                    "Configure X-Frame-Options as DENY or SAMEORIGIN, or use an appropriate Content-Security-Policy frame-ancestors directive."
            );

            score -= 10;
        }

        // 4. Content Type Protection
        boolean validNosniff = getHeader(headers, "x-content-type-options")
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(value ->
                        value.trim().equalsIgnoreCase("nosniff")
                );

        if (!validNosniff) {

            addIssue(
                    issues,
                    "X-Content-Type-Options",
                    "MEDIUM",
                    "The nosniff protection header is missing or incorrectly configured.",
                    "Set X-Content-Type-Options to nosniff."
            );

            score -= 10;
        }

        // 5. HSTS
        if (https) {

            boolean validHsts = getHeader(
                    headers,
                    "strict-transport-security"
            )
                    .stream()
                    .filter(Objects::nonNull)
                    .anyMatch(this::hasValidHstsMaxAge);

            if (!validHsts) {

                addIssue(
                        issues,
                        "Strict-Transport-Security",
                        "MEDIUM",
                        "A valid HSTS max-age directive was not detected.",
                        "Configure Strict-Transport-Security with an appropriate max-age value."
                );

                score -= 10;
            }
        }

        // 6. Referrer Policy
        boolean validReferrerPolicy = getHeader(
                headers,
                "referrer-policy"
        )
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(this::hasValidReferrerPolicy);

        if (!validReferrerPolicy) {

            addIssue(
                    issues,
                    "Referrer-Policy",
                    "LOW",
                    "A recognized Referrer-Policy value was not detected.",
                    "Consider setting Referrer-Policy to strict-origin-when-cross-origin."
            );

            score -= 5;
        }

        // 7. Permissions Policy
        boolean validPermissionsPolicy = getHeader(
                headers,
                "permissions-policy"
        )
                .stream()
                .filter(Objects::nonNull)
                .anyMatch(value -> !value.isBlank());

        if (!validPermissionsPolicy) {

            addIssue(
                    issues,
                    "Permissions-Policy",
                    "LOW",
                    "Permissions-Policy header is missing or empty.",
                    "Configure Permissions-Policy to restrict unnecessary browser features."
            );

            score -= 5;
        }

        // 8. Cookie Security
        List<String> cookies = getHeader(headers, "set-cookie");

        boolean missingSecure = false;
        boolean missingHttpOnly = false;
        boolean missingSameSite = false;

        for (String cookie : cookies) {

            if (cookie == null || cookie.isBlank()) {
                continue;
            }

            String[] attributes = cookie.split(";");

            boolean secure = false;
            boolean httpOnly = false;
            boolean sameSite = false;

            for (String attribute : attributes) {

                String normalized = attribute.trim()
                        .toLowerCase(Locale.ROOT);

                if (normalized.equals("secure")) {
                    secure = true;
                }

                if (normalized.equals("httponly")) {
                    httpOnly = true;
                }

                if (normalized.startsWith("samesite=")) {
                    String value = normalized.substring("samesite=".length());

                    sameSite = value.equals("lax") ||
                            value.equals("strict") ||
                            value.equals("none");
                }
            }

            if (https && !secure) {
                missingSecure = true;
            }

            if (!httpOnly) {
                missingHttpOnly = true;
            }

            if (!sameSite) {
                missingSameSite = true;
            }
        }

        // Secure flag: one penalty regardless of cookie count
        if (missingSecure) {

            addIssue(
                    issues,
                    "Cookie Secure Flag",
                    "MEDIUM",
                    "One or more cookies are missing the Secure attribute.",
                    "Add the Secure attribute to cookies that should only be transmitted over HTTPS."
            );

            score -= 5;
        }

        // HttpOnly flag
        if (missingHttpOnly) {

            addIssue(
                    issues,
                    "Cookie HttpOnly Flag",
                    "MEDIUM",
                    "One or more cookies are missing the HttpOnly attribute.",
                    "Add HttpOnly to cookies that do not require JavaScript access."
            );

            score -= 5;
        }

        // SameSite flag
        if (missingSameSite) {

            addIssue(
                    issues,
                    "Cookie SameSite Flag",
                    "LOW",
                    "One or more cookies are missing a recognized SameSite attribute.",
                    "Configure an appropriate SameSite value such as Lax or Strict."
            );

            score -= 5;
        }

        // Final score
        score = Math.max(0, Math.min(100, score));

        String grade = calculateGrade(score);

        String message = issues.isEmpty()
                ? "No issues detected by the implemented security checks."
                : issues.size() +
                " security configuration findings detected. Review their risk levels and recommendations.";

        return new SecurityAnalysisResponse(
                score,
                grade,
                issues,
                message
        );
    }

    private void addIssue(
            List<SecurityIssue> issues,
            String name,
            String riskLevel,
            String description,
            String recommendation
    ) {

        issues.add(new SecurityIssue(
                name,
                "MISSING",
                riskLevel,
                description,
                recommendation
        ));
    }

    private boolean hasValidHstsMaxAge(String value) {

        String[] directives = value.split(";");

        for (String directive : directives) {

            String normalized = directive.trim()
                    .toLowerCase(Locale.ROOT);

            if (normalized.startsWith("max-age=")) {

                try {
                    long maxAge = Long.parseLong(
                            normalized.substring("max-age=".length())
                    );

                    return maxAge > 0;

                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }

        return false;
    }

    private boolean hasValidReferrerPolicy(String value) {

        String[] allowedPolicies = {
                "no-referrer",
                "no-referrer-when-downgrade",
                "origin",
                "origin-when-cross-origin",
                "same-origin",
                "strict-origin",
                "strict-origin-when-cross-origin",
                "unsafe-url"
        };

        String[] policies = value.split(",");

        for (String policy : policies) {

            String normalized = policy.trim()
                    .toLowerCase(Locale.ROOT);

            for (String allowed : allowedPolicies) {

                if (normalized.equals(allowed)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String calculateGrade(int score) {

        if (score >= 90) {
            return "A";
        }

        if (score >= 75) {
            return "B";
        }

        if (score >= 60) {
            return "C";
        }

        if (score >= 40) {
            return "D";
        }

        return "F";
    }

    private List<String> getHeader(
            Map<String, List<String>> headers,
            String headerName
    ) {

        if (headers == null || headers.isEmpty()) {
            return List.of();
        }

        return headers.entrySet()
                .stream()
                .filter(entry ->
                        entry.getKey() != null &&
                        entry.getKey().equalsIgnoreCase(headerName)
                )
                .filter(entry -> entry.getValue() != null)
                .flatMap(entry -> entry.getValue().stream())
                .filter(Objects::nonNull)
                .toList();
    }
}