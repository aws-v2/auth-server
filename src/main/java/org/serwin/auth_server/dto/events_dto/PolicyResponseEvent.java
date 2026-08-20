package org.serwin.auth_server.dto.events_dto;

public record PolicyResponseEvent(
        String request_id,
        String status,
        String policy_id, // optional, only if created/found
        Object policy, // optional, return PolicyResponseDTO or list of them
        String message,
        String error) {
}
