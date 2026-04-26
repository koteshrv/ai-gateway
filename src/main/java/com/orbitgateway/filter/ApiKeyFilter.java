package com.orbitgateway.filter;

import com.orbitgateway.config.TenantConfigProperties;
import com.orbitgateway.config.TenantConfigProperties.TenantMatch;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String TENANT_ID_ATTRIBUTE = "orbit.tenantId";
    public static final String TENANT_ATTRIBUTE = "orbit.tenant";

    private final TenantConfigProperties tenantConfigProperties;

    public ApiKeyFilter(TenantConfigProperties tenantConfigProperties) {
        this.tenantConfigProperties = tenantConfigProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String headerName = tenantConfigProperties.getGateway().getApiKeyHeader();
        String apiKey = request.getHeader(headerName);
        TenantMatch tenantMatch = tenantConfigProperties.findByApiKey(apiKey).orElse(null);

        if (tenantMatch == null) {
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Invalid API key");
            return;
        }

        request.setAttribute(TENANT_ID_ATTRIBUTE, tenantMatch.tenantId());
        request.setAttribute(TENANT_ATTRIBUTE, tenantMatch.tenant());
        filterChain.doFilter(request, response);
    }

    private void writeJsonError(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"status":%d,"error":"%s","message":"%s"}
                """.formatted(status, error, message).trim());
    }
}
