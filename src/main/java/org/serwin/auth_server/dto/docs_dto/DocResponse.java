package org.serwin.auth_server.dto.docs_dto;


public class DocResponse {
    private Metadata metadata;
    private String content;

    public DocResponse(Metadata metadata, String content) {
        this.metadata = metadata;
        this.content = content;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
