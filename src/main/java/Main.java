

import fetcher.BookkeeperFetcher;
import fetcher.model.JiraTicket;
import fetcher.model.JiraVersion;
import metrics.FeatureExtractor;
import metrics.BuggyMethodExtractor;
import utils.CsvGenerator;
import utils.CsvPreprocessor;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main {

    private static final String OWNER            = "apache";
    private static final String REPO             = "bookkeeper";
    private static final String REMOTE_URI       = "https://github.com/" + OWNER + "/" + REPO + ".git";
    private static final String DEFAULT_REPO_DIR = "/home/leonardo/uni/isw2/" + REPO;
    private static final String churnCacheFile = "churn_cache.json";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson        gson   = new Gson();

    public static void main(String[] args) {
        try {
            System.out.println("Avvio Milestone-1: dataset BOOKKEEPER");

            // --- 1) Jira tickets + project versions
            BookkeeperFetcher fetcher = new BookkeeperFetcher();
            List<JiraTicket> tickets   = loadOrFetchTickets(fetcher);
            List<JiraVersion> jiraVers = fetcher.fetchProjectVersions();
            System.out.println(" → Versioni JIRA disponibili: " +
                    String.join(" ",
                            jiraVers.stream().map(JiraVersion::getName).toList()
                    )
            );

            // --- 2) Clona (o apri) il repo per il log dei commit
            File repoDir = new File(args.length>0 ? args[0] : DEFAULT_REPO_DIR);
            Git git = repoDir.exists() ? open(repoDir) : cloneRepo(repoDir);

            // --- 3) Prendi le tag direttamente da GitHub
            List<String> gitTags = fetchGitHubTags(OWNER, REPO);
            System.out.println(" → Tag remoti trovati su GitHub: " + gitTags.size());

            // --- 4) Intersezione Git ∩ JIRA e semantic sort
            Set<String> jiraNorm = new HashSet<>();
            for (JiraVersion v : jiraVers) {
                jiraNorm.add(v.getName().replaceFirst("^v",""));
            }
            List<String> validTags = new ArrayList<>();
            for (String t : gitTags) {
                if (jiraNorm.contains(t.replaceFirst("^(?:v|release-)",""))) {
                    validTags.add(t);
                }
            }
            Collections.sort(validTags, Main::compareVersion);
            if (validTags.isEmpty()) {
                System.out.println("⚠️ Nessun tag Git∩JIRA trovato: userò HEAD");
                validTags = List.of("HEAD");
            }
            System.out.println(" → Tag validi (tutte): " + validTags);

            // --- 5) Filtra i commit bug-fix
            Pattern issueRe = buildBugIssuePattern(tickets);
            List<RevCommit> bugFixes = new ArrayList<>();
            for (RevCommit c : git.log().call()) {
                if (issueRe.matcher(c.getFullMessage()).find()) {
                    bugFixes.add(c);
                }
            }
            System.out.println(" → Commit bug-fix trovati: " + bugFixes.size());

            // --- 6) Per ogni tag estrai feature (zip+unzip → JavaParser+PMD)
            FeatureExtractor fx = new FeatureExtractor(repoDir.toPath());
            Map<String, Map<String,FeatureExtractor.MethodFeatures>> allFeat = new LinkedHashMap<>();
            for (String tag : validTags) {
                System.out.println(" → Elaboro release " + tag);
                Map<String,FeatureExtractor.MethodFeatures> feats;
                if ("HEAD".equals(tag)) {
                    feats = walkAndExtract(repoDir, fx);
                } else {
                    Path tmp     = downloadAndUnzip(OWNER, REPO, tag);
                    Path projDir = findSingleSubdir(tmp);
                    feats = walkAndExtract(projDir.toFile(), fx);
                    deleteDirectoryRecursively(tmp);
                }
                allFeat.put(tag, feats);
                System.out.println("   ✓ " + tag + " → " + feats.size() + " metodi");
            }

            // --- 7) Identifica metodi buggy + statistiche
            BuggyMethodExtractor extractor = new BuggyMethodExtractor(git.getRepository());
            Map<RevCommit,List<String>> buggyMap = extractor.extractChangedMethods(bugFixes);
            System.out.println(" → Commits con diff estraibili: " + buggyMap.size());
            int totalChanged = buggyMap.values().stream().mapToInt(List::size).sum();
            System.out.println(" → Totale modifiche di metodo (con duplicati): " + totalChanged);
            Set<String> buggyMethods = buggyMap.values().stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
            System.out.println(" → Metodi unici identificati come buggy: " + buggyMethods.size());

            // 8) Calcola methodHistories e churn (con cache su file)
            System.out.println("inizio calcolo churn");
            Map<String,Integer> histories = extractor.calculateMethodHistories(bugFixes);

// churn: o lo leggo dal file, o lo calcolo e salvo
            Map<String,Integer> churn;
            File cache = new File(churnCacheFile);
            if (cache.exists() && cache.length() > 0) {
                // legge la mappa da JSON
                System.out.println("file churn gia presente, leggo da file");
                try (Reader r = new FileReader(cache)) {
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String,Integer>>(){}.getType();
                    churn = gson.fromJson(r, type);
                    System.out.println("→ Caricato churn da cache: " + churn.size() + " metodi");
                }
            } else {
                // lo calcolo e salvo
                System.out.println("file churn non presente");
                churn = extractor.calculateMethodChurn(bugFixes);
                try (Writer w = new FileWriter(cache)) {
                    gson.toJson(churn, w);
                    System.out.println("→ Cache churn salvato su " + churnCacheFile);
                }
            }

            // inietta i valori dentro le feature
            for (var entry : allFeat.entrySet()) {
                for (var me : entry.getValue().entrySet()) {
                    String sig = me.getKey().split("#",2)[1];
                    FeatureExtractor.MethodFeatures f = me.getValue();
                    f.methodHistories = histories.getOrDefault(sig, 0);
                    f.churn           = churn    .getOrDefault(sig, 0);
                }
            }

            // --- 9) Applica regola del 33% sulle release (dopo etichettatura)
            int keepCount = Math.max(1, (int)Math.floor(validTags.size() * 0.33));
            List<String> keptTags = validTags.subList(0, keepCount);
            System.out.println(" → Release mantenute (33% più vecchie): " + keptTags);
            Map<String, Map<String,FeatureExtractor.MethodFeatures>> filteredFeat = new LinkedHashMap<>();
            for (String t : keptTags) filteredFeat.put(t, allFeat.get(t));

            // --- 10) Genera CSV grezzo
            String rawCsv = "bookkeeper_dataset_raw.csv";
            new CsvGenerator().generateCsv(filteredFeat, buggyMethods, rawCsv);
            System.out.println("✓ CSV grezzo creato: " + rawCsv);

            // --- 11) Preprocessing: rimuovo duplicate
            Path rawPath   = Paths.get(rawCsv);
            Path cleanPath = Paths.get("bookkeeper_dataset_clean.csv");
            CsvPreprocessor.removeDuplicateRows(rawPath, cleanPath);
            System.out.println("→ Righe duplicate rimosse → " + cleanPath);

            git.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // ————— Cammina l’albero dei sorgenti e filtra test/generated/etc. —————
    private static Map<String, FeatureExtractor.MethodFeatures> walkAndExtract(File dir, FeatureExtractor fx) throws IOException {
        Map<String, FeatureExtractor.MethodFeatures> m = new HashMap<>();

        String[] excludes = {
                "/test/", "/generated/", "/target/",
                "/common/", "/utils/", "/examples/",
                "/api/", "/internal/", "/dto/",
                "/model/", "/config/", "/resources/"
        };

        Files.walk(dir.toPath())
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.toString().contains("/src/main/java/"))
                .filter(p -> {
                    String path = p.toString().replace('\\','/');
                    // escludi cartelle e test
                    if (path.endsWith("Test.java")) return false;
                    for (String ex : excludes) {
                        if (path.contains(ex)) return false;
                    }
                    return true;
                })
                .forEach(p -> {
                    try {
                        m.putAll(fx.extractFromFile(p.toFile()));
                    } catch (Exception e) {
                        System.err.println("Parse " + p + ": " + e.getMessage());
                    }
                });
        return m;
    }

    // ————— Helpers (clone, open, GitHub API, unzip, ecc.) —————
    private static List<JiraTicket> loadOrFetchTickets(BookkeeperFetcher f) throws Exception {
        File cache = new File("bookkeeper_jira_tickets.json");
        if (cache.exists()) {
            return f.readTicketsFromFile(cache.getName());
        } else {
            List<JiraTicket> ts = f.fetchAllJiraTickets(System.getenv("JIRA_USER"), System.getenv("JIRA_PASS"));
            f.writeTicketsToJsonFile(ts, cache.getName());
            return ts;
        }
    }

    private static Git cloneRepo(File dir) throws GitAPIException {
        return Git.cloneRepository()
                .setURI(REMOTE_URI)
                .setDirectory(dir)
                .setCloneAllBranches(true)
                .call();
    }

    private static Git open(File dir) throws IOException {
        return new Git(new FileRepositoryBuilder()
                .setGitDir(new File(dir, ".git"))
                .readEnvironment().findGitDir().build());
    }

    private static List<String> fetchGitHubTags(String owner, String repo) throws IOException {
        HttpUrl url = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/tags")
                .newBuilder().addQueryParameter("per_page","100").build();
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("Richiesta GitHub tag fallita: " + resp);
            JsonArray arr = gson.fromJson(resp.body().charStream(), JsonArray.class);
            List<String> tags = new ArrayList<>();
            for (JsonElement el : arr) tags.add(el.getAsJsonObject().get("name").getAsString());
            return tags;
        }
    }

    private static Path downloadAndUnzip(String owner, String repo, String tag) throws IOException {
        HttpUrl url = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/zipball/" + tag);
        Request req = new Request.Builder().url(url).build();
        Path tmpDir = Files.createTempDirectory(repo + "-" + tag + "-");
        try (Response resp = client.newCall(req).execute();
             InputStream in = resp.body().byteStream();
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path out = tmpDir.resolve(entry.getName());
                if (entry.isDirectory()) Files.createDirectories(out);
                else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) {
                        zip.transferTo(os);
                    }
                }
                zip.closeEntry();
            }
        }
        return tmpDir;
    }

    private static Path findSingleSubdir(Path dir) throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            List<Path> subs = new ArrayList<>();
            for (Path p : ds) if (Files.isDirectory(p)) subs.add(p);
            return subs.size()==1 ? subs.get(0) : dir;
        }
    }

    private static void deleteDirectoryRecursively(Path dir) throws IOException {
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private static Pattern buildBugIssuePattern(List<JiraTicket> tks) {
        StringBuilder buf = new StringBuilder();
        for (JiraTicket t : tks) {
            if (!"Bug".equalsIgnoreCase(t.getIssueType())) continue;
            String s = t.getStatus();
            if (!("Closed".equalsIgnoreCase(s) || "Resolved".equalsIgnoreCase(s))) continue;
            if (!"Fixed".equalsIgnoreCase(t.getResolution())) continue;
            if (buf.length()>0) buf.append("|");
            buf.append(Pattern.quote(t.getKey()));
        }
        return Pattern.compile(buf.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static int compareVersion(String a, String b) {
        String[] pa = a.replaceFirst("^(?:v|release-)","").split("\\.");
        String[] pb = b.replaceFirst("^(?:v|release-)","").split("\\.");
        int n = Math.max(pa.length,pb.length);
        for (int i=0;i<n;i++){
            int va = i<pa.length?Integer.parseInt(pa[i]):0;
            int vb = i<pb.length?Integer.parseInt(pb[i]):0;
            if (va!=vb) return Integer.compare(va,vb);
        }
        return 0;
    }
}
