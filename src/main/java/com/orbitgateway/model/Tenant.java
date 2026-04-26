package com.orbitgateway.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record Tenant(
        @NotBlank String apiKey,
        @NotBlank String backendUrl,
        @Min(1) int rateLimit
) {
}
