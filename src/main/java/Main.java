// src/main/java/your/package/Main.java


import fetcher.BookkeeperFetcher;
import fetcher.model.JiraTicket;
import fetcher.model.JiraVersion;
import metrics.FeatureExtractor;
import metrics.BuggyMethodExtractor;
import utils.CsvGenerator;

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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main {

    private static final String OWNER            = "apache";
    private static final String REPO             = "bookkeeper";
    private static final String REMOTE_URI       = "https://github.com/" + OWNER + "/" + REPO + ".git";
    private static final String DEFAULT_REPO_DIR = "/home/leonardo/uni/isw2/" + REPO;

    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson        gson   = new Gson();

    public static void main(String[] args) {
        try {
            System.out.println("Avvio Milestone-1: dataset BOOKKEEPER");

            // 1) Jira tickets + project versions
            BookkeeperFetcher fetcher = new BookkeeperFetcher();
            List<JiraTicket> tickets     = loadOrFetchTickets(fetcher);
            List<JiraVersion> jiraVers   = fetcher.fetchProjectVersions();
            System.out.println(" → Versioni JIRA: " +
                    jiraVers.stream().map(JiraVersion::getName).collect(Collectors.joining(" "))
            );

            // 2) Clona (o apri) il repo per il log dei commit
            File repoDir = new File(args.length>0 ? args[0] : DEFAULT_REPO_DIR);
            Git git = repoDir.exists() ? open(repoDir) : cloneRepo(repoDir);

            // 3) Prendi i tag direttamente da GitHub
            List<String> gitTags = fetchGitHubTags(OWNER, REPO);
            System.out.println(" → Tag remoti trovati su GitHub: " + gitTags.size());

            // 4) Intersezione Git ∩ JIRA
            Set<String> jiraNorm = jiraVers.stream()
                    .map(JiraVersion::getName)
                    .map(v -> v.replaceFirst("^v",""))
                    .collect(Collectors.toSet());
            List<String> validTags = gitTags.stream()
                    .filter(t -> jiraNorm.contains(t.replaceFirst("^(?:v|release-)","")))
                    .sorted(Main::compareVersion)
                    .collect(Collectors.toList());
            if (validTags.isEmpty()) {
                System.out.println("⚠️ Nessun tag Git∩JIRA trovato: userò HEAD");
                validTags = List.of("HEAD");
            }
            System.out.println(" → Tag validi: " + validTags);

            // 5) Filtra i commit bug-fix
            Pattern issueRe = buildBugIssuePattern(tickets);
            List<RevCommit> bugFixes = new ArrayList<>();
            for (RevCommit c : git.log().call()) {
                if (issueRe.matcher(c.getFullMessage()).find()) {
                    bugFixes.add(c);
                }
            }
            System.out.println(" → Commit bug-fix: " + bugFixes.size());

            // 6) Per ogni tag scarica lo zip, estraine il sorgente, estrai le feature
            FeatureExtractor fx = new FeatureExtractor(repoDir.toPath());
            Map<String, Map<String,FeatureExtractor.MethodFeatures>> allFeat = new LinkedHashMap<>();

            for (String tag : validTags) {
                System.out.println(" → Elaboro release " + tag);
                if ("HEAD".equals(tag)) {
                    // estrai da working‐dir
                    allFeat.put(tag, walkAndExtract(repoDir, fx));
                } else {
                    Path tmp = downloadAndUnzip(OWNER, REPO, tag);
                    Path projectDir = findSingleSubdir(tmp);
                    allFeat.put(tag, walkAndExtract(projectDir.toFile(), fx));
                    deleteDirectoryRecursively(tmp);
                }
                System.out.println("   ✓ " + tag + " → " + allFeat.get(tag).size() + " metodi");
            }

            // 7) Identifica metodi buggy e genera CSV
            Set<String> buggy = new BuggyMethodExtractor(git.getRepository())
                    .extractChangedMethods(bugFixes)
                    .values().stream().flatMap(Collection::stream)
                    .collect(Collectors.toSet());
            System.out.println(" → Metodi buggy totali: " + buggy.size());

            new CsvGenerator().generateCsv(allFeat, buggy, "bookkeeper_dataset.csv");
            System.out.println("✓ CSV creato: bookkeeper_dataset.csv");

            git.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ————— Helper Jira —————

    private static List<JiraTicket> loadOrFetchTickets(BookkeeperFetcher f) throws Exception {
        File cache = new File("bookkeeper_jira_tickets.json");
        List<JiraTicket> ts = cache.exists()
                ? f.readTicketsFromFile(cache.getName())
                : f.fetchAllJiraTickets(System.getenv("JIRA_USER"), System.getenv("JIRA_PASS"));
        if (!cache.exists()) f.writeTicketsToJsonFile(ts, cache.getName());
        System.out.println(" → Ticket totali: " + ts.size());
        return ts;
    }

    // ————— Helper Git clone/log —————

    private static Git cloneRepo(File dir) throws GitAPIException {
        return Git.cloneRepository()
                .setURI(REMOTE_URI)
                .setDirectory(dir)
                .setCloneAllBranches(true)
                .call();
    }

    private static Git open(File dir) throws IOException {
        return new Git(new FileRepositoryBuilder()
                .setGitDir(new File(dir,".git"))
                .readEnvironment().findGitDir().build());
    }

    // ————— GitHub API per i tag —————

    private static List<String> fetchGitHubTags(String owner, String repo) throws IOException {
        HttpUrl url = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/tags")
                .newBuilder().addQueryParameter("per_page","100").build();
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful())
                throw new IOException("GitHub tags request failed: " + resp);
            JsonArray arr = gson.fromJson(resp.body().charStream(), JsonArray.class);
            List<String> tags = new ArrayList<>();
            for (JsonElement el : arr) {
                String name = el.getAsJsonObject().get("name").getAsString();
                tags.add(name);
            }
            return tags;
        }
    }

    // ————— Download & unzip dello zipball di GitHub —————

    private static Path downloadAndUnzip(String owner, String repo, String tag) throws IOException {
        HttpUrl url = HttpUrl.parse("https://api.github.com/repos/" + owner + "/" + repo + "/zipball/" + tag);
        Request req = new Request.Builder().url(url).build();
        Path tmpDir = Files.createTempDirectory(repo + "-" + tag + "-");
        try (Response resp = client.newCall(req).execute();
             InputStream  in   = resp.body().byteStream();
             ZipInputStream zip = new ZipInputStream(in)) {

            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path out = tmpDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
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

    // Se lo zip estrae una singola cartella root, la individua
    private static Path findSingleSubdir(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> subs = s.filter(Files::isDirectory).collect(Collectors.toList());
            return (subs.size()==1 ? subs.get(0) : dir);
        }
    }

    private static void deleteDirectoryRecursively(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    // ————— Utilità comune —————

    private static Pattern buildBugIssuePattern(List<JiraTicket> tks) {
        String orKeys = tks.stream()
                .filter(t->"Bug".equalsIgnoreCase(t.getIssueType()))
                .filter(t->{String s=t.getStatus(); return "Closed".equalsIgnoreCase(s)||"Resolved".equalsIgnoreCase(s);})
                .filter(t->"Fixed".equalsIgnoreCase(t.getResolution()))
                .map(JiraTicket::getKey).map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile(orKeys, Pattern.CASE_INSENSITIVE);
    }

    private static Map<String,FeatureExtractor.MethodFeatures> walkAndExtract(File dir, FeatureExtractor fx) throws IOException {
        Map<String,FeatureExtractor.MethodFeatures> m = new HashMap<>();
        try (Stream<Path> ps = Files.walk(dir.toPath())) {
            ps.filter(p->p.toString().endsWith(".java") && p.toString().contains("/src/main/java/"))
                    .forEach(p->{
                        try { m.putAll(fx.extractFromFile(p.toFile())); }
                        catch(Exception e){ System.err.println("Parse "+p+": "+e.getMessage()); }
                    });
        }
        return m;
    }

    private static int compareVersion(String a, String b) {
        String[] pa=a.replaceFirst("^(?:v|release-)","").split("\\.");
        String[] pb=b.replaceFirst("^(?:v|release-)","").split("\\.");
        int n=Math.max(pa.length,pb.length);
        for(int i=0;i<n;i++){
            int va=i<pa.length?Integer.parseInt(pa[i]):0;
            int vb=i<pb.length?Integer.parseInt(pb[i]):0;
            if(va!=vb) return Integer.compare(va,vb);
        }
        return 0;
    }
}
