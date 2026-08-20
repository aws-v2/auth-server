package org.serwin.auth_server.dto.events_dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class InstanceTokenResponse {
      public String token;
      public String error;
}