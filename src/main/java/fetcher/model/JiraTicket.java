package fetcher.model;



import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Modello “ricco” di ticket JIRA.
 * Tutti i campi sono opzionali: se l'API non li restituisce restano null o [].
 */
public class JiraTicket {

    /* ---------- identificazione ---------- */
    private String key;
    private String summary;
    private String description;
    private String status;          // es. "Closed"
    private String issueType;       // es. "Bug"

    /* ---------- date ---------- */
    private LocalDate creationDate;
    private LocalDate resolutionDate;
    private LocalDate updatedDate;
    private LocalDate dueDate;

    /* ---------- versioni ---------- */
    private JiraVersion openingVersion;
    private JiraVersion fixedVersion;
    private List<JiraVersion> affectedVersions = new ArrayList<>();

    /* ---------- metadati ---------- */
    private String priority;
    private String reporter;
    private String assignee;
    private String resolution;      // es. "Fixed"
    private String environment;

    private List<String> labels     = new ArrayList<>();
    private List<String> components = new ArrayList<>();

    /* ---------- time tracking ---------- */
    private Long timeOriginalEstimate;
    private Long timeSpent;
    private Long timeRemainingEstimate;

    /* ---------- interazioni ---------- */
    private List<Comment>    comments    = new ArrayList<>();
    private List<Attachment> attachments = new ArrayList<>();
    private Integer votes;
    private Integer watcherCount;
    private List<Worklog> worklogs      = new ArrayList<>();

    /* costruttore vuoto per JSON-B */
    public JiraTicket() {}

    /* ---------- getter & setter ---------- */
    public String getKey() { return key; }
    public void   setKey(String key) { this.key = key; }

    public String getSummary() { return summary; }
    public void   setSummary(String summary) { this.summary = summary; }

    public String getDescription() { return description; }
    public void   setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void   setStatus(String status) { this.status = status; }

    public String getIssueType() { return issueType; }
    public void   setIssueType(String issueType) { this.issueType = issueType; }

    public LocalDate getCreationDate() { return creationDate; }
    public void      setCreationDate(LocalDate creationDate) { this.creationDate = creationDate; }

    public LocalDate getResolutionDate() { return resolutionDate; }
    public void      setResolutionDate(LocalDate resolutionDate) { this.resolutionDate = resolutionDate; }

    public LocalDate getUpdatedDate() { return updatedDate; }
    public void      setUpdatedDate(LocalDate updatedDate) { this.updatedDate = updatedDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void      setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public JiraVersion getOpeningVersion() { return openingVersion; }
    public void        setOpeningVersion(JiraVersion openingVersion) { this.openingVersion = openingVersion; }

    public JiraVersion getFixedVersion() { return fixedVersion; }
    public void        setFixedVersion(JiraVersion fixedVersion) { this.fixedVersion = fixedVersion; }

    public List<JiraVersion> getAffectedVersions() { return affectedVersions; }
    public void              setAffectedVersions(List<JiraVersion> affectedVersions) {
        this.affectedVersions = affectedVersions;
    }

    public String getPriority() { return priority; }
    public void   setPriority(String priority) { this.priority = priority; }

    public String getReporter() { return reporter; }
    public void   setReporter(String reporter) { this.reporter = reporter; }

    public String getAssignee() { return assignee; }
    public void   setAssignee(String assignee) { this.assignee = assignee; }

    public String getResolution() { return resolution; }
    public void   setResolution(String resolution) { this.resolution = resolution; }

    public String getEnvironment() { return environment; }
    public void   setEnvironment(String environment) { this.environment = environment; }

    public List<String> getLabels() { return labels; }
    public void         setLabels(List<String> labels) { this.labels = labels; }

    public List<String> getComponents() { return components; }
    public void         setComponents(List<String> components) { this.components = components; }

    public Long getTimeOriginalEstimate() { return timeOriginalEstimate; }
    public void setTimeOriginalEstimate(Long timeOriginalEstimate) {
        this.timeOriginalEstimate = timeOriginalEstimate;
    }

    public Long getTimeSpent() { return timeSpent; }
    public void setTimeSpent(Long timeSpent) { this.timeSpent = timeSpent; }

    public Long getTimeRemainingEstimate() { return timeRemainingEstimate; }
    public void setTimeRemainingEstimate(Long timeRemainingEstimate) {
        this.timeRemainingEstimate = timeRemainingEstimate;
    }

    public List<Comment> getComments() { return comments; }
    public void          setComments(List<Comment> comments) { this.comments = comments; }

    public List<Attachment> getAttachments() { return attachments; }
    public void             setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public Integer getVotes() { return votes; }
    public void    setVotes(Integer votes) { this.votes = votes; }

    public Integer getWatcherCount() { return watcherCount; }
    public void    setWatcherCount(Integer watcherCount) { this.watcherCount = watcherCount; }

    public List<Worklog> getWorklogs() { return worklogs; }
    public void           setWorklogs(List<Worklog> worklogs) { this.worklogs = worklogs; }

    /* ---------- inner DTO classes per interazioni ---------- */
    public static class Comment {
        private String body;
        private String author;
        private LocalDate created;

        public String getBody() { return body; }
        public void   setBody(String body) { this.body = body; }

        public String getAuthor() { return author; }
        public void   setAuthor(String author) { this.author = author; }

        public LocalDate getCreated() { return created; }
        public void       setCreated(LocalDate created) { this.created = created; }
    }

    public static class Attachment {
        private String id;
        private String filename;
        private String mimeType;
        private String content;   // URL
        private LocalDate created;

        public String getId() { return id; }
        public void   setId(String id) { this.id = id; }

        public String getFilename() { return filename; }
        public void   setFilename(String filename) { this.filename = filename; }

        public String getMimeType() { return mimeType; }
        public void   setMimeType(String mimeType) { this.mimeType = mimeType; }

        public String getContent() { return content; }
        public void   setContent(String content) { this.content = content; }

        public LocalDate getCreated() { return created; }
        public void       setCreated(LocalDate created) { this.created = created; }
    }

    public static class Worklog {
        private String author;
        private String comment;
        private long   timeSpentSeconds;
        private LocalDate started;

        public String getAuthor() { return author; }
        public void   setAuthor(String author) { this.author = author; }

        public String getComment() { return comment; }
        public void   setComment(String comment) { this.comment = comment; }

        public long getTimeSpentSeconds() { return timeSpentSeconds; }
        public void setTimeSpentSeconds(long timeSpentSeconds) {
            this.timeSpentSeconds = timeSpentSeconds;
        }

        public LocalDate getStarted() { return started; }
        public void      setStarted(LocalDate started) { this.started = started; }
    }
}