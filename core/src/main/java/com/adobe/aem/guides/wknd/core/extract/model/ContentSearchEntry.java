package com.adobe.aem.guides.wknd.core.extract.model;

import java.util.ArrayList;

public record ContentSearchEntry(
        String path,
        String title,
        String description,
        String[] content,
        String url
) {
    public static class Builder {
        private String path;
        private String title;
        private String description;
        private final ArrayList<String> content = new ArrayList<>();
        private String url;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder content(String content) {
            this.content.add(content);
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public ContentSearchEntry build() {
            return new ContentSearchEntry(path, title, description, content.toArray(String[]::new), url);
        }
    }
}
