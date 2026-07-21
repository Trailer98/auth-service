package com.selflearning.authservice.application.response;

import com.selflearning.authservice.application.domain.AuthApplication;
import java.time.LocalDateTime;

public record ApplicationResponse(
        Long id,
        String applicationCode,
        String applicationName,
        String description,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ApplicationResponse from(AuthApplication application) {
        return new ApplicationResponse(
                application.getId(),
                application.getApplicationCode(),
                application.getApplicationName(),
                application.getDescription(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
