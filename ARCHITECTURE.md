# Architecture

## Overview
Orbit Gateway follows a filter chain pattern common in production API gateways. Each request is authenticated, rate-limited, then proxied to a configured backend.

## Request Flow
Client -> ApiKeyFilter -> RateLimitFilter -> ProxyController -> Backend

1. Client sends a request to `/v1/proxy/**` with `X-API-Key`.
2. `ApiKeyFilter` validates the key and resolves the tenant.
3. `RateLimitFilter` applies per-tenant token bucket checks.
4. `ProxyController` forwards request method/path/query/body to the tenant backend URL.
5. Upstream response is returned to client and request metrics are logged.

## Components

### ApiKeyFilter
- Reads API key header (`X-API-Key` by default).
- Finds matching tenant in configured tenant map.
- Rejects invalid keys with `401 Unauthorized`.
- On success, stores tenant context for downstream components.

### RateLimitFilter
- Uses an in-memory token bucket per tenant.
- Each tenant gets a bucket sized to `rateLimit` with continuous refill over one minute.
- One request consumes one token. If tokens are unavailable, request is rejected with `429 Too Many Requests`.
- Isolation is per tenant, so one noisy tenant does not consume another tenant's budget.
- For v1, in-memory state is intentionally simple; limitation is that limits are not shared across multiple instances.

### ProxyController
- Handles requests on `/v1/proxy/**`.
- Strips the `/v1/proxy` prefix and appends remaining path/query to tenant `backendUrl`.
- Forwards headers (excluding hop-by-hop and gateway-specific headers), method, and body.
- Passes upstream status/body back to caller.

## Design Decisions
- Spring Boot filters over interceptors: filters execute earlier in request lifecycle and are better suited for cross-cutting gateway policies like auth and throttling.
- Token bucket over fixed window: smoother request handling with better burst control and fewer edge spikes at minute boundaries.
- `application.yml` over DB for v1: fastest path for a clean demo and interview walkthrough with minimal infrastructure.

Limitations and v2 improvements:
- In-memory limiter -> move to Redis for distributed rate limiting.
- Static config at startup -> move tenant config to DB/control plane.
- Basic upstream handling -> add timeout/retry/circuit breaker and richer observability.

