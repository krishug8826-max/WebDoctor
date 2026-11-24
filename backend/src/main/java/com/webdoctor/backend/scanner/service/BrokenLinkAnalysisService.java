package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.BrokenLinkAnalysisResponse;
import com.webdoctor.backend.scanner.dto.BrokenLinkFinding;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Service
public class BrokenLinkAnalysisService {

    private static final int MAX_LINKS = 25;
    private static final int MAX_REDIRECTS = 5;

    private final HttpClient httpClient;
    private final Predicate<String> publicAddressChecker;

    // Production constructor
    public BrokenLinkAnalysisService() {

        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                BrokenLinkAnalysisService::isPublicAddress
        );
    }

    // Package-private constructor for controlled testing
    BrokenLinkAnalysisService(
            HttpClient httpClient,
            Predicate<String> publicAddressChecker
    ) {

        this.httpClient = httpClient;
        this.publicAddressChecker = publicAddressChecker;
    }

    public BrokenLinkAnalysisResponse analyze(
            String websiteUrl,
            String html
    ) {

        List<BrokenLinkFinding> findings = new ArrayList<>();

        int workingLinks = 0;
        int brokenLinks = 0;
        int redirectedLinks = 0;
        int skippedLinks = 0;

        try {

            URI baseUri = URI.create(websiteUrl);

            String scheme = baseUri.getScheme();

            if (!baseUri.isAbsolute()
                    || scheme == null
                    || !(scheme.equalsIgnoreCase("http")
                    || scheme.equalsIgnoreCase("https"))
                    || baseUri.getHost() == null
                    || baseUri.getUserInfo() != null) {

                throw new IllegalArgumentException("Invalid website URL");
            }

            Document document = Jsoup.parse(html, websiteUrl);

            Elements elements = document.select("a[href], area[href]");

            Set<String> uniqueLinks = new LinkedHashSet<>();

            for (Element element : elements) {

                String href = element.attr("href").trim();

                if (href.isBlank()
                        || href.startsWith("#")
                        || href.regionMatches(true, 0, "mailto:", 0, 7)
                        || href.regionMatches(true, 0, "tel:", 0, 4)
                        || href.regionMatches(true, 0, "javascript:", 0, 11)
                        || href.regionMatches(true, 0, "data:", 0, 5)) {

                    continue;
                }

                try {

                    URI resolvedUri = baseUri.resolve(href).normalize();

                    String resolvedScheme = resolvedUri.getScheme();

                    if (!resolvedUri.isAbsolute()
                            || resolvedScheme == null
                            || !(resolvedScheme.equalsIgnoreCase("http")
                            || resolvedScheme.equalsIgnoreCase("https"))
                            || resolvedUri.getHost() == null
                            || resolvedUri.getUserInfo() != null) {

                        continue;
                    }

                    uniqueLinks.add(resolvedUri.toString());

                } catch (Exception ignored) {
                    // Ignore invalid links
                }
            }

            int totalLinks = uniqueLinks.size();
            int checkedLinks = 0;

            for (String link : uniqueLinks) {

                if (checkedLinks >= MAX_LINKS) {
                    continue;
                }

                checkedLinks++;

                try {

                    LinkCheckResult result = checkLink(link);

                    if (result == null) {

                        skippedLinks++;

                        findings.add(new BrokenLinkFinding(
                                link,
                                "UNKNOWN",
                                0,
                                "SKIPPED",
                                "Link could not be checked safely",
                                link,
                                0
                        ));

                        continue;
                    }

                    switch (result.status()) {

                        case "WORKING" -> workingLinks++;

                        case "REDIRECT" -> redirectedLinks++;

                        case "BROKEN" -> brokenLinks++;

                        default -> skippedLinks++;
                    }

                    findings.add(new BrokenLinkFinding(
                            link,
                            result.linkType(),
                            result.statusCode(),
                            result.status(),
                            result.message(),
                            result.finalUrl(),
                            result.redirectCount()
                    ));

                } catch (Exception e) {

                    brokenLinks++;

                    findings.add(new BrokenLinkFinding(
                            link,
                            "UNKNOWN",
                            0,
                            "ERROR",
                            "Link check failed",
                            link,
                            0
                    ));
                }
            }

            // Includes unsafe links and links beyond MAX_LINKS.
            skippedLinks += Math.max(0, totalLinks - checkedLinks);

            return new BrokenLinkAnalysisResponse(
                    totalLinks,
                    workingLinks,
                    brokenLinks,
                    redirectedLinks,
                    skippedLinks,
                    findings,
                    "Advanced link analysis completed successfully"
            );

        } catch (Exception e) {

            return new BrokenLinkAnalysisResponse(
                    0,
                    0,
                    0,
                    0,
                    0,
                    findings,
                    "Broken link analysis failed"
            );
        }
    }

    private LinkCheckResult checkLink(String originalUrl)
            throws Exception {

        URI originalUri = URI.create(originalUrl);
        URI currentUri = originalUri;

        Set<String> visitedUrls = new LinkedHashSet<>();

        int redirectCount = 0;
        int lastStatusCode = 0;

        while (true) {

            // Every redirect destination is checked again.
            if (!publicAddressChecker.test(currentUri.getHost())) {

                return new LinkCheckResult(
                        0,
                        "SKIPPED",
                        "UNKNOWN",
                        "Private or local address is not allowed",
                        currentUri.toString(),
                        redirectCount
                );
            }

            String currentUrl = currentUri.toString();

            if (!visitedUrls.add(currentUrl)) {

                return new LinkCheckResult(
                        lastStatusCode,
                        "BROKEN",
                        getLinkType(originalUri, currentUri),
                        "Redirect loop detected",
                        currentUrl,
                        redirectCount
                );
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(currentUri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "WebDoctor-Scanner/1.0")
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );

            int statusCode = response.statusCode();
            lastStatusCode = statusCode;

            // Some servers do not support HEAD.
            if (statusCode == 405 || statusCode == 501) {

                HttpRequest getRequest = HttpRequest.newBuilder()
                        .uri(currentUri)
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "WebDoctor-Scanner/1.0")
                        .GET()
                        .build();

                response = httpClient.send(
                        getRequest,
                        HttpResponse.BodyHandlers.discarding()
                );

                statusCode = response.statusCode();
                lastStatusCode = statusCode;
            }

            String linkType = getLinkType(originalUri, currentUri);

            if (isRedirect(statusCode)) {

                if (redirectCount >= MAX_REDIRECTS) {

                    return new LinkCheckResult(
                            statusCode,
                            "BROKEN",
                            linkType,
                            "Maximum redirect limit exceeded",
                            currentUrl,
                            redirectCount
                    );
                }

                String location = response.headers()
                        .firstValue("location")
                        .orElse(null);

                if (location == null || location.isBlank()) {

                    return new LinkCheckResult(
                            statusCode,
                            "BROKEN",
                            linkType,
                            "Redirect location is missing",
                            currentUrl,
                            redirectCount
                    );
                }

                URI nextUri;

                try {

                    nextUri = currentUri.resolve(location).normalize();

                } catch (Exception e) {

                    return new LinkCheckResult(
                            statusCode,
                            "BROKEN",
                            linkType,
                            "Invalid redirect destination",
                            currentUrl,
                            redirectCount
                    );
                }

                String nextScheme = nextUri.getScheme();

                if (!nextUri.isAbsolute()
                        || nextUri.getHost() == null
                        || nextUri.getUserInfo() != null
                        || nextScheme == null
                        || !(nextScheme.equalsIgnoreCase("http")
                        || nextScheme.equalsIgnoreCase("https"))) {

                    return new LinkCheckResult(
                            statusCode,
                            "BROKEN",
                            linkType,
                            "Invalid redirect destination",
                            currentUrl,
                            redirectCount
                    );
                }

                redirectCount++;
                currentUri = nextUri;

                continue;
            }

            if (statusCode >= 200 && statusCode < 300) {

                return new LinkCheckResult(
                        statusCode,
                        redirectCount > 0 ? "REDIRECT" : "WORKING",
                        linkType,
                        redirectCount > 0
                                ? "Redirect successful. Final destination is working"
                                : "Link is working",
                        currentUrl,
                        redirectCount
                );
            }

            return new LinkCheckResult(
                    statusCode,
                    "BROKEN",
                    linkType,
                    "Final destination returned HTTP error: " + statusCode,
                    currentUrl,
                    redirectCount
            );
        }
    }

    private boolean isRedirect(int statusCode) {

        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private static boolean isPublicAddress(String host) {

        try {

            InetAddress[] addresses = InetAddress.getAllByName(host);

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

    private String getLinkType(URI baseUri, URI linkUri) {

        if (baseUri.getHost() != null
                && baseUri.getHost().equalsIgnoreCase(linkUri.getHost())) {

            return "INTERNAL";
        }

        return "EXTERNAL";
    }

    private record LinkCheckResult(
            int statusCode,
            String status,
            String linkType,
            String message,
            String finalUrl,
            int redirectCount
    ) {}
}