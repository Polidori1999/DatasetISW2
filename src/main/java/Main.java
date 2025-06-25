

import fetcher.BookkeeperFetcher;
import fetcher.model.JiraTicket;
import metrics.FeatureExtractor;
import metrics.BuggyMethodExtractor;
import utils.CsvGenerator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    /** Percorso locale del repo BookKeeper.
     *  Puoi passarlo anche come primo argomento da linea di comando. */
    private static final String DEFAULT_REPO_PATH = "/home/leonardo/uni/isw2/bookkeeper";

    public static void main(String[] args) throws Exception {
        System.out.println("Avvio processo Milestone-1: generazione dataset BOOKKEEPER");

        /* --------------------------------------------------------
         * 1) Ticket JIRA (cache su file JSON)
         * -------------------------------------------------------- */
        String jsonFileName = "bookkeeper_jira_tickets.json";
        File   jsonFile     = new File(jsonFileName);

        BookkeeperFetcher fetcher   = new BookkeeperFetcher();
        List<JiraTicket>  allTickets;

        if (jsonFile.exists()) {
            System.out.println("Carico ticket da JSON...");
            allTickets = fetcher.readTicketsFromFile(jsonFileName);
        } else {
            System.out.println("Scarico ticket da JIRA...");
            String jiraUser = System.getenv("JIRA_USER");
            String jiraPass = System.getenv("JIRA_PASS");
            allTickets      = fetcher.fetchAllJiraTickets(jiraUser, jiraPass);
            fetcher.writeTicketsToJsonFile(allTickets, jsonFileName);
        }
        System.out.println(" → Ticket totali: " + allTickets.size());

        /* --------------------------------------------------------
         * 2) Seleziona solo Bug chiusi/risolti come “Fixed”
         * -------------------------------------------------------- */
        System.out.println("Filtro ticket bug-fix (Bug + Fixed)...");
        List<JiraTicket> bugTickets = allTickets.stream()
                .filter(t -> "Bug".equalsIgnoreCase(t.getIssueType()))
                .filter(t -> {
                    String st = t.getStatus();
                    return "Closed".equalsIgnoreCase(st) || "Resolved".equalsIgnoreCase(st);
                })
                .filter(t -> "Fixed".equalsIgnoreCase(t.getResolution()))
                .collect(Collectors.toList());

        Set<String> bugKeys = bugTickets.stream()
                .map(JiraTicket::getKey)
                .collect(Collectors.toSet());

        System.out.println(" → Ticket bug-fix rilevanti: " + bugKeys.size());

        /* --------------------------------------------------------
         * 3) Clona (se serve) ed apre la repository Git
         * -------------------------------------------------------- */
        String repoPath   = args.length > 0 ? args[0] : DEFAULT_REPO_PATH;
        File   repoDir    = new File(repoPath);

        if (!repoDir.exists()) {
            System.out.println("Clono Apache BookKeeper in " + repoDir.getAbsolutePath() + " …");
            try (Git ignored = Git.cloneRepository()
                    .setURI("https://github.com/apache/bookkeeper.git")
                    .setDirectory(repoDir)
                    .call()) {
                // chiuso subito: lo riapriremo dopo
            }
        } else {
            System.out.println("Repo BookKeeper già presente: OK");
        }

        /* try-with-resources → chiudiamo sia Repository che Git */
        try (Repository repo = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment()
                .findGitDir()
                .build();
             Git git = new Git(repo)) {

            /* ----------------------------------------------------
             * 4) Trova i commit che citano i ticket bug-fix
             * ---------------------------------------------------- */
            System.out.println("Scansione commit bug-fix nel log…");
            Pattern keyPattern = Pattern.compile(
                    bugKeys.stream()
                            .map(Pattern::quote)
                            .collect(Collectors.joining("|")),
                    Pattern.CASE_INSENSITIVE);

            List<RevCommit> bugFixCommits = new ArrayList<>();
            for (RevCommit c : git.log().call()) {
                if (keyPattern.matcher(c.getFullMessage()).find()) {
                    bugFixCommits.add(c);
                }
            }
            System.out.println(" → Commit bug-fix trovati: " + bugFixCommits.size());

            /* ----------------------------------------------------
             * 5) Estrazione feature statiche (HEAD)
             * ---------------------------------------------------- */
            // 5) Estrazione feature statiche (HEAD)
            System.out.println("Estrazione feature statiche dai sorgenti (HEAD)…");
            FeatureExtractor fe = new FeatureExtractor();
            Map<String, Map<String, FeatureExtractor.MethodFeatures>> allFeatures = new HashMap<>();

            try (Stream<Path> paths = Files.walk(repoDir.toPath())) {
                paths.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> p.toString().contains("/src/main/java/"))
                        .forEach(p -> {
                            try {
                                allFeatures.put(
                                        p.toString(),                   //  <<—  chiave String
                                        fe.extractFromFile(p.toFile())
                                );
                            } catch (Exception e) {
                                System.err.println("Errore parsing " + p + ": " + e.getMessage());
                            }
                        });
            }
            System.out.println(" → Feature estratte da " + allFeatures.size() + " sorgenti");


            /* ----------------------------------------------------
             * 6) Identificazione metodi buggy
             *    (usa implementazione statica stile originale)
             * ---------------------------------------------------- */
            System.out.println("Identificazione metodi buggy…");
            BuggyMethodExtractor bme = new BuggyMethodExtractor(repo);
            Map<RevCommit, List<String>> changed = bme.extractChangedMethods(bugFixCommits);

            Set<String> buggyMethods = new HashSet<>();
            changed.values().forEach(buggyMethods::addAll);
            System.out.println(" → Metodi buggy identificati: " + buggyMethods.size());

            /* ----------------------------------------------------
             * 7) Genera CSV finale
             * ---------------------------------------------------- */
            System.out.println("Generazione CSV finale…");
            String csvFile = "bookkeeper_dataset.csv";
            new CsvGenerator(1).generateCsv(allFeatures, buggyMethods, csvFile);
            System.out.println("✓ CSV creato: " + csvFile);
        }
    }
}
