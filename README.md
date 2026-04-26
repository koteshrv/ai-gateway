# Orbit Gateway
Lightweight multi-tenant API gateway in Spring Boot, built to demonstrate core gateway patterns in a simple, interview-ready project.

## What It Does
Orbit Gateway sits between clients and upstream APIs to centralize authentication, rate limiting, routing, and logging.

Without a gateway, every backend must implement these concerns independently. With this gateway, policy enforcement is handled once at the edge and applied consistently per tenant.

## Architecture
Client -> [ApiKeyFilter] -> [RateLimitFilter] -> [ProxyController] -> Backend

- `ApiKeyFilter`: validates the incoming API key and resolves tenant context from configured tenant definitions.
- `RateLimitFilter`: applies per-tenant token bucket checks before the request reaches proxy logic.
- `ProxyController`: forwards the HTTP request to the tenant's configured backend URL and returns the upstream response.

## Features (V1)
- API key authentication
- Per-tenant rate limiting (token bucket algorithm)
- Request routing to configured backends
- Request/response logging with latency
- Multi-tenancy via `application.yml` config

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.9+

### Run Locally
```bash
git clone https://github.com/koteshrv/orbit-gateway
cd orbit-gateway
mvn spring-boot:run
```

### Run with Docker
```bash
docker build -t orbit-gateway .
docker run -p 8080:8080 orbit-gateway
```

## Live Demo
Base URL: https://orbit-gateway.onrender.com

1. Successful request:
```bash
curl -X GET https://orbit-gateway.onrender.com/v1/proxy/get \
  -H "X-API-Key: key-tenant-a-123"
```

2. Invalid API key -> 401:
```bash
curl -X GET https://orbit-gateway.onrender.com/v1/proxy/get \
  -H "X-API-Key: invalid-key"
```

3. Rate limit exceeded -> 429:
```bash
# Run this 11 times for tenant-a (limit is 10/min)
for i in {1..11}; do
  curl -X GET https://orbit-gateway.onrender.com/v1/proxy/get \
    -H "X-API-Key: key-tenant-a-123"
done
```

## Configuration
Tenant configuration lives in `src/main/resources/application.yml` and is loaded at startup.

```yaml
tenants:
  tenant-a:
    apiKey: ${TENANT_A_API_KEY:key-tenant-a-123}
    backendUrl: "https://httpbin.org"
    rateLimit: 10
```

Field meanings:
- `apiKey`: key expected in `X-API-Key` header (can be overridden via env variable).
- `backendUrl`: upstream base URL for that tenant.
- `rateLimit`: token bucket capacity/refill rate expressed as requests per minute.

## Rate Limiting
This project uses an in-memory token bucket per tenant:
- Each tenant gets N tokens per minute.
- Each request consumes one token.
- Tokens refill continuously up to bucket capacity.
- Empty bucket means request is rejected with `429 Too Many Requests`.
- Token bucket is used because it is simple, predictable, and easy to reason about in interviews.

## Roadmap
See v2 plan in `ARCHITECTURE.md`.

## Tech Stack
- Java 17
- Spring Boot 3.x
- Maven
- Docker
- Deployed on Render
