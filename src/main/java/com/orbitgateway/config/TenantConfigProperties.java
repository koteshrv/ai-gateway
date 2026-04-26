package com.orbitgateway.config;

import com.orbitgateway.model.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Validated
@ConfigurationProperties(prefix = "")
public class TenantConfigProperties {

    @NotEmpty
    private Map<String, @Valid Tenant> tenants = new LinkedHashMap<>();

    private Gateway gateway = new Gateway();

    public Map<String, Tenant> getTenants() {
        return tenants;
    }

    public void setTenants(Map<String, Tenant> tenants) {
        this.tenants = tenants;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    public Optional<TenantMatch> findByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        return tenants.entrySet()
                .stream()
                .filter(entry -> apiKey.equals(entry.getValue().apiKey()))
                .map(entry -> new TenantMatch(entry.getKey(), entry.getValue()))
                .findFirst();
    }

    public record TenantMatch(String tenantId, Tenant tenant) {
    }

    public static class Gateway {
        private String apiKeyHeader = "X-API-Key";

        public String getApiKeyHeader() {
            return apiKeyHeader;
        }

        public void setApiKeyHeader(String apiKeyHeader) {
            this.apiKeyHeader = apiKeyHeader;
        }
    }
}
