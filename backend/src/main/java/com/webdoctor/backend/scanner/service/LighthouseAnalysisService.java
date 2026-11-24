package com.webdoctor.backend.scanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webdoctor.backend.scanner.dto.LighthouseAnalysisResponse;
import com.webdoctor.backend.scanner.dto.LighthouseAnalysisResponse.LighthouseResult;
import com.webdoctor.backend.scanner.dto.LighthouseAnalysisResponse.LighthouseAudit;
import com.webdoctor.backend.scanner.dto.LighthouseAnalysisResponse.LighthouseMetric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

@Service
public class LighthouseAnalysisService {

    private static final Logger log =
            LoggerFactory.getLogger(LighthouseAnalysisService.class);

    private static final String API_ENDPOINT =
            "https://www.googleapis.com/pagespeedonline/v5/runPagespeed";

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(180);

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final HttpClient httpClient;
    private final PageSpeedGateway gateway;

    @Autowired
    public LighthouseAnalysisService(
            ObjectMapper objectMapper,
            @Value("${PAGESPEED_API_KEY:}") String apiKey
    ) {

        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        this.gateway = this::fetchFromGoogle;
    }

    // Constructor for unit tests.
    LighthouseAnalysisService(
            ObjectMapper objectMapper,
            PageSpeedGateway gateway
    ) {

        this.objectMapper = objectMapper;
        this.apiKey = "";
        this.httpClient = null;
        this.gateway = gateway;
    }

    public LighthouseAnalysisResponse analyze(String url) {

        LighthouseResult mobile =
                runAuditSafely(url, "mobile");

        LighthouseResult desktop =
                runAuditSafely(url, "desktop");

        boolean mobileCompleted =
                "COMPLETED".equals(mobile.status());

        boolean desktopCompleted =
                "COMPLETED".equals(desktop.status());

        String status;

        if (mobileCompleted && desktopCompleted) {
            status = "COMPLETED";
        } else if (mobileCompleted || desktopCompleted) {
            status = "PARTIAL";
        } else {
            status = "FAILED";
        }

        String message = switch (status) {
            case "COMPLETED" ->
                    "Mobile and Desktop PageSpeed audits completed successfully.";

            case "PARTIAL" ->
                    "Only one PageSpeed audit completed successfully.";

            default ->
                    "PageSpeed audits could not be completed.";
        };

        return new LighthouseAnalysisResponse(
                status,
                mobile,
                desktop,
                message
        );
    }

    private LighthouseResult runAuditSafely(
            String url,
            String strategy
    ) {

        try {
            JsonNode response = gateway.fetch(url, strategy);

            return mapResponse(response, strategy);

        } catch (Exception e) {

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            log.error(
                    "PageSpeed {} audit failed for URL: {}. Reason: {}",
                    strategy,
                    url,
                    e.getMessage()
            );

            return failedResult(strategy);
        }
    }

