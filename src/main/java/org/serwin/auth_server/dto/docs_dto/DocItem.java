package org.serwin.auth_server.dto.docs_dto;


public class DocItem {
    private String title;
    private String slug;

    public DocItem() {
    }

    public DocItem(String title, String slug) {
        this.title = title;
        this.slug = slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }
}
