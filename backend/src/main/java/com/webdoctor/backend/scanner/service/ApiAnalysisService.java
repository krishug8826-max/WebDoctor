package com.webdoctor.backend.scanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webdoctor.backend.scanner.dto.ApiAnalysisResponse;
import com.webdoctor.backend.scanner.dto.ApiFinding;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ApiAnalysisService {

    private static final int MAX_ENDPOINTS = 20;
    private static final int MAX_EXTERNAL_SCRIPTS = 10;
    private static final int MAX_SCRIPT_SIZE = 1024 * 1024;
    private static final int MAX_RESPONSE_SIZE = 1024 * 1024;

    private static final Pattern FETCH_PATTERN = Pattern.compile(
            "\\bfetch\\s*\\(\\s*(['\"])([^'\"]+)\\1",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AXIOS_PATTERN = Pattern.compile(
            "\\baxios\\s*\\.\\s*(get|post|put|patch|delete|head)"
                    + "\\s*\\(\\s*(['\"`])([^'\"`]+)\\2",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AXIOS_CREATE_PATTERN = Pattern.compile(
            "(?:const|let|var)\\s+(\\w+)\\s*=\\s*axios\\.create\\s*\\("
                    + "\\s*\\{[\\s\\S]{0,1500}?baseURL\\s*:\\s*(['\"`])"
                    + "([^'\"`]+)\\2",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AXIOS_INSTANCE_PATTERN = Pattern.compile(
            "\\b(\\w+)\\s*\\.\\s*(get|post|put|patch|delete|head)"
                    + "\\s*\\(\\s*(['\"`])([^'\"`]+)\\3",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern XHR_PATTERN = Pattern.compile(
            "\\.open\\s*\\(\\s*(['\"])(GET|POST|PUT|PATCH|DELETE|HEAD)"
                    + "\\1\\s*,\\s*(['\"`])([^'\"`]+)\\3",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JQUERY_PATTERN = Pattern.compile(
            "\\$\\.ajax\\s*\\(\\s*\\{[\\s\\S]*?url\\s*:"
                    + "\\s*(['\"`])([^'\"`]+)\\1",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JQUERY_SHORTCUT_PATTERN = Pattern.compile(
        "\\$\\s*\\.\\s*(get|post)\\s*\\(\\s*(['\"`])([^'\"`]+)\\2",
        Pattern.CASE_INSENSITIVE
);

    private static final Pattern URL_PROPERTY_PATTERN = Pattern.compile(
            "\\burl\\s*:\\s*(['\"`])([^'\"`]+)\\1",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DYNAMIC_TEMPLATE_PATTERN = Pattern.compile(
            "\\b(?:fetch|axios\\s*\\.\\s*(?:get|post|put|patch|delete)|"
                    + "\\w+\\s*\\.\\s*(?:get|post|put|patch|delete))"
                    + "\\s*\\(\\s*`([^`]*\\$\\{[^}]+}[^`]*)`",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GRAPHQL_PATTERN = Pattern.compile(
            "\\b(?:graphql|gql|ApolloClient|HttpLink)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GRAPHQL_URI_PATTERN = Pattern.compile(
            "\\buri\\s*:\\s*(['\"`])([^'\"`]+)\\1",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ApiAnalysisService() {

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        this.objectMapper = new ObjectMapper();
    }

    // =====================================================
    // MAIN ANALYSIS
    // =====================================================

    public ApiAnalysisResponse analyze(
            String websiteUrl,
            String html
    ) {

        List<ApiFinding> findings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        Map<String, EndpointCandidate> endpoints =
                new LinkedHashMap<>();

        int totalScriptsFound = 0;
        int inlineScriptsScanned = 0;
        int externalScriptsFound = 0;
        int externalScriptsDownloaded = 0;
        int apiPatternsDetected = 0;

        int working = 0;
        int broken = 0;
        int redirected = 0;
        int authenticationRequired = 0;
        int errors = 0;
        int skipped = 0;

        int jsonEndpoints = 0;
        int invalidJsonEndpoints = 0;

        long totalResponseTime = 0;
        long fastestResponseTime = Long.MAX_VALUE;
        long slowestResponseTime = 0;
        int responseTimeCount = 0;

        try {

            URI baseUri = URI.create(websiteUrl).normalize();

            if (!isAllowedUri(baseUri)) {

                throw new IllegalArgumentException(
                        "Website URL is not allowed for scanning"
                );
            }

            Document document = Jsoup.parse(html, websiteUrl);

            // 1. HTML links

            Elements links = document.select("a[href]");

            for (Element link : links) {

                addApiEndpoint(
                        endpoints,
                        baseUri,
                        link.attr("href"),
                        "GET",
                        "HTML_LINK",
                        "LOW"
                );
            }

            // 2. GET forms

            Elements forms = document.select("form[action]");

            for (Element form : forms) {

                String method = form.attr("method").trim();

                if (method.isBlank()
                        || method.equalsIgnoreCase("get")) {

                    addApiEndpoint(
                            endpoints,
                            baseUri,
                            form.attr("action"),
                            "GET",
                            "HTML_FORM",
                            "MEDIUM"
                    );
                }
            }

            // 3. Data attributes

            Elements dataElements = document.select(
                    "[data-api], [data-endpoint]"
            );

            for (Element element : dataElements) {

                String value = element.hasAttr("data-api")
                        ? element.attr("data-api")
                        : element.attr("data-endpoint");

                addApiEndpoint(
                        endpoints,
                        baseUri,
                        value,
                        "GET",
                        "DATA_ATTRIBUTE",
                        "MEDIUM"
                );
            }

            // 4. Inline JavaScript

            Elements inlineScripts = document.select(
                    "script:not([src])"
            );

            totalScriptsFound += inlineScripts.size();

            for (Element script : inlineScripts) {

                inlineScriptsScanned++;

                apiPatternsDetected += detectJavaScriptEndpoints(
                        endpoints,
                        baseUri,
                        script.data()
                );
            }

            // 5. External JavaScript

            Elements externalScripts = document.select("script[src]");

            externalScriptsFound = externalScripts.size();

            int scriptsAttempted = 0;

            for (Element script : externalScripts) {

                if (scriptsAttempted >= MAX_EXTERNAL_SCRIPTS) {
                    break;
                }

                String scriptUrl = script.attr("abs:src");

                if (scriptUrl == null || scriptUrl.isBlank()) {
                    continue;
                }

                try {

                    URI scriptUri = URI.create(scriptUrl).normalize();

                    if (!isAllowedUri(scriptUri)
                            || !isPublicAddress(scriptUri.getHost())) {
                        continue;
                    }

                    scriptsAttempted++;

                    String javascript = fetchExternalScript(scriptUri);

                    if (javascript != null && !javascript.isBlank()) {

                        externalScriptsDownloaded++;
                        totalScriptsFound++;

                        apiPatternsDetected += detectJavaScriptEndpoints(
                                endpoints,
                                baseUri,
                                javascript
                        );
                    }

                } catch (Exception ignored) {
                    // External script could not be inspected.
                }
            }

            // 6. HTML URL attributes

            Elements urlElements = document.select(
                    "[action], [data-url], [data-href]"
            );

            for (Element element : urlElements) {

                for (String attribute : List.of(
                        "action",
                        "data-url",
                        "data-href"
                )) {

                    if (element.hasAttr(attribute)) {

                        addApiEndpoint(
                                endpoints,
                                baseUri,
                                element.attr(attribute),
                                "GET",
                                "HTML_ATTRIBUTE",
                                "LOW"
                        );
                    }
                }
            }

            // 7. Test discovered endpoints

            int checked = 0;

            for (EndpointCandidate candidate : endpoints.values()) {

                if (checked >= MAX_ENDPOINTS) {
                    break;
                }

                checked++;

                if (candidate.dynamic()) {

                    skipped++;

                    findings.add(createFinding(
                            candidate,
                            0,
                            0,
                            "SKIPPED",
                            "NOT_TESTED",
                            "Not Available",
                            false,
                            false,
                            "Dynamic endpoint requires runtime values.",
                            "Use authorized browser or source-code analysis."
                    ));

                    continue;
                }

                if (!candidate.method().equals("GET")) {

                    skipped++;

                    findings.add(createFinding(
                            candidate,
                            0,
                            0,
                            "SKIPPED",
                            "NOT_TESTED",
                            "Not Available",
                            false,
                            false,
                            "Non-GET request was not executed for safety.",
                            "Test this endpoint in an authorized environment."
                    ));

                    continue;
                }

                try {

                    URI endpointUri = URI.create(
                            candidate.endpoint()
                    ).normalize();

                    if (!isAllowedUri(endpointUri)
                            || !isSameOrigin(baseUri, endpointUri)
                            || !isPublicAddress(endpointUri.getHost())) {

                        skipped++;

                        findings.add(createFinding(
                                candidate,
                                0,
                                0,
                                "SKIPPED",
                                "NOT_TESTED",
                                "Not Available",
                                false,
                                false,
                                "Endpoint is outside the permitted scan scope.",
                                "Verify the endpoint and scanning authorization."
                        ));

                        continue;
                    }

                    long startTime = System.currentTimeMillis();

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(endpointUri)
                            .timeout(Duration.ofSeconds(10))
                            .header(
                                    "User-Agent",
                                    "WebDoctor-Scanner/1.0"
                            )
                            .header(
                                    "Accept",
                                    "application/json, */*"
                            )
                            .GET()
                            .build();

                    HttpResponse<InputStream> response = httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofInputStream()
                    );

                    long responseTime =
                            System.currentTimeMillis() - startTime;

                    int statusCode = response.statusCode();

                    String contentType = response.headers()
                            .firstValue("content-type")
                            .orElse("Unknown");

                    String lowerContentType =
                            contentType.toLowerCase(Locale.ROOT);

                    boolean isJson =
                            lowerContentType.contains("application/json")
                                    || lowerContentType.contains("+json");

                    boolean validJson = false;
                    String message;
                    String recommendation;
                    String status;
                    String category;

                    String responseBody = "";

                    try (InputStream input = response.body()) {

                        byte[] body = input.readNBytes(
                                MAX_RESPONSE_SIZE + 1
                        );

                        if (body.length <= MAX_RESPONSE_SIZE) {

                            responseBody = new String(
                                    body,
                                    StandardCharsets.UTF_8
                            );
                        }
                    }

                    if (isJson) {

                        jsonEndpoints++;

                        if (!responseBody.isBlank()) {

                            try {

                                objectMapper.readTree(responseBody);
                                validJson = true;

                            } catch (Exception ignored) {

                                invalidJsonEndpoints++;
                            }
                        }
                    }

                    totalResponseTime += responseTime;
                    responseTimeCount++;

                    fastestResponseTime = Math.min(
                            fastestResponseTime,
                            responseTime
                    );

                    slowestResponseTime = Math.max(
                            slowestResponseTime,
                            responseTime
                    );

                    if (statusCode >= 200 && statusCode < 300) {

                        status = "WORKING";
                        category = "SUCCESS";
                        message = "API responded successfully.";

                        if (isJson && !validJson) {

                            message = "API returned an empty, oversized or invalid JSON response.";
                            recommendation = "Verify the API response body and JSON content-type.";

                            if (!responseBody.isBlank()) {
                                recommendation = "Check the response body for malformed JSON.";
                            }

                        } else {

                            recommendation = "No immediate action required.";
                        }

                        working++;

                    } else if (statusCode == 401 || statusCode == 403) {

                        status = "AUTHENTICATION_REQUIRED";
                        category = "AUTHENTICATION";
                        message = statusCode == 401
                                ? "API requires valid authentication."
                                : "API access is forbidden or restricted.";

                        recommendation = "Verify authentication credentials, tokens and access permissions.";

                        authenticationRequired++;

                    } else if (statusCode >= 300 && statusCode < 400) {

                        status = "REDIRECT";
                        category = "REDIRECTION";
                        message = "API returned an HTTP redirect.";

                        recommendation = "Verify the redirect destination and API route.";

                        redirected++;

                    } else if (statusCode >= 400 && statusCode < 500) {

                        status = "BROKEN";
                        category = "CLIENT_ERROR";
                        message = "API returned a client error: HTTP " + statusCode;

                        recommendation = "Check the endpoint path, request parameters and required headers.";

                        broken++;

                    } else if (statusCode >= 500) {

                        status = "BROKEN";
                        category = "SERVER_ERROR";
                        message = "API returned a server error: HTTP " + statusCode;

                        recommendation = "Inspect server logs and backend API implementation.";

                        broken++;

                    } else {

                        status = "ERROR";
                        category = "UNKNOWN";
                        message = "Unexpected HTTP response.";

                        recommendation = "Review the endpoint response.";

                        errors++;
                    }

                    if (isJson && !validJson && statusCode >= 200
                            && statusCode < 300) {

                        recommendation =
                                "Verify that the response contains valid JSON.";
                    }

                    findings.add(createFinding(
                            candidate,
                            statusCode,
                            responseTime,
                            status,
                            category,
                            contentType,
                            isJson,
                            validJson,
                            message,
                            recommendation
                    ));

                } catch (java.net.http.HttpTimeoutException e) {

                    errors++;

                    findings.add(createFinding(
                            candidate,
                            0,
                            0,
                            "ERROR",
                            "TIMEOUT",
                            "Unknown",
                            false,
                            false,
                            "API request timed out.",
                            "Check API response time and server availability."
                    ));

                } catch (Exception e) {

                    errors++;

                    findings.add(createFinding(
                            candidate,
                            0,
                            0,
                            "ERROR",
                            "CONNECTION_ERROR",
                            "Unknown",
                            false,
                            false,
                            "Unable to connect to the API endpoint.",
                            "Check DNS, network connectivity and endpoint availability."
                    ));
                }
            }

            int totalEndpoints = endpoints.size();

            skipped += Math.max(
                    0,
                    totalEndpoints - checked
            );

            // 8. Calculate API health score

            int assessed = working
                    + broken
                    + redirected
                    + authenticationRequired
                    + errors;

            int score;
String grade;
String healthStatus;

if (assessed == 0) {
    score = 0;
    grade = "N/A";
    healthStatus = "NOT_ASSESSED";
} else {
    score = calculateHealthScore(
            working,
            broken,
            redirected,
            authenticationRequired,
            errors
    );

    grade = calculateGrade(score);
    healthStatus = calculateHealthStatus(score);
}

            // 9. Generate recommendations

            generateRecommendations(
                    recommendations,
                    broken,
                    authenticationRequired,
                    redirected,
                    errors,
                    invalidJsonEndpoints,
                    slowestResponseTime
            );

           if (totalEndpoints == 0) {

    recommendations.clear();

    recommendations.add(
            "No API endpoints were detected in the inspected HTML and JavaScript."
    );

    recommendations.add(
            "Some API calls may be dynamically generated or require browser runtime analysis."
    );

} else if (assessed == 0) {

    recommendations.add(
            "Endpoints were detected, but none could be actively tested."
    );

    recommendations.add(
            "Use authorized browser or source-code analysis for deeper inspection."
    );
}

            long averageResponseTime = responseTimeCount == 0
                    ? 0
                    : totalResponseTime / responseTimeCount;

            long fastest = fastestResponseTime == Long.MAX_VALUE
                    ? 0
                    : fastestResponseTime;

            return new ApiAnalysisResponse(
                    score,
                    grade,
                    healthStatus,
                    totalEndpoints,
                    checked,
                    working,
                    broken,
                    redirected,
                    authenticationRequired,
                    errors,
                    skipped,
                    jsonEndpoints,
                    invalidJsonEndpoints,
                    averageResponseTime,
                    fastest,
                    slowestResponseTime,
                    totalScriptsFound,
                    inlineScriptsScanned,
                    externalScriptsFound,
                    externalScriptsDownloaded,
                    apiPatternsDetected,
                    findings,
                    recommendations,
                    totalEndpoints == 0
        ? "No API endpoints were detected. API health could not be assessed."
        : assessed == 0
            ? "API endpoints were detected, but none could be actively tested."
            : "API analysis completed successfully."
            );

        } catch (Exception e) {

            return new ApiAnalysisResponse(
                    0,
                    "N/A",
                    "ANALYSIS_FAILED",
                    endpoints.size(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    totalScriptsFound,
                    inlineScriptsScanned,
                    externalScriptsFound,
                    externalScriptsDownloaded,
                    apiPatternsDetected,
                    findings,
                    List.of(
                            "Verify the website URL and HTML content.",
                            "Check whether the website is publicly reachable."
                    ),
                    "API analysis could not be completed."
            );
        }
    }

    // =====================================================
    // JAVASCRIPT DETECTION
    // =====================================================

    private int detectJavaScriptEndpoints(
            Map<String, EndpointCandidate> endpoints,
            URI baseUri,
            String javascript
    ) {

        if (javascript == null || javascript.isBlank()) {
            return 0;
        }

        int initialEndpointCount = endpoints.size();
        Matcher fetchMatcher = FETCH_PATTERN.matcher(javascript);

        while (fetchMatcher.find()) {

       

            addApiEndpoint(
                    endpoints,
                    baseUri,
                    fetchMatcher.group(2),
                    "GET",
                    "FETCH_API",
                    "HIGH"
            );
        }

        Matcher axiosMatcher = AXIOS_PATTERN.matcher(javascript);

        while (axiosMatcher.find()) {

            

            addApiEndpoint(
                    endpoints,
                    baseUri,
                    axiosMatcher.group(3),
                    axiosMatcher.group(1).toUpperCase(Locale.ROOT),
                    "AXIOS",
                    "HIGH"
            );
        }

        Map<String, String> axiosInstances = new LinkedHashMap<>();

        Matcher createMatcher = AXIOS_CREATE_PATTERN.matcher(javascript);

        while (createMatcher.find()) {

            axiosInstances.put(
                    createMatcher.group(1),
                    createMatcher.group(3)
            );

           
        }

        Matcher instanceMatcher =
                AXIOS_INSTANCE_PATTERN.matcher(javascript);

        while (instanceMatcher.find()) {

            String instanceName = instanceMatcher.group(1);

            String baseURL = axiosInstances.get(instanceName);

            if (baseURL == null) {
                continue;
            }

            String combinedUrl = combineAxiosUrl(
                    baseURL,
                    instanceMatcher.group(4)
            );

            addApiEndpoint(
                    endpoints,
                    baseUri,
                    combinedUrl,
                    instanceMatcher.group(2).toUpperCase(Locale.ROOT),
                    "AXIOS_INSTANCE",
                    "HIGH"
            );

        
        }

        Matcher xhrMatcher = XHR_PATTERN.matcher(javascript);

        while (xhrMatcher.find()) {

            

            addApiEndpoint(
                    endpoints,
                    baseUri,
                    xhrMatcher.group(4),
                    xhrMatcher.group(2).toUpperCase(Locale.ROOT),
                    "XML_HTTP_REQUEST",
                    "HIGH"
            );
        }

        Matcher jqueryMatcher = JQUERY_PATTERN.matcher(javascript);

        while (jqueryMatcher.find()) {

         

            addApiEndpoint(
                    endpoints,
                    baseUri,
                    jqueryMatcher.group(2),
                    "GET",
                    "JQUERY_AJAX",
                    "MEDIUM"
            );
        }
        Matcher jqueryShortcutMatcher =
                JQUERY_SHORTCUT_PATTERN.matcher(javascript);

        while (jqueryShortcutMatcher.find()) {

            

            addApiEndpoint(
                    endpoints,
                    baseUri,
                    jqueryShortcutMatcher.group(3),
                    jqueryShortcutMatcher.group(1).toUpperCase(Locale.ROOT),
                    "JQUERY_SHORTCUT",
                    "MEDIUM"
            );
        }
        Matcher urlMatcher = URL_PROPERTY_PATTERN.matcher(javascript);

        while (urlMatcher.find()) {

         

            addApiEndpoint(
                    endpoints,
                    baseUri,
                    urlMatcher.group(2),
                    "GET",
                    "GENERIC_URL_PROPERTY",
                    "LOW"
            );
        }

        Matcher dynamicMatcher =
                DYNAMIC_TEMPLATE_PATTERN.matcher(javascript);

        while (dynamicMatcher.find()) {

          

            addApiEndpoint(
                    endpoints,
                    baseUri,
                    dynamicMatcher.group(1),
                    "GET",
                    "DYNAMIC_TEMPLATE",
                    "MEDIUM"
            );
        }

        if (GRAPHQL_PATTERN.matcher(javascript).find()) {

            

            Matcher graphqlMatcher =
                    GRAPHQL_URI_PATTERN.matcher(javascript);

            while (graphqlMatcher.find()) {

                

                addApiEndpoint(
                        endpoints,
                        baseUri,
                        graphqlMatcher.group(2),
                        "POST",
                        "GRAPHQL",
                        "HIGH"
                );
            }
        }

        return endpoints.size() - initialEndpointCount;
    }

    // =====================================================
    // ENDPOINT REGISTRATION
    // =====================================================

    private void addApiEndpoint(
            Map<String, EndpointCandidate> endpoints,
            URI baseUri,
            String value,
            String method,
            String source,
            String confidence
    ) {

        if (value == null || value.isBlank()) {
            return;
        }

        try {

            String cleanValue = value.trim();

            boolean dynamic = cleanValue.contains("${");

            if (dynamic) {

                int position = cleanValue.indexOf("${");

                cleanValue = cleanValue.substring(0, position);

                if (cleanValue.isBlank()) {
                    return;
                }
            }

            URI endpointUri = baseUri.resolve(cleanValue).normalize();

            if (!isAllowedUri(endpointUri)) {
                return;
            }

            String path = endpointUri.getPath();

            if (path == null) {
                return;
            }

            String lowerPath = path.toLowerCase(Locale.ROOT);

           boolean isApi =
                    
                 lowerPath.startsWith("/api")
                || lowerPath.contains("/v1/")
                || lowerPath.contains("/v2/")
                || lowerPath.matches(".*/v\\d+(/.*)?")
                || lowerPath.contains("/rest/")
                || lowerPath.contains("/rest.php")
                || lowerPath.endsWith("/graphql")
                || lowerPath.contains("/graphql/")
                || lowerPath.endsWith("/gql")
                || lowerPath.endsWith("/api.php")
                || lowerPath.contains("/services/")
                || lowerPath.contains("/wp-json/")
                || lowerPath.contains("/odata/")
                || lowerPath.contains("/jsonrpc")
                || lowerPath.contains("/rpc/");

            if (!isApi) {
                return;
            }

            String normalizedMethod = method == null
                    ? "GET"
                    : method.toUpperCase(Locale.ROOT);

            String endpoint = endpointUri.toString();

            if (dynamic) {
                endpoint += "{dynamic}";
            }

            EndpointCandidate candidate = new EndpointCandidate(
                    endpoint,
                    normalizedMethod,
                    source,
                    confidence,
                    dynamic
            );

            String key = endpoint + "|" + normalizedMethod;

            endpoints.putIfAbsent(key, candidate);

        } catch (Exception ignored) {
            // Ignore invalid endpoint.
        }
    }

    // =====================================================
    // AXIOS URL COMBINATION
    // =====================================================

    private String combineAxiosUrl(
            String baseURL,
            String path
    ) {

        if (path == null || path.isBlank()) {
            return baseURL;
        }

        try {

            URI pathUri = URI.create(path);

            if (pathUri.isAbsolute()) {
                return path;
            }

        } catch (Exception ignored) {
            return path;
        }

        return baseURL.replaceAll("/+$", "")
                + "/"
                + path.replaceAll("^/+", "");
    }

    // =====================================================
    // FINDING CREATION
    // =====================================================

    private ApiFinding createFinding(
            EndpointCandidate candidate,
            int statusCode,
            long responseTime,
            String status,
            String category,
            String contentType,
            boolean jsonResponse,
            boolean validJson,
            String message,
            String recommendation
    ) {

        return new ApiFinding(
                candidate.endpoint(),
                candidate.method(),
                statusCode,
                responseTime,
                status,
                category,
                contentType,
                jsonResponse,
                validJson,
                message,
                recommendation,
                candidate.source(),
                candidate.confidence()
        );
    }

    // =====================================================
    // HEALTH SCORE
    // =====================================================

    private int calculateHealthScore(
            int working,
            int broken,
            int redirected,
            int authentication,
            int errors
    ) {

        int total = working
                + broken
                + redirected
                + authentication
                + errors;

        if (total == 0) {
            return 0;
        }

        double points =
                (working * 100.0)
                        + (redirected * 80.0)
                        + (authentication * 75.0)
                        + (errors * 25.0);

        return (int) Math.round(points / total);
    }

    private String calculateGrade(int score) {

        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";

        return "F";
    }

    private String calculateHealthStatus(int score) {

        if (score >= 90) return "EXCELLENT";
        if (score >= 80) return "GOOD";
        if (score >= 70) return "FAIR";
        if (score >= 60) return "NEEDS_IMPROVEMENT";

        return "POOR";
    }

    // =====================================================
    // RECOMMENDATION ENGINE
    // =====================================================

    private void generateRecommendations(
            List<String> recommendations,
            int broken,
            int authentication,
            int redirected,
            int errors,
            int invalidJson,
            long slowestResponse
    ) {

        if (broken > 0) {

            recommendations.add(
                    "Inspect broken API endpoints and verify their routes and server responses."
            );
        }

        if (authentication > 0) {

            recommendations.add(
                    "Verify authentication configuration, API tokens and access permissions."
            );
        }

        if (redirected > 0) {

            recommendations.add(
                    "Review API redirects and prefer stable canonical API endpoints."
            );
        }

        if (errors > 0) {

            recommendations.add(
                    "Investigate connection failures, timeouts and unexpected API responses."
            );
        }

        if (invalidJson > 0) {

            recommendations.add(
                    "Check JSON response formatting and content-type headers."
            );
        }

        if (slowestResponse > 1000) {

            recommendations.add(
                    "Some API responses exceed one second. Investigate server processing time."
            );
        }

        if (recommendations.isEmpty()) {

            recommendations.add(
                    "No major API issues were detected in the endpoints that could be tested."
            );
        }
    }

    // =====================================================
    // EXTERNAL SCRIPT DOWNLOAD
    // =====================================================

    private String fetchExternalScript(URI scriptUri)
            throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(scriptUri)
                .timeout(Duration.ofSeconds(10))
                .header(
                        "User-Agent",
                        "WebDoctor-Scanner/1.0"
                )
                .header(
                        "Accept",
                        "application/javascript, text/javascript, */*"
                )
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        try (InputStream input = response.body()) {

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                return null;
            }

            String contentType = response.headers()
                    .firstValue("content-type")
                    .orElse("")
                    .toLowerCase(Locale.ROOT);

            if (!contentType.contains("javascript")
                    && !contentType.contains("ecmascript")
                    && !contentType.contains("text/plain")
                    && !contentType.isBlank()) {
                return null;
            }

            byte[] content = input.readNBytes(
                    MAX_SCRIPT_SIZE + 1
            );

            if (content.length > MAX_SCRIPT_SIZE) {
                return null;
            }

            return new String(
                    content,
                    StandardCharsets.UTF_8
            );
        }
    }

    // =====================================================
    // URI VALIDATION
    // =====================================================

    private boolean isAllowedUri(URI uri) {

        if (uri == null
                || uri.getScheme() == null
                || uri.getHost() == null
                || uri.getUserInfo() != null) {
            return false;
        }

        return uri.getScheme().equalsIgnoreCase("http")
                || uri.getScheme().equalsIgnoreCase("https");
    }

    // =====================================================
    // SAME ORIGIN VALIDATION
    // =====================================================

    private boolean isSameOrigin(
            URI baseUri,
            URI endpointUri
    ) {

        String baseScheme = baseUri.getScheme();
        String endpointScheme = endpointUri.getScheme();

        if (baseScheme == null || endpointScheme == null) {
            return false;
        }

        int basePort = baseUri.getPort() == -1
                ? baseScheme.equalsIgnoreCase("https") ? 443 : 80
                : baseUri.getPort();

        int endpointPort = endpointUri.getPort() == -1
                ? endpointScheme.equalsIgnoreCase("https") ? 443 : 80
                : endpointUri.getPort();

        return baseScheme.equalsIgnoreCase(endpointScheme)
                && baseUri.getHost().equalsIgnoreCase(
                        endpointUri.getHost()
                )
                && basePort == endpointPort;
    }

    // =====================================================
    // PUBLIC ADDRESS CHECK
    // =====================================================

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

    // =====================================================
    // INTERNAL MODEL
    // =====================================================

    private record EndpointCandidate(
            String endpoint,
            String method,
            String source,
            String confidence,
            boolean dynamic
    ) {}
    
}