    private JsonNode fetchFromGoogle(
            String url,
            String strategy
    ) throws Exception {

        StringBuilder query = new StringBuilder(API_ENDPOINT)
                .append("?url=")
                .append(encode(url))
                .append("&strategy=")
                .append(encode(strategy));

        query.append("&category=PERFORMANCE");
        query.append("&category=ACCESSIBILITY");
        query.append("&category=BEST_PRACTICES");
        query.append("&category=SEO");

        if (!apiKey.isBlank()) {
            query.append("&key=").append(encode(apiKey));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(query.toString()))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                )
        );

        int statusCode = response.statusCode();

        if (statusCode < 200 || statusCode >= 300) {

            String details = extractApiError(response.body());

            if (statusCode == 429) {
                throw new IOException(
                        "Google PageSpeed API rate limit or quota exceeded. "
                                + details
                );
            }

            throw new IOException(
                    "Google PageSpeed API returned HTTP "
                            + statusCode + ". " + details
            );
        }

        return objectMapper.readTree(response.body());
    }

    private String encode(String value) {

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private String extractApiError(String body) {

        try {

            JsonNode error = objectMapper.readTree(body)
                    .path("error")
                    .path("message");

            if (!error.isMissingNode() && !error.isNull()) {
                return error.asText();
            }

        } catch (Exception ignored) {
            // Do not expose the raw response body in logs.
        }

        return "No additional error details available.";
    }

    private LighthouseResult mapResponse(
            JsonNode response,
            String strategy
    ) throws IOException {

        JsonNode report = response.path("lighthouseResult");

        if (!report.isObject()) {
            throw new IOException(
                    "Google PageSpeed response does not contain lighthouseResult."
            );
        }

        JsonNode runtimeError = report.path("runtimeError");

        if (!runtimeError.isMissingNode()
                && !runtimeError.isNull()
                && runtimeError.has("message")) {

            throw new IOException(
                    runtimeError.path("message").asText()
            );
        }

        JsonNode categories = report.path("categories");
        JsonNode audits = report.path("audits");

        AuditLists auditLists = getAuditLists(
                categories,
                audits
        );

        return new LighthouseResult(
                "COMPLETED",
                strategy,

                getScore(categories, "performance"),

                getMetric(audits, "first-contentful-paint"),
                getMetric(audits, "largest-contentful-paint"),
                getMetric(audits, "total-blocking-time"),
                getMetric(audits, "cumulative-layout-shift"),
                getMetric(audits, "speed-index"),

                textOrNull(report, "finalUrl"),
                textOrNull(report, "fetchTime"),

                getScore(categories, "accessibility"),
                getScore(categories, "best-practices"),
                getScore(categories, "seo"),

                auditLists.opportunities(),
                auditLists.diagnostics(),
                auditLists.failedAudits()
        );
    }

    private Integer getScore(
            JsonNode categories,
            String categoryId
    ) {

        JsonNode score = categories
                .path(categoryId)
                .path("score");

        if (!score.isNumber()) {
            return null;
        }

        return (int) Math.round(score.asDouble() * 100);
    }

    private LighthouseMetric getMetric(
            JsonNode audits,
            String auditId
    ) {

        JsonNode audit = audits.path(auditId);

        if (!audit.isObject()) {
            return null;
        }

        return new LighthouseMetric(
                numberOrNull(audit, "numericValue"),
                textOrNull(audit, "displayValue"),
                numberOrNull(audit, "score")
        );
    }

    private AuditLists getAuditLists(
            JsonNode categories,
            JsonNode audits
    ) {

        Map<String, LighthouseAudit> opportunities =
                new LinkedHashMap<>();

        Map<String, LighthouseAudit> diagnostics =
                new LinkedHashMap<>();

        Map<String, LighthouseAudit> failedAudits =
                new LinkedHashMap<>();

        Iterator<Map.Entry<String, JsonNode>> categoryIterator =
                categories.fields();

        while (categoryIterator.hasNext()) {

            JsonNode category = categoryIterator.next().getValue();

            JsonNode references = category.path("auditRefs");

            if (!references.isArray()) {
                continue;
            }

            for (JsonNode reference : references) {

                String id = textOrNull(reference, "id");

                if (id == null) {
                    continue;
                }

                JsonNode audit = audits.path(id);

                if (!audit.isObject()) {
                    continue;
                }

                LighthouseAudit item = toAudit(id, audit);

                String group = textOrNull(reference, "group");

                Double score = numberOrNull(audit, "score");

                if (("load-opportunities".equals(group)
                        || "budgets".equals(group))
                        && score != null
                        && score < 1) {

                    opportunities.putIfAbsent(id, item);
                }

                if ("diagnostics".equals(group)) {
                    diagnostics.putIfAbsent(id, item);
                }

                if (score != null && score < 1) {
                    failedAudits.putIfAbsent(id, item);
                }
            }
        }

        return new AuditLists(
                new ArrayList<>(opportunities.values()),
                new ArrayList<>(diagnostics.values()),
                new ArrayList<>(failedAudits.values())
        );
    }

    private LighthouseAudit toAudit(
            String id,
            JsonNode audit
    ) {

        JsonNode details = audit.path("details");

        return new LighthouseAudit(
                id,
                textOrNull(audit, "title"),
                textOrNull(audit, "description"),
                textOrNull(audit, "displayValue"),
                numberOrNull(audit, "score"),
                textOrNull(audit, "scoreDisplayMode"),
                numberOrNull(audit, "numericValue"),
                textOrNull(audit, "numericUnit"),
                numberOrNull(details, "overallSavingsMs"),
                numberOrNull(details, "overallSavingsBytes")
        );
    }

    private String textOrNull(
            JsonNode node,
            String field
    ) {

        JsonNode value = node.path(field);

        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    private Double numberOrNull(
            JsonNode node,
            String field
    ) {

        JsonNode value = node.path(field);

        if (!value.isNumber()) {
            return null;
        }

        return value.asDouble();
    }

    private LighthouseResult failedResult(String strategy) {

        return new LighthouseResult(
                "FAILED",
                strategy,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private record AuditLists(
            List<LighthouseAudit> opportunities,
            List<LighthouseAudit> diagnostics,
            List<LighthouseAudit> failedAudits
    ) {
    }

    @FunctionalInterface
    interface PageSpeedGateway {
        JsonNode fetch(String url, String strategy) throws Exception;
    }
}