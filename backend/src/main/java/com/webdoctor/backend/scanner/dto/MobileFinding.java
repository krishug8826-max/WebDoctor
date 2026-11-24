package com.webdoctor.backend.scanner.dto;

public record MobileFinding(

        String name,

        String status,

        String description,

        String recommendation

) {}