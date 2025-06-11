package fetcher;

import fetcher.model.JiraIssue;
import fetcher.model.Release;
import okhttp3.*;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fetch delle release GitHub (tutte, paginando) e dei ticket JIRA per fixVersion.
 */
public class BookkeeperFetcher {
    private static final String GITHUB_API =
            "https://api.github.com/repos/apache/bookkeeper/releases";
    private static final String JIRA_API   =
            "https://issues.apache.org/jira/rest/api/2/search";
    private static final String JIRA_JQL   =
            "project=BOOKKEEPER+AND+fixVersion=\"%s\"";

    private final OkHttpClient client = new OkHttpClient();
    private final Jsonb jsonb;

    public BookkeeperFetcher() {
        // Adapter per Instant <-> ISO string
        JsonbConfig cfg = new JsonbConfig()
                .withAdapters(new InstantAdapter());
        this.jsonb = JsonbBuilder.create(cfg);
    }

    /**
     * Scarica tutte le release di BookKeeper da GitHub:
     * per_page=100 e page=1,2,... finché la risposta non è vuota.
     */
    public List<Release> fetchAllReleases() throws IOException {
        List<Release> all = new ArrayList<>();
        int page = 1;

        while (true) {
            HttpUrl url = HttpUrl.parse(GITHUB_API).newBuilder()
                    .addQueryParameter("per_page", "100")
                    .addQueryParameter("page",    String.valueOf(page))
                    .build();

            Request req = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    throw new IOException("GitHub API error: " + resp.code());
                }

                String body = resp.body() != null ? resp.body().string() : "";
                Release[] arr = jsonb.fromJson(body, Release[].class);

                System.out.printf("→ Pagina %d: ho scaricato %d release%n", page, arr.length);
                if (arr.length == 0) {
                    System.out.println("→ Nessuna release in pagina, esco dal ciclo.");
                    break;
                }

                all.addAll(Arrays.asList(arr));
                System.out.printf("→ Totale release finora: %d%n", all.size());
            }
            page++;
        }

        System.out.printf("### FINE: totali release scaricate = %d%n", all.size());
        return all;
    }


    // helper per JSON-B
    public static class Tag {
        public String name;
        public CommitRef commit;
        public Tag() {}
    }
    public static class CommitRef {
        public String url;
        public CommitRef() {}
    }
    public static class CommitDetail {
        public Commit commit;
        public CommitDetail() {}
    }
    public static class Commit {
        public GitUser committer;
        public Commit() {}
    }
    public static class GitUser {
        public String date;
        public GitUser() {}
    }


    /**
     * Ordina per publishedAt e mantiene il 34% più recente.
     */
    public List<Release> filterNewest34Percent(List<Release> all) {
        List<Release> sorted = all.stream()
                .filter(r -> r.getPublishedAt() != null)
                .sorted(Comparator.comparing(Release::getPublishedAt))
                .collect(Collectors.toList());
        int cutoff = (int) Math.ceil(sorted.size() * 0.66);
        return sorted.subList(cutoff, sorted.size());
    }

    /**
     * Scarica i ticket JIRA per una data fixVersion.
     */
    public List<JiraIssue> fetchIssuesForVersion(String version,
                                                 String jiraUser,
                                                 String jiraPass) throws IOException {
        String jql = String.format(JIRA_JQL, version);
        HttpUrl url = HttpUrl.parse(JIRA_API).newBuilder()
                .addQueryParameter("jql",        jql)
                .addQueryParameter("maxResults", "1000")
                .build();

        Request.Builder rb = new Request.Builder()
                .url(url)
                .header("Accept", "application/json");
        if (jiraUser != null && jiraPass != null) {
            rb.header("Authorization", Credentials.basic(jiraUser, jiraPass));
        }

        try (Response resp = client.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("JIRA API error: " + resp.code());
            }
            String body = resp.body().string();
            SearchResponse sr = jsonb.fromJson(body, SearchResponse.class);

            List<JiraIssue> issues = new ArrayList<>();
            for (SearchIssue si : sr.issues) {
                String key     = si.key;
                String summary = si.fields.summary;
                String status  = si.fields.status.name;
                issues.add(new JiraIssue(key, summary, status, version));
            }
            return issues;
        }
    }

    // --- adapter per Instant <-> JSON string ---
    public static class InstantAdapter implements JsonbAdapter<Instant,String> {
        public InstantAdapter() {}
        @Override public String adaptToJson(Instant obj)   { return obj.toString(); }
        @Override public Instant adaptFromJson(String obj) { return Instant.parse(obj); }
    }

    // --- classi interne per deserializzare JIRA search ---
    public static class SearchResponse {
        public List<SearchIssue> issues;
        public SearchResponse() {}
    }
    public static class SearchIssue {
        public String key;
        public Fields fields;
        public SearchIssue() {}
    }
    public static class Fields {
        public String summary;
        public Status status;
        public Fields() {}
    }
    public static class Status {
        public String name;
        public Status() {}
    }
}