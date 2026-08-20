
package org.serwin.auth_server.dto.docs_dto;



import java.util.Collections;
import java.util.List;

public class Metadata {
    private String title;
    private String description;
    private String icon;
    private String lastUpdated;
    private List<String> tags = Collections.emptyList();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags != null ? tags : Collections.emptyList();
    }
}
