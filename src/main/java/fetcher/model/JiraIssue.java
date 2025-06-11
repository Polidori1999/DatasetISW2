package fetcher.model;

public class JiraIssue {
    public String key;
    public String summary;
    public String status;
    public String fixVersion;

    // 1) Costruttore no-args per poter fare "new JiraIssue()"
    public JiraIssue() {}

    // 2) Il costruttore a 4 parametri che già hai (non toccarlo se va bene)
    public JiraIssue(String key, String summary, String status, String fixVersion) {
        this.key        = key;
        this.summary    = summary;
        this.status     = status;
        this.fixVersion = fixVersion;
    }

    public void setFixVersion(String fixVersion) {
        this.fixVersion = fixVersion;
    }
    public String getFixVersion() {
        return fixVersion;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public void setSummary(String summary) {
        this.summary = summary;
    }
    public void setKey(String key) {
        this.key = key;
    }
    public String getKey() {
        return key;
    }
    public String getSummary() {
        return summary;
    }
    public String getStatus() {
        return status;
    }

}