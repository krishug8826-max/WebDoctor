package com.webdoctor.backend.history.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.webdoctor.backend.auth.entity.AppUser;
import com.webdoctor.backend.history.entity.ScanHistory;

public interface ScanHistoryRepository extends JpaRepository<ScanHistory, Long> {

    List<ScanHistory> findTop50ByOrderByScannedAtDesc();

    List<ScanHistory> findTop50ByUserOrderByScannedAtDesc(AppUser user);

    Optional<ScanHistory> findByIdAndUser(Long id, AppUser user);

    @Query(
        value = """
            SELECT h FROM ScanHistory h
            WHERE h.user = :user
            AND (
                :search IS NULL
                OR :search = ''
                OR LOWER(h.url) LIKE LOWER(CONCAT('%', :search, '%'))
            )
            AND (
                :reachable IS NULL
                OR h.reachable = :reachable
            )
            AND (
                :fromDate IS NULL
                OR h.scannedAt >= :fromDate
            )
            AND (
                :toDate IS NULL
                OR h.scannedAt < :toDate
            )
            """,
        countQuery = """
            SELECT COUNT(h) FROM ScanHistory h
            WHERE h.user = :user
            AND (
                :search IS NULL
                OR :search = ''
                OR LOWER(h.url) LIKE LOWER(CONCAT('%', :search, '%'))
            )
            AND (
                :reachable IS NULL
                OR h.reachable = :reachable
            )
            AND (
                :fromDate IS NULL
                OR h.scannedAt >= :fromDate
            )
            AND (
                :toDate IS NULL
                OR h.scannedAt < :toDate
            )
            """
    )
    Page<ScanHistory> findHistoryPage(
            @Param("user") AppUser user,
            @Param("search") String search,
            @Param("reachable") Boolean reachable,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );
}