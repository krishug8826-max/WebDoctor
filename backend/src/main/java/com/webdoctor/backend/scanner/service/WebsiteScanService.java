package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.ScanResponse;
import com.webdoctor.backend.scanner.dto.SslAnalysisResponse;
import com.webdoctor.backend.scanner.dto.SecurityAnalysisResponse;
import com.webdoctor.backend.scanner.dto.PerformanceAnalysisResponse;
import com.webdoctor.backend.scanner.dto.SeoAnalysisResponse;
import com.webdoctor.backend.scanner.dto.BrokenLinkAnalysisResponse;
import com.webdoctor.backend.scanner.dto.JavaScriptAnalysisResponse;
import com.webdoctor.backend.scanner.dto.MobileAnalysisResponse;
import com.webdoctor.backend.scanner.dto.ApiAnalysisResponse;
import com.webdoctor.backend.scanner.dto.LighthouseAnalysisResponse;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class WebsiteScanService {

    private final HttpClient httpClient;

    private final SslAnalysisService sslAnalysisService;

    private final SecurityAnalysisService securityAnalysisService;

    private final PerformanceAnalysisService performanceAnalysisService;

    private final SeoAnalysisService seoAnalysisService;

    private final BrokenLinkAnalysisService brokenLinkAnalysisService;

    private final JavaScriptAnalysisService javaScriptAnalysisService;

    private final MobileAnalysisService mobileAnalysisService;

    private final ApiAnalysisService apiAnalysisService;

    private final LighthouseAnalysisService lighthouseAnalysisService;

    public WebsiteScanService(
            SslAnalysisService sslAnalysisService,
            SecurityAnalysisService securityAnalysisService,
            PerformanceAnalysisService performanceAnalysisService,
            SeoAnalysisService seoAnalysisService,
            BrokenLinkAnalysisService brokenLinkAnalysisService,
            JavaScriptAnalysisService javaScriptAnalysisService,
            MobileAnalysisService mobileAnalysisService,
            ApiAnalysisService apiAnalysisService,
            LighthouseAnalysisService lighthouseAnalysisService) {

        this.sslAnalysisService = sslAnalysisService;
        this.securityAnalysisService = securityAnalysisService;
        this.performanceAnalysisService = performanceAnalysisService;
        this.seoAnalysisService = seoAnalysisService;
        this.brokenLinkAnalysisService = brokenLinkAnalysisService;
        this.javaScriptAnalysisService = javaScriptAnalysisService;
        this.mobileAnalysisService = mobileAnalysisService;
        this.apiAnalysisService = apiAnalysisService;
        this.lighthouseAnalysisService = lighthouseAnalysisService;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public ScanResponse scanWebsite(String url) {

        long startTime = System.currentTimeMillis();

        try {

            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException(
                        "Website URL is required");
            }

            URI uri = URI.create(url.trim()).normalize();

            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http")
                    || scheme.equalsIgnoreCase("https"))
                    || host == null
                    || uri.getUserInfo() != null) {

                throw new IllegalArgumentException(
                        "Invalid website URL");
            }

            // Check website IP address
            InetAddress[] addresses =
                    InetAddress.getAllByName(host);

            if (addresses.length == 0) {
                throw new IllegalArgumentException(
                        "Website host not found");
            }

            for (InetAddress address : addresses) {

                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {

                    throw new IllegalArgumentException(
                            "Private or local addresses are not allowed");
                }
            }

            // Send website request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "WebDoctor-Scanner/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            long responseTime =
                    System.currentTimeMillis() - startTime;

            int statusCode = response.statusCode();

            Map<String, List<String>> headers =
                    response.headers().map();

            // 1. SSL Analysis
            SslAnalysisResponse sslReport =
                    sslAnalysisService.analyzeSsl(url);

            // 2. Security Analysis
            SecurityAnalysisResponse securityReport =
                    securityAnalysisService.analyze(url, headers);

            // 3. Existing HTTP Performance Analysis
            PerformanceAnalysisResponse performanceReport =
                    performanceAnalysisService.analyze(
                            responseTime,
                            headers
                    );

            String contentType = headers.entrySet()
                    .stream()
                    .filter(entry -> entry.getKey()
                            .equalsIgnoreCase("content-type"))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst()
                    .orElse("");

            SeoAnalysisResponse seoReport = null;

            BrokenLinkAnalysisResponse brokenLinkReport = null;

            JavaScriptAnalysisResponse javaScriptReport = null;

            MobileAnalysisResponse mobileReport = null;

            ApiAnalysisResponse apiReport = null;

            LighthouseAnalysisResponse lighthouseReport = null;

            boolean isHtml = contentType.toLowerCase()
                    .contains("text/html")
                    || contentType.toLowerCase()
                    .contains("application/xhtml+xml");

            if (isHtml) {

                String html = response.body();

                // 4. SEO Analysis
                seoReport = seoAnalysisService.analyze(html);

                // 5. Broken Link Analysis
                brokenLinkReport =
                        brokenLinkAnalysisService.analyze(url, html);

                // 6. JavaScript Analysis
                javaScriptReport =
                        javaScriptAnalysisService.analyze(url, html);

                // 7. Mobile Responsiveness Analysis
                mobileReport =
                        mobileAnalysisService.analyze(html);

                // 8. API Analysis
                apiReport =
                        apiAnalysisService.analyze(url, html);

                // 9. Real Lighthouse Mobile + Desktop Analysis
                lighthouseReport =
                        lighthouseAnalysisService.analyze(url);
            }

            return new ScanResponse(
                    url,
                    statusCode >= 200 && statusCode < 400,
                    statusCode,
                    responseTime,
                    "Website scan completed successfully",
                    sslReport,
                    securityReport,
                    performanceReport,
                    seoReport,
                    brokenLinkReport,
                    javaScriptReport,
                    mobileReport,
                    apiReport,
                    lighthouseReport
            );

        } catch (IllegalArgumentException e) {

            throw e;

        } catch (Exception e) {

            long responseTime =
                    System.currentTimeMillis() - startTime;

            return new ScanResponse(
                    url,
                    false,
                    0,
                    responseTime,
                    "Website could not be reached",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}