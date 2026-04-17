package org.serwin.auth_server.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.serwin.auth_server.entities.Client;
import org.serwin.auth_server.repository.ClientRepository;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ClientAuthenticationFilter extends OncePerRequestFilter {

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public ClientAuthenticationFilter(ClientRepository clientRepository,
            @org.springframework.context.annotation.Lazy PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String clientId = request.getHeader("X-Client-Id");
        final String clientSecret = request.getHeader("X-Client-Secret");

        // If headers are missing, proceed to the next filter (allowing other auth
        // methods)
        if (clientId == null || clientSecret == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            log.debug("Attempting client authentication for Client ID: {}", clientId);

            Optional<Client> clientOptional = clientRepository.findByClientId(clientId);

            if (clientOptional.isPresent()) {
                Client client = clientOptional.get();

                if (client.isEnabled() && passwordEncoder.matches(clientSecret, client.getClientSecretHash())) {

                    List<SimpleGrantedAuthority> authorities = Arrays.stream(client.getRoles().split(","))
                            .map(role -> new SimpleGrantedAuthority(role.trim()))
                            .collect(Collectors.toList());

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            client,
                            null,
                            authorities);

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Client authentication successful for: {}", clientId);
                } else {
                    log.warn("Client authentication failed: Invalid secret or disabled client for ID: {}", clientId);
                }
            } else {
                log.warn("Client authentication failed: Client ID not found: {}", clientId);
            }
        } catch (Exception e) {
            log.error("Error during client authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
