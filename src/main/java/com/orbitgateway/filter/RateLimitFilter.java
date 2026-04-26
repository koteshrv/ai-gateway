package com.orbitgateway.filter;

import com.orbitgateway.model.Tenant;
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
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, WindowState> windows = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

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
        String tenantId = (String) request.getAttribute(ApiKeyFilter.TENANT_ID_ATTRIBUTE);
        Tenant tenant = (Tenant) request.getAttribute(ApiKeyFilter.TENANT_ATTRIBUTE);

        if (tenantId == null || tenant == null) {
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Tenant context missing");
            return;
        }

        boolean allowed = tryAcquire(tenantId, tenant.rateLimit(), Instant.now(clock).toEpochMilli());
        if (!allowed) {
            writeJsonError(response, 429, "Too Many Requests", "Rate limit exceeded");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean tryAcquire(String tenantId, int limitPerMinute, long nowEpochMillis) {
        WindowState state = windows.computeIfAbsent(tenantId, ignored -> new WindowState(limitPerMinute, nowEpochMillis));

        synchronized (state) {
            state.refill(limitPerMinute, nowEpochMillis);
            if (state.tokens < 1.0d) {
                return false;
            }

            state.tokens -= 1.0d;
            return true;
        }
    }

    private static final class WindowState {
        private double tokens;
        private long lastRefillEpochMillis;

        private WindowState(int capacity, long nowEpochMillis) {
            this.tokens = capacity;
            this.lastRefillEpochMillis = nowEpochMillis;
        }

        private void refill(int capacity, long nowEpochMillis) {
            if (nowEpochMillis <= lastRefillEpochMillis) {
                return;
            }

            double refillPerMs = capacity / 60_000.0d;
            long elapsedMs = nowEpochMillis - lastRefillEpochMillis;
            double refilled = elapsedMs * refillPerMs;

            tokens = Math.min(capacity, tokens + refilled);
            lastRefillEpochMillis = nowEpochMillis;
        }
    }

    private void writeJsonError(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"status":%d,"error":"%s","message":"%s"}
                """.formatted(status, error, message).trim());
    }
}
