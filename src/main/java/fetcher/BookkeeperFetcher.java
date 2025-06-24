package fetcher;


import fetcher.model.JiraTicket;
import fetcher.model.JiraVersion;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.annotation.JsonbProperty;
import okhttp3.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BookkeeperFetcher {

    private static final String JIRA_SEARCH_API =
            "https://issues.apache.org/jira/rest/api/2/search";
    private static final String JIRA_JQL_ALL =
            "project = BOOKKEEPER ORDER BY created ASC";

    private final OkHttpClient client = new OkHttpClient();
    private final Jsonb        jsonb;

    public BookkeeperFetcher() {
        this.jsonb = JsonbBuilder.create(
                new JsonbConfig().withFormatting(true)
        );
    }

    public List<JiraTicket> fetchAllJiraTickets(String user, String pwd) throws IOException {
        List<JiraTicket> tickets = new ArrayList<>();
        int startAt = 0, pageSize = 500, total;

        do {
            HttpUrl url = HttpUrl.parse(JIRA_SEARCH_API).newBuilder()
                    .addQueryParameter("jql",        JIRA_JQL_ALL)
                    .addQueryParameter("fields",     "*all")
                    .addQueryParameter("startAt",    String.valueOf(startAt))
                    .addQueryParameter("maxResults", String.valueOf(pageSize))
                    .build();

            Request.Builder rb = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/json");
            if (user != null && pwd != null)
                rb.header("Authorization", Credentials.basic(user, pwd));

            try (Response resp = client.newCall(rb.build()).execute()) {
                if (!resp.isSuccessful())
                    throw new IOException("JIRA search API error: HTTP " + resp.code());

                JiraSearchResponse sr = jsonb.fromJson(resp.body().string(), JiraSearchResponse.class);
                total = sr.total;
                for (JiraSearchIssue si : sr.issues) {
                    tickets.add(mapIssue(si));
                }
            }
            startAt += pageSize;
        } while (startAt < total);

        return tickets;
    }

    public void writeTicketsToJsonFile(List<JiraTicket> tickets, String filePath) throws IOException {
        Files.writeString(Paths.get(filePath), jsonb.toJson(tickets));
    }

    private static JiraTicket mapIssue(JiraSearchIssue si) {
        JiraTicket t = new JiraTicket();
        Fields f   = si.fields;

        t.setKey(si.key);
        t.setSummary(f.summary);
        t.setDescription(f.description);
        t.setStatus(f.status      != null ? f.status.name      : null);
        t.setIssueType(f.issuetype != null ? f.issuetype.name : null);
        t.setPriority(f.priority    != null ? f.priority.name    : null);
        t.setReporter(f.reporter    != null ? f.reporter.displayName : null);
        t.setAssignee(f.assignee    != null ? f.assignee.displayName : null);
        t.setResolution(f.resolution!= null ? f.resolution.name : null);

        if (f.created        != null) t.setCreationDate  (LocalDate.parse(f.created       .substring(0,10)));
        if (f.updated        != null) t.setUpdatedDate   (LocalDate.parse(f.updated       .substring(0,10)));
        if (f.resolutiondate != null) t.setResolutionDate(LocalDate.parse(f.resolutiondate.substring(0,10)));
        if (f.duedate        != null) t.setDueDate       (LocalDate.parse(f.duedate       .substring(0,10)));

        if (!f.fixVersions.isEmpty()) t.setFixedVersion  (f.fixVersions.get(0));
        if (!f.versions   .isEmpty()) t.setOpeningVersion(f.versions   .get(0));
        t.setAffectedVersions(f.versions);
        t.setLabels      (f.labels);
        t.setComponents  (f.components.stream().map(c -> c.name).collect(Collectors.toList()));

        t.setEnvironment(f.environment);

        t.setTimeOriginalEstimate(f.timeOriginalEstimate);
        t.setTimeSpent(f.timeSpent);
        t.setTimeRemainingEstimate(f.remainingEstimate);

        // commenti
        if (f.comment != null) {
            t.setComments(f.comment.comments.stream()
                    .map(c -> {
                        JiraTicket.Comment jc = new JiraTicket.Comment();
                        jc.setBody(c.body);
                        jc.setAuthor(c.author.displayName);
                        jc.setCreated(LocalDate.parse(c.created.substring(0,10)));
                        return jc;
                    })
                    .collect(Collectors.toList())
            );
        }

        // allegati
        if (!f.attachment.isEmpty()) {
            t.setAttachments(f.attachment.stream().map(dto -> {
                JiraTicket.Attachment a = new JiraTicket.Attachment();
                a.setId(dto.id);
                a.setFilename(dto.filename);
                a.setMimeType(dto.mimeType);
                a.setContent(dto.content);
                if (dto.created != null)
                    a.setCreated(LocalDate.parse(dto.created.substring(0,10)));
                return a;
            }).collect(Collectors.toList()));
        }


        // voti & watchers
        if (f.votes != null)    t.setVotes(f.votes.votes);
        if (f.watchers != null) t.setWatcherCount(f.watchers.watchCount);

        // worklog
        if (f.worklog != null) {
            t.setWorklogs(f.worklog.worklogs.stream()
                    .map(w -> {
                        JiraTicket.Worklog jw = new JiraTicket.Worklog();
                        jw.setAuthor(w.author.displayName);
                        jw.setComment(w.comment);
                        jw.setTimeSpentSeconds(w.timeSpentSeconds);
                        jw.setStarted(LocalDate.parse(w.started.substring(0,10)));
                        return jw;
                    })
                    .collect(Collectors.toList())
            );
        }

        return t;
    }

    public static class JiraSearchResponse { public int total; public List<JiraSearchIssue> issues; }
    public static class JiraSearchIssue    { public String key; public Fields fields; }

    public static class Fields {
        public String summary;
        public String description;
        public Status status;
        public IssueType issuetype;
        public Priority priority;
        public User reporter;
        public User assignee;
        public Resolution resolution;

        public String created;
        public String updated;
        @JsonbProperty("resolutiondate") public String resolutiondate;
        @JsonbProperty("duedate")        public String duedate;

        public List<JiraVersion> versions    = new ArrayList<>();
        public List<JiraVersion> fixVersions = new ArrayList<>();
        public List<String>      labels      = new ArrayList<>();
        public List<Component>   components  = new ArrayList<>();

        public String environment;

        @JsonbProperty("timeoriginalestimate") public Long timeOriginalEstimate;
        @JsonbProperty("timespent")             public Long timeSpent;
        @JsonbProperty("timeestimate")          public Long remainingEstimate;

        public CommentContainer    comment;
        public List<Attachment> attachment = new ArrayList<>();
        public Votes               votes;
        public Watchers            watchers;
        public WorklogContainer    worklog;
    }

    public static class Status     { public String name; }
    public static class IssueType  { public String name; }
    public static class Priority   { public String name; }
    public static class User       { public String displayName; }
    public static class Resolution { public String name; }
    public static class Component  { public String name; }

    public static class CommentContainer    { public List<Comment>   comments    = new ArrayList<>(); }
    public static class Comment             { public String body; public User author; public String created; }

    public static class AttachmentContainer { public List<Attachment> attachments = new ArrayList<>(); }
    public static class Attachment          {
        public String id;
        public String filename;
        public String mimeType;
        public String content;
        public String created;
    }

    public static class Votes    { public int votes; public boolean hasVoted; }
    public static class Watchers { @JsonbProperty("watchCount") public int watchCount; }

    public static class WorklogContainer { public List<Worklog> worklogs = new ArrayList<>(); }
    public static class Worklog          {
        public User author;
        public String comment;
        @JsonbProperty("timeSpentSeconds") public long timeSpentSeconds;
        public String started;
    }
}