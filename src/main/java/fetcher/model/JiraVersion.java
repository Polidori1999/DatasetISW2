package fetcher.model;

import java.time.LocalDate;

public class JiraVersion {
    private int id;
    private String name;
    private LocalDate releaseDate;

    public JiraVersion() { }

    public JiraVersion(String name, LocalDate releaseDate) {
        this.name = name;
        this.releaseDate = releaseDate;
    }

    // getters & setters

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
}