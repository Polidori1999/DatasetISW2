import fetcher.JiraInjection;
import fetcher.model.JiraTicket;
import fetcher.model.JiraVersion;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import utils.InstantAdapter;

import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // 1) Configuro JSON-B con l’adapter per Instant e abilito pretty-print
        JsonbConfig config = new JsonbConfig()

                .withFormatting(true)
                .withAdapters(new InstantAdapter());
        Jsonb jsonb = JsonbBuilder.create(config);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try {
            // --- PARTE UNICA: JIRA BookKeeper releases (34% più vecchie) ---
            JiraInjection ji = new JiraInjection("BOOKKEEPER");
            ji.injectReleases();
            List<JiraVersion> allJv = ji.getReleases();

            System.out.println("=== Tutte le JIRA Releases disponibili ===");
            allJv.sort(Comparator.comparing(JiraVersion::getReleaseDate));
            for (JiraVersion v : allJv) {
                System.out.println(v.getName() + "  (releaseDate " + v.getReleaseDate().format(fmt) + ")");
            }
            System.out.printf("Caricate %d JIRA release totali%n%n", allJv.size());

            // Calcolo quante tenerne: 34% più vecchie
            int cutoffJv = (int) Math.ceil(allJv.size() * 0.34);
            List<JiraVersion> oldest34 = allJv.subList(0, cutoffJv);

            System.out.println("=== JIRA Releases tenute (34% più vecchie) ===");
            for (JiraVersion v : oldest34) {
                System.out.println(v.getName() + "  (releaseDate " + v.getReleaseDate().format(fmt) + ")");
            }
            System.out.printf("Tenute %d JIRA release (34%% più vecchie)%n%n", oldest34.size());

            // Scrivo le JIRA-release più vecchie in releases.json
            try (FileWriter fw = new FileWriter("releases.json")) {
                jsonb.toJson(oldest34, fw);
            }
            System.out.println("File releases.json scritto con le JIRA release più vecchie\n");

            // --- Fetch dei ticket per quelle release ---
            List<JiraTicket> jiraBugs = ji.fetchFixedForVersions(oldest34);
            System.out.printf("Estratti %d ticket Fixed/Closed/Resolved%n%n", jiraBugs.size());

            // Serializzo in jira_bugs.json
            try (FileWriter fw = new FileWriter("jira_bugs.json")) {
                jsonb.toJson(jiraBugs, fw);
            }
            System.out.println("File JSON salvati: releases.json, jira_bugs.json");

        } catch (IOException e) {
            System.err.printf("Errore I/O: %s%n", e.getMessage());
            e.printStackTrace();
        }
    }
}
