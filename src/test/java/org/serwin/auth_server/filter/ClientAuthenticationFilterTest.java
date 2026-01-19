package org.serwin.auth_server.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.serwin.auth_server.entities.Client;
import org.serwin.auth_server.repository.ClientRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientAuthenticationFilterTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ClientAuthenticationFilter clientAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_ValidCredentials_SetsAuthentication() throws ServletException, IOException {
        String clientId = "test-client";
        String clientSecret = "test-secret";
        String encodedSecret = "encoded-secret";

        when(request.getHeader("X-Client-Id")).thenReturn(clientId);
        when(request.getHeader("X-Client-Secret")).thenReturn(clientSecret);

        Client client = new Client();
        client.setClientId(clientId);
        client.setClientSecretHash(encodedSecret);
        client.setEnabled(true);
        client.setRoles("ROLE_CLIENT");

        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.of(client));
        when(passwordEncoder.matches(clientSecret, encodedSecret)).thenReturn(true);

        clientAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_InvalidSecret_DoesNotSetAuthentication() throws ServletException, IOException {
        String clientId = "test-client";
        String clientSecret = "wrong-secret";
        String encodedSecret = "encoded-secret";

        when(request.getHeader("X-Client-Id")).thenReturn(clientId);
        when(request.getHeader("X-Client-Secret")).thenReturn(clientSecret);

        Client client = new Client();
        client.setClientId(clientId);
        client.setClientSecretHash(encodedSecret);
        client.setEnabled(true);

        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.of(client));
        when(passwordEncoder.matches(clientSecret, encodedSecret)).thenReturn(false);

        clientAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_ClientNotFound_DoesNotSetAuthentication() throws ServletException, IOException {
        String clientId = "unknown-client";
        String clientSecret = "secret";

        when(request.getHeader("X-Client-Id")).thenReturn(clientId);
        when(request.getHeader("X-Client-Secret")).thenReturn(clientSecret);
        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.empty());

        clientAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_DisabledClient_DoesNotSetAuthentication() throws ServletException, IOException {
        String clientId = "disabled-client";
        String clientSecret = "test-secret";

        when(request.getHeader("X-Client-Id")).thenReturn(clientId);
        when(request.getHeader("X-Client-Secret")).thenReturn(clientSecret);

        Client client = new Client();
        client.setClientId(clientId);
        client.setEnabled(false);

        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.of(client));

        clientAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_MissingHeaders_ContinuesChain() throws ServletException, IOException {
        when(request.getHeader("X-Client-Id")).thenReturn(null);

        clientAuthenticationFilter.doFilterInternal(request, response, filterChain);

        verify(clientRepository, never()).findByClientId(any());
        verify(filterChain).doFilter(request, response);
    }
}
