package com.selflearning.authservice.role.request;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UserRoleAssignRequest(
        @NotNull
        Set<Long> roleIds
) {
}
