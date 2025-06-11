package fetcher.model;

import java.time.LocalDate;
import java.util.List;

public class JiraTicket {
    private String key;
    private LocalDate creationDate;
    private LocalDate resolutionDate;
    private JiraVersion openingVersion;
    private JiraVersion fixedVersion;
    private List<JiraVersion> affectedVersions;

    public JiraTicket() { }

    public JiraTicket(String key,
                      LocalDate creationDate,
                      LocalDate resolutionDate,
                      JiraVersion openingVersion,
                      JiraVersion fixedVersion,
                      List<JiraVersion> affectedVersions) {
        this.key = key;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;
        this.openingVersion = openingVersion;
        this.fixedVersion = fixedVersion;
        this.affectedVersions = affectedVersions;
    }

    // getters & setters

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public LocalDate getCreationDate() { return creationDate; }
    public void setCreationDate(LocalDate creationDate) { this.creationDate = creationDate; }

    public LocalDate getResolutionDate() { return resolutionDate; }
    public void setResolutionDate(LocalDate resolutionDate) { this.resolutionDate = resolutionDate; }

    public JiraVersion getOpeningVersion() { return openingVersion; }
    public void setOpeningVersion(JiraVersion openingVersion) { this.openingVersion = openingVersion; }

    public JiraVersion getFixedVersion() { return fixedVersion; }
    public void setFixedVersion(JiraVersion fixedVersion) { this.fixedVersion = fixedVersion; }

    public List<JiraVersion> getAffectedVersions() { return affectedVersions; }
    public void setAffectedVersions(List<JiraVersion> affectedVersions) { this.affectedVersions = affectedVersions; }
}