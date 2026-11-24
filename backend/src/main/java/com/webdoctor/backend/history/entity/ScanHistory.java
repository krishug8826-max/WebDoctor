package com.webdoctor.backend.history.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import com.webdoctor.backend.auth.entity.AppUser;

@Entity
@Table(name = "scan_history")
public class ScanHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false)
    private boolean reachable;

    @Column(nullable = false)
    private int statusCode;

    @Column(nullable = false)
    private long responseTimeMs;

    @Column(nullable = false)
    private LocalDateTime scannedAt;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String responseJson;

    // Nullable to support existing historical records.
    @Column(name = "health_score")
    private Integer healthScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private AppUser user;

    protected ScanHistory() {
    }

    public ScanHistory(
            String url,
            boolean reachable,
            int statusCode,
            long responseTimeMs,
            LocalDateTime scannedAt,
            String responseJson) {

        this(url, reachable, statusCode, responseTimeMs,
                scannedAt, responseJson, null);
    }

    public ScanHistory(
            String url,
            boolean reachable,
            int statusCode,
            long responseTimeMs,
            LocalDateTime scannedAt,
            String responseJson,
            AppUser user) {

        this.url = url;
        this.reachable = reachable;
        this.statusCode = statusCode;
        this.responseTimeMs = responseTimeMs;
        this.scannedAt = scannedAt;
        this.responseJson = responseJson;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public boolean isReachable() {
        return reachable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public LocalDateTime getScannedAt() {
        return scannedAt;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public Integer getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(Integer healthScore) {
        this.healthScore = healthScore;
    }

    public AppUser getUser() {
        return user;
    }
}