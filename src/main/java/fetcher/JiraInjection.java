package fetcher;

import fetcher.model.JiraTicket;
import fetcher.model.JiraVersion;
import jakarta.json.bind.Jsonb;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gestisce il fetch da JIRA.
 */
public class JiraInjection {
    private static final String VERSIONS_API =
            "https://issues.apache.org/jira/rest/api/latest/project/%s";
    private static final String SEARCH_API   =
            "https://issues.apache.org/jira/rest/api/2/search";

    private final OkHttpClient client = new OkHttpClient();
    private final Jsonb jsonb;
    private final String projKey;
    private List<JiraVersion> releases;

    public JiraInjection(String projKey, Jsonb jsonb) {
        this.projKey = projKey;
        this.jsonb   = jsonb;
    }

    /** Carica le release per poter mappare date→release (opzionale) */
    public void injectReleases() throws IOException {
        String url = String.format(VERSIONS_API, projKey);
        Request req = new Request.Builder()
                .url(url)
                .header("Accept","application/json")
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String body = resp.body().string();
            VersionsResponse vr = jsonb.fromJson(body, VersionsResponse.class);
            releases = vr.versions;
            releases.sort(Comparator.comparing(JiraVersion::getReleaseDate));
            for (int i = 0; i < releases.size(); i++) {
                releases.get(i).setId(i+1);
            }
        }
    }

    /**
     * Fetcha tutti i ticket Bug con
     * status = Closed OR Resolved
     * resolution = Fixed
     */
    public List<JiraTicket> fetchFixedBugs() throws IOException {
        String jql = String.format(
                "project=\"%s\" AND issuetype=Bug " +
                        "AND (status=Closed OR status=Resolved) " +
                        "AND resolution=Fixed",
                projKey
        );

        List<JiraTicket> result = new ArrayList<>();
        int startAt = 0, total;

        do {
            HttpUrl url = HttpUrl.parse(SEARCH_API).newBuilder()
                    .addQueryParameter("jql",        jql)
                    .addQueryParameter("fields",     "key,versions,created,resolutiondate,status")
                    .addQueryParameter("startAt",    String.valueOf(startAt))
                    .addQueryParameter("maxResults", "1000")
                    .build();

            Request req = new Request.Builder()
                    .url(url)
                    .header("Accept","application/json")
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                String body = resp.body().string();
                SearchResponse sr = jsonb.fromJson(body, SearchResponse.class);
                total = sr.total;

                for (SearchIssue issue : sr.issues) {
                    LocalDate created  = issue.fields.created;
                    LocalDate resolved = issue.fields.resolutionDate;
                    // map opening/fixed version if serve
                    JiraVersion opening = getReleaseAfterOrEqualDate(created);
                    JiraVersion fixed   = getReleaseAfterOrEqualDate(resolved);

                    // build ticket
                    JiraTicket t = new JiraTicket(
                            issue.key,
                            created,
                            resolved,
                            opening,
                            fixed,
                            issue.fields.versions
                    );
                    result.add(t);
                }
                startAt += sr.issues.size();
            }
        } while (startAt < total);

        result.sort(Comparator.comparing(JiraTicket::getResolutionDate));
        return result;
    }

    private JiraVersion getReleaseAfterOrEqualDate(LocalDate d) {
        if (releases == null) return null;
        return releases.stream()
                .filter(r -> !r.getReleaseDate().isBefore(d))
                .findFirst()
                .orElse(null);
    }

    // getter (opzionale)
    public List<JiraVersion> getReleases() { return releases; }

    // --- binding classes ---
    public static class VersionsResponse {
        public List<JiraVersion> versions;
        public VersionsResponse() {}
    }
    public static class SearchResponse {
        public int total;
        public List<SearchIssue> issues;
        public SearchResponse() {}
    }
    public static class SearchIssue {
        public String key;
        public Fields fields;
        public SearchIssue() {}
    }
    public static class Fields {
        public List<JiraVersion> versions;
        public LocalDate created;
        @jakarta.json.bind.annotation.JsonbProperty("resolutiondate")
        public LocalDate resolutionDate;
        public Fields() {}
    }
}
