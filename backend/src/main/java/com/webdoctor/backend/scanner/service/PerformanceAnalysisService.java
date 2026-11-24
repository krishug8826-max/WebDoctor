package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.PerformanceAnalysisResponse;
import com.webdoctor.backend.scanner.dto.PerformanceFinding;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PerformanceAnalysisService {

    public PerformanceAnalysisResponse analyze(
            long responseTime,
            Map<String, List<String>> headers
    ) {

        List<PerformanceFinding> findings = new ArrayList<>();

        int score = 100;

        // 1. Response Time Analysis

        if (responseTime <= 200) {

            findings.add(new PerformanceFinding(
                    "Server Response Time",
                    "GOOD",
                    "Website responded quickly.",
                    "Continue monitoring server response time."
            ));

        } else if (responseTime <= 500) {

            findings.add(new PerformanceFinding(
                    "Server Response Time",
                    "AVERAGE",
                    "Website response time is acceptable but can improve.",
                    "Optimize backend processing, database queries and server resources."
            ));

            score -= 10;

        } else if (responseTime <= 1000) {

            findings.add(new PerformanceFinding(
                    "Server Response Time",
                    "WARNING",
                    "Website response time is slower than recommended.",
                    "Investigate server performance, database queries and network latency."
            ));

            score -= 25;

        } else {

            findings.add(new PerformanceFinding(
                    "Server Response Time",
                    "POOR",
                    "Website is taking a long time to respond.",
                    "Investigate slow server operations and optimize application performance."
            ));

            score -= 40;
        }

        // 2. Content Length Analysis

        long contentLength = getContentLength(headers);

        if (contentLength > 0) {

            if (contentLength <= 500_000) {

                findings.add(new PerformanceFinding(
                        "Response Size",
                        "GOOD",
                        "Reported response size is relatively small.",
                        "Continue optimizing HTML and other website resources."
                ));

            } else if (contentLength <= 2_000_000) {

                findings.add(new PerformanceFinding(
                        "Response Size",
                        "AVERAGE",
                        "Reported response size could be optimized.",
                        "Reduce unnecessary content and optimize images and resources."
                ));

                score -= 10;

            } else {

                findings.add(new PerformanceFinding(
                        "Response Size",
                        "WARNING",
                        "Reported response size is relatively large.",
                        "Consider compressing and optimizing website resources."
                ));

                score -= 20;
            }

        } else {

            findings.add(new PerformanceFinding(
                    "Response Size",
                    "UNKNOWN",
                    "Content-Length was not provided by the server.",
                    "Use a browser-based performance audit to measure downloaded resources."
            ));
        }

        // 3. Content Type Analysis

        String contentType = getHeader(headers, "content-type");

        if (contentType.isBlank()) {

            contentType = "Not Available";

            findings.add(new PerformanceFinding(
                    "Content Type",
                    "UNKNOWN",
                    "Server did not provide a Content-Type header.",
                    "Configure the server to return the correct Content-Type."
            ));

        } else {

            findings.add(new PerformanceFinding(
                    "Content Type",
                    "DETECTED",
                    "Response content type: " + contentType,
                    "Ensure the correct content type is configured."
            ));
        }

        // 4. Compression Analysis

        String encoding = getHeader(headers, "content-encoding");

        String compression;

        if (encoding.isBlank()) {

            compression = "Not Detected";

            findings.add(new PerformanceFinding(
                    "Response Compression",
                    "NOT_DETECTED",
                    "No Content-Encoding header was detected.",
                    "Check whether GZIP or Brotli compression is configured. Compression may not be used for every response."
            ));

        } else {

            compression = encoding;

            findings.add(new PerformanceFinding(
                    "Response Compression",
                    "DETECTED",
                    "Server reported Content-Encoding: " + encoding,
                    "Continue using suitable compression for text-based resources."
            ));
        }

        // 5. Cache Control Analysis

        String cacheControl = getHeader(headers, "cache-control");

        if (cacheControl.isBlank()) {

            findings.add(new PerformanceFinding(
                    "Browser Caching",
                    "NOT_DETECTED",
                    "Cache-Control header was not detected.",
                    "Review caching policies for static resources such as images, CSS and JavaScript."
            ));

        } else {

            findings.add(new PerformanceFinding(
                    "Browser Caching",
                    "DETECTED",
                    "Cache-Control: " + cacheControl,
                    "Review whether the caching policy is suitable for the resource."
            ));
        }

        // 6. Final Score

        score = Math.max(0, Math.min(100, score));

        String grade;

        if (score >= 90) {
            grade = "A";
        } else if (score >= 75) {
            grade = "B";
        } else if (score >= 60) {
            grade = "C";
        } else if (score >= 40) {
            grade = "D";
        } else {
            grade = "F";
        }

        return new PerformanceAnalysisResponse(
                score,
                grade,
                responseTime,
                contentLength,
                contentType,
                compression,
                findings,
                "Performance analysis completed."
        );
    }

    private String getHeader(
            Map<String, List<String>> headers,
            String headerName
    ) {

        return headers.entrySet()
                .stream()
                .filter(entry ->
                        entry.getKey().equalsIgnoreCase(headerName))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse("");
    }

    private long getContentLength(
            Map<String, List<String>> headers
    ) {

        try {

            String value = getHeader(headers, "content-length");

            if (value.isBlank()) {
                return 0;
            }

            return Long.parseLong(value);

        } catch (NumberFormatException e) {

            return 0;
        }
    }
}