package org.serwin.auth_server.dto.events_dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class InstanceTokenRequest{
        public String instanceId;
        public String userId;
        public  String payload;
   
}
