package com.webdoctor.backend.scanner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScanRequest(

        @NotBlank(message = "Website URL is required")
        @Size(max = 2048, message = "Website URL must not exceed 2048 characters")
        String url

) {
}
