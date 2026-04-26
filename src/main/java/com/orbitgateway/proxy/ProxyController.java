package com.orbitgateway.proxy;

import com.orbitgateway.filter.ApiKeyFilter;
import com.orbitgateway.model.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Enumeration;

@RestController
public class ProxyController {

    private static final String PROXY_PREFIX = "/v1/proxy";

    private final RestTemplate restTemplate;

    public ProxyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @RequestMapping(PROXY_PREFIX + "/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        if (request.getRequestURI().startsWith("/actuator") || request.getRequestURI().equals("/health")) {
            return ResponseEntity.notFound().build();
        }

        Tenant tenant = (Tenant) request.getAttribute(ApiKeyFilter.TENANT_ATTRIBUTE);
        if (tenant == null) {
            return ResponseEntity.status(401).body("Unauthorized".getBytes());
        }

        String targetUrl = buildTargetUrl(tenant.backendUrl(), request);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpHeaders headers = copyHeaders(request);

        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<byte[]> upstream = restTemplate.exchange(URI.create(targetUrl), method, entity, byte[].class);
            return ResponseEntity.status(upstream.getStatusCode())
                    .headers(stripHopByHop(upstream.getHeaders()))
                    .body(upstream.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(stripHopByHop(ex.getResponseHeaders()))
                    .body(ex.getResponseBodyAsByteArray());
        } catch (RestClientException ex) {
            return ResponseEntity.status(502).body("Bad Gateway".getBytes());
        }
    }

    private String buildTargetUrl(String backendUrl, HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String path = requestPath.startsWith(PROXY_PREFIX)
                ? requestPath.substring(PROXY_PREFIX.length())
                : requestPath;
        if (!StringUtils.hasText(path)) {
            path = "/";
        }
        String query = request.getQueryString();

        String baseUrl = backendUrl.endsWith("/") ? backendUrl.substring(0, backendUrl.length() - 1) : backendUrl;
        String fullPath = path.startsWith("/") ? path : "/" + path;

        if (StringUtils.hasText(query)) {
            return baseUrl + fullPath + "?" + query;
        }
        return baseUrl + fullPath;
    }

    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (headerName.equalsIgnoreCase("host")
                    || headerName.equalsIgnoreCase("content-length")
                    || headerName.equalsIgnoreCase("x-api-key")) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(headerName);
            while (values.hasMoreElements()) {
                headers.add(headerName, values.nextElement());
            }
        }
        return headers;
    }

    private HttpHeaders stripHopByHop(HttpHeaders headers) {
        HttpHeaders cleaned = new HttpHeaders();
        if (headers == null) {
            return cleaned;
        }
        cleaned.putAll(headers);
        cleaned.remove(HttpHeaders.TRANSFER_ENCODING);
        cleaned.remove(HttpHeaders.CONNECTION);
        cleaned.remove("Keep-Alive");
        return cleaned;
    }
}
