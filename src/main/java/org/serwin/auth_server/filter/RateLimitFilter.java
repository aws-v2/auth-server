package org.serwin.auth_server.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.serwin.auth_server.service.RateLimitingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String ipAddress = getClientIpAddress(request);

        // Determine bucket type based on endpoint
        RateLimitingService.BucketType bucketType = determineBucketType(path);

        if (bucketType != null) {
            boolean allowed = rateLimitingService.isAllowed(ipAddress, bucketType);

            if (!allowed) {
                auditLog.warn("RATE_LIMIT_EXCEEDED - ip={}, path={}, bucketType={}",
                        ipAddress, path, bucketType);
                log.warn("Rate limit exceeded for IP: {} on path: {}", ipAddress, path);

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                Map<String, Object> errorResponse = Map.of(
                        "error", "Rate limit exceeded",
                        "message", "Too many requests. Please try again later.",
                        "status", HttpStatus.TOO_MANY_REQUESTS.value());

                response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitingService.BucketType determineBucketType(String path) {
        // Authentication endpoints - strict rate limiting
        if (path.startsWith("/api/v1/auth/login") ||
                path.startsWith("/api/v1/auth/register") ||
                path.startsWith("/api/v1/auth/forgot-password") ||
                path.startsWith("/api/v1/auth/reset-password")) {
            return RateLimitingService.BucketType.AUTH;
        }

        // MFA endpoints - very strict rate limiting
        if (path.startsWith("/api/v1/auth/mfa/")) {
            return RateLimitingService.BucketType.STRICT;
        }

        // API key operations - strict rate limiting
        if (path.startsWith("/api/v1/auth/api-keys")) {
            return RateLimitingService.BucketType.STRICT;
        }

        // Other authenticated endpoints
        if (path.startsWith("/api/v1/auth/")) {
            return RateLimitingService.BucketType.API;
        }

        // No rate limiting for other paths
        return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
