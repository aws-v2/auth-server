package org.serwin.auth_server.dto.docs_dto;


import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocManifest {
    private String service;
    private String apiVersion;
    private String scope;
    private List<DocCategory> internal = Collections.emptyList();

    // "public" is a Java keyword, so the field is renamed and mapped back
    // via @JsonProperty so the JSON output still uses "public" like the Go side.
    @JsonProperty("public")
    private List<DocCategory> publicCategories = Collections.emptyList();

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public List<DocCategory> getInternal() {
        return internal;
    }

    public void setInternal(List<DocCategory> internal) {
        this.internal = internal != null ? internal : Collections.emptyList();
    }

    @JsonProperty("public")
    public List<DocCategory> getPublicCategories() {
        return publicCategories;
    }

    public void setPublicCategories(List<DocCategory> publicCategories) {
        this.publicCategories = publicCategories != null ? publicCategories : Collections.emptyList();
    }
}
