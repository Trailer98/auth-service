package com.selflearning.authservice.auth.response;

import java.util.List;

public record AuthContextResponse(
        Long userId,
        String username,
        String applicationCode,
        List<String> roles,
        List<String> permissions
) {
}
