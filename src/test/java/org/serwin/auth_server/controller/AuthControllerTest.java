package org.serwin.auth_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.serwin.auth_server.config.SecurityConfig;
import org.serwin.auth_server.dto.LoginRequest;
import org.serwin.auth_server.dto.LoginResponse;
import org.serwin.auth_server.dto.RegisterRequest;
import org.serwin.auth_server.dto.UserDto;
import org.serwin.auth_server.service.AuthService;
import org.serwin.auth_server.service.NatsService;
import org.serwin.auth_server.service.TokenBlacklistService;
import org.serwin.auth_server.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private NatsService natsService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private org.serwin.auth_server.repository.ClientRepository clientRepository;

    @MockBean
    private org.serwin.auth_server.repository.UserRepository userRepository;

    @MockBean
    private org.serwin.auth_server.service.RateLimitingService rateLimitingService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isAllowed(any(), any())).thenReturn(true);
        when(tokenBlacklistService.hashToken(anyString())).thenReturn("hashed-token");
    }

    @Test
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");
        request.setConfirmPassword("password");
        request.setFirstName("Test");
        request.setLastName("User");

        LoginResponse response = LoginResponse.builder()
                .token("token")
                .email("test@example.com")
                .build();

        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"));

        verify(natsService, times(1)).publish(eq("user"), eq("registered"), any());
    }

    @Test
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");

        LoginResponse response = LoginResponse.builder()
                .token("token")
                .build();

        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void enableMfa_success() throws Exception {
        when(authService.enableMfa("test@example.com")).thenReturn(Map.of("qrCode", "data..."));

        mockMvc.perform(post("/api/v1/auth/mfa/enable")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrCode").value("data..."));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void logout_success() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .with(csrf())
                .header("Authorization", "Bearer my-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(tokenBlacklistService, times(1)).blacklistToken(eq("my-token"), eq("test@example.com"), anyString());
        verify(natsService, times(1)).publish(eq("token"), eq("blacklisted"), any());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void getCurrentUser_success() throws Exception {
        UserDto user = new UserDto();
        user.setEmail("test@example.com");

        when(authService.getCurrentUser("test@example.com")).thenReturn(user);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }
}