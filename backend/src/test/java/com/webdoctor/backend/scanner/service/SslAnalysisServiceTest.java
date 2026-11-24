package com.webdoctor.backend.scanner.service;

import com.webdoctor.backend.scanner.dto.SslAnalysisResponse;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;

import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SslAnalysisServiceTest {

    private final SslAnalysisService service =
            new SslAnalysisService();

    @Test
    void shouldDetectWhenWebsiteDoesNotUseHttps() {

        SslAnalysisResponse result =
                service.analyzeSsl("http://example.com");

        assertFalse(result.secure());
        assertEquals("example.com", result.domain());
        assertEquals("Website does not use HTTPS", result.message());
        assertEquals("Not Available", result.issuer());
    }

    @Test
    void shouldDetectValidSslCertificate() throws Exception {

        Instant now = Instant.now();

        X509Certificate certificate = createCertificate(
                now.minusSeconds(86400),
                now.plusSeconds(90L * 86400)
        );

        SslAnalysisResponse result =
                analyzeWithCertificate(certificate);

        assertTrue(result.secure());
        assertEquals("example.com", result.domain());
        assertEquals("CN=Test CA", result.issuer());
        assertEquals("SSL certificate is valid", result.message());
        assertTrue(result.daysUntilExpiry() > 30);
    }

    @Test
    void shouldDetectExpiredCertificate() throws Exception {

        Instant now = Instant.now();

        X509Certificate certificate = createCertificate(
                now.minusSeconds(90L * 86400),
                now.minusSeconds(86400)
        );

        SslAnalysisResponse result =
                analyzeWithCertificate(certificate);

        assertFalse(result.secure());
        assertEquals("SSL certificate has expired", result.message());
        assertTrue(result.daysUntilExpiry() < 0);
    }

    @Test
    void shouldDetectCertificateThatIsNotYetValid() throws Exception {

        Instant now = Instant.now();

        X509Certificate certificate = createCertificate(
                now.plusSeconds(86400),
                now.plusSeconds(90L * 86400)
        );

        SslAnalysisResponse result =
                analyzeWithCertificate(certificate);

        assertFalse(result.secure());
        assertEquals(
                "SSL certificate is not yet valid",
                result.message()
        );
    }

    @Test
    void shouldDetectCertificateExpiringSoon() throws Exception {

        Instant now = Instant.now();

        X509Certificate certificate = createCertificate(
                now.minusSeconds(86400),
                now.plusSeconds(20L * 86400)
        );

        SslAnalysisResponse result =
                analyzeWithCertificate(certificate);

        assertTrue(result.secure());
        assertEquals(
                "SSL certificate is valid but expires soon",
                result.message()
        );

        assertTrue(result.daysUntilExpiry() <= 30);
    }

    @Test
    void shouldHandleSslVerificationFailure() throws Exception {

        HttpsURLConnection connection =
                mock(HttpsURLConnection.class);

        when(connection.getServerCertificates())
                .thenThrow(new SSLPeerUnverifiedException("Verification failed"));

        SslAnalysisResponse result =
                analyzeWithConnection(connection);

        assertFalse(result.secure());

        assertEquals(
                "SSL certificate could not be verified",
                result.message()
        );

        verify(connection).disconnect();
    }

    @Test
    void shouldHandleConnectionException() throws Exception {

        HttpsURLConnection connection =
                mock(HttpsURLConnection.class);

        when(connection.getServerCertificates())
                .thenThrow(new RuntimeException("Connection failed"));

        SslAnalysisResponse result =
                analyzeWithConnection(connection);

        assertFalse(result.secure());
        assertTrue(result.message().contains("SSL analysis failed"));
        verify(connection).disconnect();
    }

    @Test
    void shouldHandleInvalidUrl() {

        SslAnalysisResponse result =
                service.analyzeSsl("invalid url");

        assertFalse(result.secure());
        assertEquals("Unknown", result.domain());
        assertTrue(result.message().startsWith("SSL analysis failed"));
    }

    private X509Certificate createCertificate(
            Instant validFrom,
            Instant validUntil
    ) {

        X509Certificate certificate =
                mock(X509Certificate.class);

        when(certificate.getNotBefore())
                .thenReturn(Date.from(validFrom));

        when(certificate.getNotAfter())
                .thenReturn(Date.from(validUntil));

        when(certificate.getIssuerX500Principal())
                .thenReturn(new X500Principal("CN=Test CA"));

        return certificate;
    }

    private SslAnalysisResponse analyzeWithCertificate(
            X509Certificate certificate
    ) throws Exception {

        HttpsURLConnection connection =
                mock(HttpsURLConnection.class);

        when(connection.getServerCertificates())
                .thenReturn(new Certificate[]{certificate});

        return analyzeWithConnection(connection);
    }

    private SslAnalysisResponse analyzeWithConnection(
            HttpsURLConnection connection
    ) throws Exception {

        try (MockedConstruction<URL> mockedUrls =
                     Mockito.mockConstruction(
                             URL.class,
                             (mock, context) -> {

                                 when(mock.getHost())
                                         .thenReturn("example.com");

                                 when(mock.getProtocol())
                                         .thenReturn("https");

                                 when(mock.openConnection())
                                         .thenReturn(connection);
                             }
                     )) {

            return service.analyzeSsl("https://example.com");
        }
    }
}