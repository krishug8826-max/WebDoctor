package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.SslAnalysisResponse;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Service
public class SslAnalysisService {

    public SslAnalysisResponse analyzeSsl(String websiteUrl) {

        HttpsURLConnection connection = null;

        try {
            URL url = new URL(websiteUrl);

            String domain = url.getHost();

            // Check HTTPS protocol
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                return new SslAnalysisResponse(
                        false,
                        domain,
                        "Not Available",
                        "Not Available",
                        "Not Available",
                        0,
                        "Website does not use HTTPS"
                );
            }

            connection = (HttpsURLConnection) url.openConnection();

            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);

            connection.connect();

            // Get SSL certificates
            Certificate[] certificates =
                    connection.getServerCertificates();

            if (certificates == null || certificates.length == 0) {
                return errorResponse(
                        domain,
                        "No SSL certificate found"
                );
            }

            if (!(certificates[0] instanceof X509Certificate certificate)) {
                return errorResponse(
                        domain,
                        "Unsupported SSL certificate format"
                );
            }

            Instant now = Instant.now();

            Instant validFrom =
                    certificate.getNotBefore().toInstant();

            Instant validUntil =
                    certificate.getNotAfter().toInstant();

            long daysRemaining = Duration.between(
                    now,
                    validUntil
            ).toDays();

            // Check certificate validity
            boolean valid = !now.isBefore(validFrom)
                    && !now.isAfter(validUntil);

            String issuer =
                    certificate.getIssuerX500Principal().getName();

            String formattedValidFrom =
                    DateTimeFormatter.ISO_INSTANT.format(validFrom);

            String formattedValidUntil =
                    DateTimeFormatter.ISO_INSTANT.format(validUntil);

            String message;

            if (now.isBefore(validFrom)) {
                message = "SSL certificate is not yet valid";
            } else if (now.isAfter(validUntil)) {
                message = "SSL certificate has expired";
            } else if (daysRemaining <= 30) {
                message = "SSL certificate is valid but expires soon";
            } else {
                message = "SSL certificate is valid";
            }

            return new SslAnalysisResponse(
                    valid,
                    domain,
                    issuer,
                    formattedValidFrom,
                    formattedValidUntil,
                    daysRemaining,
                    message
            );

        } catch (SSLPeerUnverifiedException e) {

            return errorResponse(
                    websiteUrl,
                    "SSL certificate could not be verified"
            );

        } catch (Exception e) {

            return errorResponse(
                    websiteUrl,
                    "SSL analysis failed: " + e.getMessage()
            );

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private SslAnalysisResponse errorResponse(
            String websiteUrl,
            String message
    ) {

        String domain;

        try {
            domain = new URL(websiteUrl).getHost();
        } catch (Exception e) {
            domain = "Unknown";
        }

        return new SslAnalysisResponse(
                false,
                domain,
                "Not Available",
                "Not Available",
                "Not Available",
                0,
                message
        );
    }
}