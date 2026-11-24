package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.JavaScriptAnalysisResponse;
import com.webdoctor.backend.scanner.dto.JavaScriptFinding;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.springframework.stereotype.Service;

import java.io.InputStream;

import java.net.URI;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JavaScriptAnalysisService {

    private static final int MAX_SCRIPTS = 25;

    private final HttpClient httpClient;

    public JavaScriptAnalysisService() {

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public JavaScriptAnalysisResponse analyze(
            String websiteUrl,
            String html
    ) {

        List<JavaScriptFinding> findings = new ArrayList<>();

        int workingScripts = 0;
        int brokenScripts = 0;
        int redirectedScripts = 0;
        int errorScripts = 0;
        int skippedScripts = 0;

        try {

            URI baseUri = URI.create(websiteUrl);

            Document document = Jsoup.parse(html, websiteUrl);

            Elements scripts = document.select("script[src]");

            Map<String, String> uniqueScripts = new LinkedHashMap<>();

            // Detect unique JavaScript resources
            for (Element script : scripts) {

                String src = script.attr("src").trim();

                if (src.isBlank()) {
                    continue;
                }

                try {

                    URI scriptUri = baseUri.resolve(src).normalize();

                    String scheme = scriptUri.getScheme();

                    if (scheme == null
                            || !(scheme.equalsIgnoreCase("http")
                            || scheme.equalsIgnoreCase("https"))
                            || scriptUri.getHost() == null
                            || scriptUri.getUserInfo() != null) {

                        continue;
                    }

                    String scriptType =
                            "module".equalsIgnoreCase(
                                    script.attr("type").trim())
                                    ? "MODULE"
                                    : "CLASSIC";

                    uniqueScripts.putIfAbsent(
                            scriptUri.toString(),
                            scriptType
                    );

                } catch (Exception ignored) {
                    // Ignore invalid script URLs
                }
            }

            int totalScripts = uniqueScripts.size();

            int checkedScripts = 0;

            for (Map.Entry<String, String> entry
                    : uniqueScripts.entrySet()) {

                if (checkedScripts >= MAX_SCRIPTS) {
                    continue;
                }

                checkedScripts++;

                String scriptUrl = entry.getKey();

                String scriptType = entry.getValue();

                try {

                    URI scriptUri = URI.create(scriptUrl);

                    // Validate destination IP
                    if (!isPublicAddress(scriptUri.getHost())) {

                        skippedScripts++;

                        findings.add(new JavaScriptFinding(
                                scriptUrl,
                                scriptType,
                                getLinkType(baseUri, scriptUri),
                                0,
                                "SKIPPED",
                                "Private or local address is not allowed",
                                "Not Available"
                        ));

                        continue;
                    }

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(scriptUri)
                            .timeout(Duration.ofSeconds(10))
                            .header(
                                    "User-Agent",
                                    "WebDoctor-Scanner/1.0"
                            )
                            .GET()
                            .build();

                    HttpResponse<InputStream> response =
                            httpClient.send(
                                    request,
                                    HttpResponse.BodyHandlers.ofInputStream()
                            );

                    int statusCode = response.statusCode();

                    String contentType = response.headers()
                            .firstValue("content-type")
                            .orElse("Unknown");

                    // Close response body without downloading
                    // the entire JavaScript file.
                    try (InputStream body = response.body()) {
                        // Body is intentionally not read.
                    }

                    String status;
                    String message;

                    if (statusCode >= 200 && statusCode < 300) {

                        status = "WORKING";
                        message = "JavaScript resource is accessible";
                        workingScripts++;

                    } else if (statusCode >= 300 && statusCode < 400) {

                        status = "REDIRECT";
                        message = "JavaScript resource redirects";
                        redirectedScripts++;

                    } else {

                        status = "BROKEN";
                        message = "HTTP error: " + statusCode;
                        brokenScripts++;
                    }

                    findings.add(new JavaScriptFinding(
                            scriptUrl,
                            scriptType,
                            getLinkType(baseUri, scriptUri),
                            statusCode,
                            status,
                            message,
                            contentType
                    ));

                } catch (Exception e) {

                    errorScripts++;

                    findings.add(new JavaScriptFinding(
                            scriptUrl,
                            scriptType,
                            "UNKNOWN",
                            0,
                            "ERROR",
                            "Unable to access JavaScript resource",
                            "Not Available"
                    ));
                }
            }

            skippedScripts += Math.max(
                    0,
                    totalScripts - checkedScripts
            );

            return new JavaScriptAnalysisResponse(
                    totalScripts,
                    checkedScripts,
                    workingScripts,
                    brokenScripts,
                    redirectedScripts,
                    errorScripts,
                    skippedScripts,
                    findings,
                    "JavaScript analysis completed successfully"
            );

        } catch (Exception e) {

            return new JavaScriptAnalysisResponse(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    findings,
                    "JavaScript analysis failed"
            );
        }
    }

    private boolean isPublicAddress(String host) {

        try {

            InetAddress[] addresses =
                    InetAddress.getAllByName(host);

            if (addresses.length == 0) {
                return false;
            }

            for (InetAddress address : addresses) {

                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {

                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private String getLinkType(
            URI baseUri,
            URI scriptUri
    ) {

        if (baseUri.getHost() != null
                && baseUri.getHost().equalsIgnoreCase(
                        scriptUri.getHost())) {

            return "INTERNAL";
        }

        return "EXTERNAL";
    }
}