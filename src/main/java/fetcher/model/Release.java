package fetcher.model;

import jakarta.json.bind.annotation.JsonbProperty;
import java.time.Instant;

public class Release {

    @JsonbProperty("tag_name")
    private String tagName;

    @JsonbProperty("published_at")
    private Instant publishedAt;

    /** Costruttore no-args richiesto da Jackson */
    public Release() { }

    /**
     * Costruttore completo.
     * @param tagName nome del tag (es. "v4.15.0")
     * @param publishedAt data di pubblicazione
     */
    public Release(String tagName, Instant publishedAt) {
        this.tagName = tagName;
        this.publishedAt = publishedAt;
    }

    // --- getters e setters ---

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    @Override
    public String toString() {
        return "Release{" +
                "tagName='" + tagName + '\'' +
                ", publishedAt=" + publishedAt +
                '}';
    }
}