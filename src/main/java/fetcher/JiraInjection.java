package fetcher;

import fetcher.model.JiraTicket;
import fetcher.model.JiraVersion;
import okhttp3.*;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.adapter.JsonbAdapter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JiraInjection usando Jakarta JSON-B (Yasson) per il binding,
 * con un adapter che gestisce sia date plain (yyyy-MM-dd)
 * sia timestamp completi (ISO_OFFSET_DATE_TIME).
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
    private List<JiraVersion> affectedReleases;
    private List<JiraTicket> ticketsWithIssues;
    private List<JiraTicket> fixedTickets;

    public JiraInjection(String projKey) {
        this.projKey = projKey;
        JsonbConfig cfg = new JsonbConfig()
                .withAdapters(new LocalDateAdapter());
        this.jsonb = JsonbBuilder.create(cfg);
    }

    /** 1) Fetch e ordina tutte le release (versions) del progetto */
    public void injectReleases() throws IOException {
        String url = String.format(VERSIONS_API, projKey);
        Request req = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("JIRA versions API error: " + resp.code());
            }
            String body = resp.body().string();
            VersionsResponse vr = jsonb.fromJson(body, VersionsResponse.class);
            releases = new ArrayList<>(vr.versions);

            // ordina e assegna ID progressivo
            releases.sort(Comparator.comparing(JiraVersion::getReleaseDate));
            for (int i = 0; i < releases.size(); i++) {
                releases.get(i).setId(i + 1);
            }
        }
    }

    /** 2) Restituisce la prima release ≥ data */
    private JiraVersion getReleaseAfterOrEqualDate(LocalDate d) {
        for (JiraVersion r : releases) {
            if (!r.getReleaseDate().isBefore(d)) {
                return r;
            }
        }
        return null;
    }

    /** 3) Mappa l’array “versions” di un ticket in oggetti JiraVersion */
    private void checkValidAffectedVersions(List<JiraVersion> list) {
        affectedReleases = new ArrayList<>();
        for (JiraVersion v : list) {
            for (JiraVersion r : releases) {
                if (r.getName().equals(v.getName())) {
                    affectedReleases.add(r);
                    break;
                }
            }
        }
        affectedReleases.sort(Comparator.comparing(JiraVersion::getReleaseDate));
    }

    /** 4) Paga i ticket a blocchi di 1000, filtra i Bug Fixed, crea JiraTicket */
    public void pullIssues() throws IOException {
        ticketsWithIssues = new ArrayList<>();
        int startAt = 0, total;

        do {
            HttpUrl url = HttpUrl.parse(SEARCH_API).newBuilder()
                    .addQueryParameter("jql",
                            String.format("project=\"%s\" AND issuetype=\"Bug\" "
                                    + "AND (status=Closed OR status=Resolved) "
                                    + "AND resolution=Fixed", projKey))
                    .addQueryParameter("fields", "key,versions,created,resolutiondate")
                    .addQueryParameter("startAt", String.valueOf(startAt))
                    .addQueryParameter("maxResults", "1000")
                    .build();

            Request req = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    throw new IOException("JIRA search API error: " + resp.code());
                }
                String body = resp.body().string();
                SearchResponse sr = jsonb.fromJson(body, SearchResponse.class);

                total = sr.total;
                for (SearchIssue issue : sr.issues) {
                    LocalDate created  = issue.fields.created;
                    LocalDate resolved = issue.fields.resolutionDate;
                    JiraVersion opening = getReleaseAfterOrEqualDate(created);
                    JiraVersion fixed   = getReleaseAfterOrEqualDate(resolved);

                    checkValidAffectedVersions(issue.fields.versions);

                    // filtro di coerenza
                    if (opening != null && fixed != null
                            && !affectedReleases.isEmpty()
                            && (!affectedReleases.get(0).getReleaseDate().isBefore(opening.getReleaseDate())
                            || opening.getReleaseDate().isAfter(fixed.getReleaseDate()))
                            && opening.getId() != releases.get(0).getId()) {
                        continue;
                    }

                    ticketsWithIssues.add(new JiraTicket(
                            issue.key,
                            created,
                            resolved,
                            opening,
                            fixed,
                            new ArrayList<>(affectedReleases)
                    ));
                }

                startAt += sr.issues.size();
            }
        } while (startAt < total);

        ticketsWithIssues.sort(Comparator.comparing(JiraTicket::getResolutionDate));
    }

    /** 5) (Facoltativo) ulteriore filtro/metriche… */
    public void filterFixedNormally() {
        fixedTickets = new ArrayList<>();
        for (JiraTicket t : ticketsWithIssues) {
            if (t.getAffectedVersions().contains(t.getFixedVersion())) {
                fixedTickets.add(t);
            }
        }
        fixedTickets.sort(Comparator.comparing(JiraTicket::getResolutionDate));
    }

    // --- getters ---
    public List<JiraVersion> getReleases()         { return releases; }
    public List<JiraTicket> getTicketsWithIssues() { return ticketsWithIssues; }
    public List<JiraTicket> getFixedTickets()      { return fixedTickets; }


    // --- adapter per LocalDate: gestisce sia 'yyyy-MM-dd' sia 'yyyy-MM-ddTHH:mm:ss.SSSZ' ---
    public static class LocalDateAdapter implements JsonbAdapter<LocalDate,String> {
        private static final DateTimeFormatter TIMESTAMP_FMT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

        public LocalDateAdapter() {}

        @Override
        public String adaptToJson(LocalDate obj) {
            return obj.toString();
        }

        @Override
        public LocalDate adaptFromJson(String obj) {
            if (obj.indexOf('T') > 0) {
                return OffsetDateTime.parse(obj, TIMESTAMP_FMT).toLocalDate();
            } else {
                return LocalDate.parse(obj);
            }
        }
    }

    // --- classi di supporto per JSON-B binding ---
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

        @JsonbProperty("resolutiondate")
        public LocalDate resolutionDate;

        public LocalDate created;
        public Fields() {}
    }


    /**
     * Fetcha tutti i ticket Bug Fixed per le release specificate.
     */
    public List<JiraTicket> fetchFixedForVersions(List<JiraVersion> vers) throws IOException {
        // Prepara la JQL: fixVersion in ("v1","v2",...)
        String names = vers.stream()
                .map(v -> "\"" + v.getName() + "\"")
                .collect(Collectors.joining(","));
        String jql = String.format(
                "project=\"%s\" AND issuetype=Bug " +
                        "AND (status=Closed OR status=Resolved) " +
                        "AND resolution=Fixed " +
                        "AND fixVersion in (%s)",
                projKey, names
        );

        List<JiraTicket> result = new ArrayList<>();
        int startAt = 0, total;

        do {
            HttpUrl url = HttpUrl.parse(SEARCH_API).newBuilder()
                    .addQueryParameter("jql",       jql)
                    .addQueryParameter("fields",    "key,versions,created,resolutiondate")
                    .addQueryParameter("startAt",   String.valueOf(startAt))
                    .addQueryParameter("maxResults","1000")
                    .build();

            Request req = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    throw new IOException("JIRA API error: " + resp.code());
                }
                String body = resp.body() != null ? resp.body().string() : "";
                SearchResponse sr = jsonb.fromJson(body, SearchResponse.class);

                total = sr.total;
                for (SearchIssue issue : sr.issues) {
                    LocalDate created  = issue.fields.created;
                    LocalDate resolved = issue.fields.resolutionDate;
                    JiraVersion opening = getReleaseAfterOrEqualDate(created);
                    JiraVersion fixed   = getReleaseAfterOrEqualDate(resolved);

                    // ricava la lista filteredAffectedVersions
                    checkValidAffectedVersions(issue.fields.versions);
                    List<JiraVersion> aff = new ArrayList<>(affectedReleases);

                    // costruisci il ticket
                    JiraTicket t = new JiraTicket(
                            issue.key, created, resolved,
                            opening, fixed, aff
                    );

                    // solo se valido (definisci isValidIssue internamente)
                    if (isValidIssue(t, aff.isEmpty() ? null : aff.get(0))) {
                        result.add(t);
                    }
                }
                startAt += sr.issues.size();
            }
        } while (startAt < total);

        result.sort(Comparator.comparing(JiraTicket::getResolutionDate));
        return result;
    }

    /**
     * Controlla che il ticket abbia opening ≤ firstAffected ≤ fixed
     * e non sia della prima release (ID = 1).
     */
    private boolean isValidIssue(JiraTicket t, JiraVersion firstAffected) {
        if (t.getOpeningVersion() == null || t.getFixedVersion() == null) return false;
        if (firstAffected == null) return false;
        if (firstAffected.getReleaseDate().isBefore(t.getOpeningVersion().getReleaseDate())) return false;
        if (t.getOpeningVersion().getReleaseDate().isAfter(t.getFixedVersion().getReleaseDate())) return false;
        // scarta ticket aperti sulla prima release (spesso bootstrap)
        return t.getOpeningVersion().getId() != releases.get(0).getId();
    }

}