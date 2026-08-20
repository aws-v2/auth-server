package org.serwin.auth_server.dto.docs_dto;


import java.util.List;

public class DocCategory {
    private String title;
    private List<DocItem> items;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<DocItem> getItems() {
        return items;
    }

    public void setItems(List<DocItem> items) {
        this.items = items;
    }
}
