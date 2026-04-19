package org.serwin.auth_server.dto;

import java.util.Map;

public class DocResponse {

    private Map<String, Object> metadata;
    private String content;

    public DocResponse(Map<String, Object> metadata, String content) {
        this.metadata = metadata;
        this.content = content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getContent() {
        return content;
    }
}
