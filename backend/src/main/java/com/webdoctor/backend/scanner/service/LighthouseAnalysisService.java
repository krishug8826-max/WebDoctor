package com.webdoctor.backend.scanner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webdoctor.backend.scanner.dto.LighthouseAnalysisResponse;
import com.webdoctor.backend.scanner.dto.LighthouseAnalysisResponse.LighthouseResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class LighthouseAnalysisService {

    private static final Logger log =
            LoggerFactory.getLogger(LighthouseAnalysisService.class);

    private static final long DEFAULT_TIMEOUT_SECONDS = 130;

    private final ObjectMapper objectMapper;
    private final Path runnerPath;
    private final long timeoutSeconds;

    // Production constructor — Spring uses this.
    // Production constructor — Spring uses this.
@Autowired

    public LighthouseAnalysisService(ObjectMapper objectMapper) {
        this(
                objectMapper,
                resolveRunnerPath(),
                DEFAULT_TIMEOUT_SECONDS
        );
    }

    // Package-private constructor — used by automated tests.
    LighthouseAnalysisService(
            ObjectMapper objectMapper,
            Path runnerPath,
            long timeoutSeconds
    ) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Timeout must be greater than zero."
            );
        }

        this.objectMapper = objectMapper;
        this.runnerPath = runnerPath.toAbsolutePath();
        this.timeoutSeconds = timeoutSeconds;
    }

    private static Path resolveRunnerPath() {

        Path workingDirectory = Path.of(
                System.getProperty("user.dir")
        ).toAbsolutePath();

        Path backendDirectory = workingDirectory.resolve("backend");

        if (Files.exists(
                backendDirectory.resolve("scripts")
                        .resolve("lighthouse-runner.mjs")
        )) {
            workingDirectory = backendDirectory;
        }

        return workingDirectory
                .resolve("scripts")
                .resolve("lighthouse-runner.mjs");
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
                    "Mobile and Desktop Lighthouse audits completed successfully.";

            case "PARTIAL" ->
                    "Only one Lighthouse audit completed successfully.";

            default ->
                    "Lighthouse audits could not be completed.";
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
            return runAudit(url, strategy);

        } catch (Exception e) {

            log.error(
                    "Lighthouse {} audit failed for URL: {}. Reason: {}",
                    strategy,
                    url,
                    e.getMessage(),
                    e
            );

            return failedResult(strategy);
        }
    }

    private LighthouseResult runAudit(
            String url,
            String strategy
    ) throws Exception {

        if (!Files.exists(runnerPath)) {
            throw new IOException(
                    "Lighthouse runner script not found: " + runnerPath
            );
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                "node",
                runnerPath.toString(),
                url,
                strategy
        );

        processBuilder.directory(
                runnerPath.getParent().getParent().toFile()
        );

        Process process = processBuilder.start();

        CompletableFuture<String> stdout =
                readOutput(process.getInputStream());

        CompletableFuture<String> stderr =
                readOutput(process.getErrorStream());

        boolean completed = process.waitFor(
                timeoutSeconds,
                TimeUnit.SECONDS
        );

        if (!completed) {

            log.error(
                    "Lighthouse {} audit timed out after {} seconds. Terminating process tree.",
                    strategy,
                    timeoutSeconds
            );

            process.descendants().forEach(child -> {
                try {
                    child.destroyForcibly();

                } catch (Exception e) {
                    log.warn(
                            "Could not terminate Lighthouse child process: {}",
                            child.pid(),
                            e
                    );
                }
            });

            process.destroyForcibly();

            process.waitFor(10, TimeUnit.SECONDS);

            stdout.cancel(true);
            stderr.cancel(true);

            throw new TimeoutException(
                    "Lighthouse " + strategy
                            + " audit timed out after "
                            + timeoutSeconds + " seconds."
            );
        }

        String output = stdout.get(10, TimeUnit.SECONDS);
        String error = stderr.get(10, TimeUnit.SECONDS);

        if (process.exitValue() != 0) {
            throw new IOException(
                    "Lighthouse process failed: " + error
            );
        }

        if (output == null || output.isBlank()) {
            throw new IOException(
                    "Lighthouse returned an empty response."
            );
        }

        LighthouseResult result = objectMapper.readValue(
                output,
                LighthouseResult.class
        );

        if (!"COMPLETED".equals(result.status())) {
            throw new IOException(
                    "Lighthouse audit did not complete."
            );
        }

        return result;
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
                null
        );
    }

    private CompletableFuture<String> readOutput(
            java.io.InputStream inputStream
    ) {

        return CompletableFuture.supplyAsync(() -> {

            try (inputStream) {

                return new String(
                        inputStream.readAllBytes(),
                        StandardCharsets.UTF_8
                );

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}