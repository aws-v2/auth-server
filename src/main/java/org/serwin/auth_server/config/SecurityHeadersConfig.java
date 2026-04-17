package org.serwin.auth_server.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.IOException;

/**
 * Security headers configuration for production deployment
 * Adds essential security headers to all responses
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class SecurityHeadersConfig implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Prevent clickjacking attacks
        httpResponse.setHeader("X-Frame-Options", "DENY");

        // Prevent MIME type sniffing
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");

        // Enable XSS protection
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

        // Strict Transport Security (HSTS) - only enable if using HTTPS
        // Tells browsers to only access this site via HTTPS for the next year
        httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");

        // Content Security Policy
        httpResponse.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data:; " +
                "font-src 'self'; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'");

        // Referrer Policy
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions Policy (formerly Feature Policy)
        httpResponse.setHeader("Permissions-Policy",
                "geolocation=(), microphone=(), camera=(), payment=()");

        // Remove server header for security
        httpResponse.setHeader("Server", "");

        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("Security headers filter initialized");
    }

    @Override
    public void destroy() {
        log.info("Security headers filter destroyed");
    }
}
