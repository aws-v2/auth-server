package org.serwin.auth_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class LoginResponse {
    private String token;
    private String email;
    private boolean mfaEnabled;
    private boolean mfaRequired;
    private String message;

    public LoginResponse() {
    }

    public LoginResponse(String token, String email, boolean mfaEnabled, boolean mfaRequired, String message) {
        this.token = token;
        this.email = email;
        this.mfaEnabled = mfaEnabled;
        this.mfaRequired = mfaRequired;
        this.message = message;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public boolean isMfaRequired() {
        return mfaRequired;
    }

    public void setMfaRequired(boolean mfaRequired) {
        this.mfaRequired = mfaRequired;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
