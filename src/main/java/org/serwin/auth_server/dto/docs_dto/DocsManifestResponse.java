package org.serwin.auth_server.dto.docs_dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocsManifestResponse {
    public String service;
    public String version;
    public String visibility;
    public String audience;
    public String description;
    public String type;
    
    public DocCategory[] categories;
    // public CategoriesMap categoriesMap;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
class CategoriesMap {
    public String scope;
    public DocCategory[] categoriesMap;
}

// type DocManifest struct {
// Service string `json:"service"`
// Version string `json:"version,omitempty"`
// Categories []DocCategory `json:"categories"`
// CategoriesMap CategoriesMap `json:"categories_mapped"`
// }

// type DocItem struct {
// Title string `json:"title"`
// Slug string `json:"slug"`
// }

// type DocCategory struct {
// Title string `json:"title"`
// Items []DocItem `json:"items"`
// }

// type CategoriesMap struct{
// CategoriesMap []DocCategory `json:"categories_mapped"`
// Scope string `json:"scope"`

// }
