package com.webdoctor.backend.scanner.dto;

public record BrokenLinkFinding(

        String url,

        String linkType,

        int statusCode,

        String status,

        String message,

        String finalUrl,

        int redirectCount

) {}