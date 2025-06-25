// src/main/java/Main.java

import fetcher.BookkeeperFetcher;
import fetcher.model.JiraTicket;
import metrics.FeatureExtractor;
import metrics.BuggyMethodExtractor;
import utils.CsvGenerator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String DEFAULT_REPO_PATH = "/home/leonardo/uni/isw2/bookkeeper";

    public static void main(String[] args) {
        try {
            System.out.println("Avvio Milestone-1: generazione dataset BOOKKEEPER");

            // 1) Jira tickets
            String jsonFile = "bookkeeper_jira_tickets.json";
            BookkeeperFetcher fetcher = new BookkeeperFetcher();
            List<JiraTicket> tickets = new File(jsonFile).exists()
                    ? fetcher.readTicketsFromFile(jsonFile)
                    : fetcher.fetchAllJiraTickets(System.getenv("JIRA_USER"), System.getenv("JIRA_PASS"));
            if (!new File(jsonFile).exists()) fetcher.writeTicketsToJsonFile(tickets, jsonFile);
            System.out.println(" → Ticket totali: " + tickets.size());

            // 2) Filtra bug-fix tickets
            Set<String> bugKeys = tickets.stream()
                    .filter(t -> "Bug".equalsIgnoreCase(t.getIssueType()))
                    .filter(t -> {
                        String s = t.getStatus();
                        return "Closed".equalsIgnoreCase(s) || "Resolved".equalsIgnoreCase(s);
                    })
                    .filter(t -> "Fixed".equalsIgnoreCase(t.getResolution()))
                    .map(JiraTicket::getKey)
                    .collect(Collectors.toSet());
            System.out.println(" → Ticket bug-fix rilevanti: " + bugKeys.size());

            // 3) Clona/apre il repo
            String repoPath = args.length > 0 ? args[0] : DEFAULT_REPO_PATH;
            File repoDir = new File(repoPath);
            if (!repoDir.exists()) {
                System.out.println("Clono repository in " + repoDir.getAbsolutePath());
                Git.cloneRepository()
                        .setURI("https://github.com/apache/bookkeeper.git")
                        .setDirectory(repoDir)
                        .call();
            }

            try (Repository repo = new FileRepositoryBuilder()
                    .setGitDir(new File(repoDir, ".git"))
                    .readEnvironment().findGitDir().build();
                 Git git = new Git(repo)) {

                // fetch dei tag remoti
                System.out.println("→ Faccio fetch dei tag remoti…");
                git.fetch()
                        .setRemote("origin")
                        .setRefSpecs(new RefSpec("+refs/tags/*:refs/tags/*"))
                        .call();

                // salva il branch di partenza
                String initialBranch = repo.getBranch();

                // 4) Scansione commit bug-fix
                Pattern issuePattern = Pattern.compile(
                        bugKeys.stream().map(Pattern::quote).collect(Collectors.joining("|")),
                        Pattern.CASE_INSENSITIVE
                );
                List<RevCommit> bugFixCommits = new ArrayList<>();
                for (RevCommit c : git.log().call()) {
                    if (issuePattern.matcher(c.getFullMessage()).find()) {
                        bugFixCommits.add(c);
                    }
                }
                System.out.println(" → Commit bug-fix trovati: " + bugFixCommits.size());

                // 5) Estrazione feature statiche per ogni tag
                System.out.println("Estrazione feature statiche per ogni release...");
                FeatureExtractor fe = new FeatureExtractor(repoDir.toPath());
                Map<String, Map<String, FeatureExtractor.MethodFeatures>> allFeatures = new LinkedHashMap<>();

                List<Ref> tags = git.tagList().call();
                System.out.println(" → Tag trovati: " + tags.size());
                if (tags.isEmpty()) {
                    // fallback su versione "1"
                    System.out.println("   Nessun tag: estraggo versione 1");
                    Map<String, FeatureExtractor.MethodFeatures> headFeatures =
                            extractFeaturesOnHEAD(repoDir, fe);
                    allFeatures.put("1", headFeatures);
                } else {
                    for (Ref tagRef : tags) {
                        String tag = tagRef.getName().replace("refs/tags/", "");
                        System.out.println(" → Checkout " + tag);
                        git.checkout().setName(tag).call();

                        Map<String, FeatureExtractor.MethodFeatures> featMap =
                                extractFeaturesOnHEAD(repoDir, fe);
                        System.out.println("   → Estratte " + featMap.size() +
                                " feature per " + tag);
                        allFeatures.put(tag, featMap);
                    }
                }

                // torna al branch iniziale
                git.checkout().setName(initialBranch).call();
                System.out.println(" → Ritornato su branch " + initialBranch);

                // 6) Identifica metodi buggy
                System.out.println("Identificazione metodi buggy…");
                BuggyMethodExtractor bme = new BuggyMethodExtractor(repo);
                Set<String> buggyMethods = new HashSet<>();
                bme.extractChangedMethods(bugFixCommits)
                        .values().forEach(buggyMethods::addAll);
                System.out.println(" → Metodi buggy identificati: " + buggyMethods.size());

                // 7) Genera CSV
                String csvFile = "bookkeeper_dataset.csv";
                System.out.println("Generazione CSV in " + new File(csvFile).getAbsolutePath() + " …");
                try {
                    new CsvGenerator().generateCsv(allFeatures, buggyMethods, csvFile);
                    System.out.println("✓ CSV creato: " + new File(csvFile).getAbsolutePath());
                } catch (Exception e) {
                    System.err.println("Errore nella generazione del CSV:");
                    e.printStackTrace();
                }
            }

        } catch (IOException ioe) {
            System.err.println("I/O error: " + ioe.getMessage());
            ioe.printStackTrace();
        } catch (Exception e) {
            System.err.println("Errore imprevisto:");
            e.printStackTrace();
        }
    }

    /** helper per riusare la logica di walking + extractFromFile */
    private static Map<String, FeatureExtractor.MethodFeatures> extractFeaturesOnHEAD(
            File repoDir, FeatureExtractor fe) {

        Map<String, FeatureExtractor.MethodFeatures> result = new HashMap<>();
        try (Stream<Path> paths = Files.walk(repoDir.toPath())) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/"))
                    .forEach(p -> {
                        try {
                            result.putAll(fe.extractFromFile(p.toFile()));
                        } catch (Exception ex) {
                            System.err.println("Errore parsing " + p + ": " + ex.getMessage());
                        }
                    });
        } catch (IOException ex) {
            System.err.println("I/O walking error: " + ex.getMessage());
        }
        return result;
    }
}
