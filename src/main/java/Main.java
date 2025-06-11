import fetcher.JiraInjection;
import fetcher.model.JiraVersion;
import fetcher.model.JiraTicket;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import utils.InstantAdapter;
import utils.LocalDateAdapter;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        // Configuro JSON-B con pretty-print e adapter per Instant/LocalDate
        JsonbConfig cfg = new JsonbConfig()
                .withFormatting(true)
                .withAdapters(new InstantAdapter(), new LocalDateAdapter());
        Jsonb jsonb = JsonbBuilder.create(cfg);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try {
            // --- 1) Carico tutte le JIRA-release ---
            JiraInjection ji = new JiraInjection("BOOKKEEPER", jsonb);
            ji.injectReleases();
            List<JiraVersion> allJv = ji.getReleases();

            Collections.sort(allJv, Comparator.comparing(JiraVersion::getReleaseDate));
            System.out.println("=== Tutte le JIRA Releases disponibili ===");
            allJv.forEach(v ->
                    System.out.println("  • " + v.getName() + " @ " + v.getReleaseDate().format(fmt))
            );
            System.out.printf("Caricate %d JIRA release totali%n%n", allJv.size());

            // --- 2) Seleziono il 34% più vecchio e salvo in releases.json ---
            int cutoff = (int) Math.ceil(allJv.size() * 0.34);
            List<JiraVersion> oldest34 = allJv.subList(0, cutoff);

            System.out.println("=== JIRA Releases selezionate (34% più vecchie) ===");
            oldest34.forEach(v ->
                    System.out.println("  • " + v.getName() + " @ " + v.getReleaseDate().format(fmt))
            );
            System.out.printf("Selezionate %d release%n%n", oldest34.size());

            try (FileWriter fw = new FileWriter("releases.json")) {
                jsonb.toJson(oldest34, fw);
            }
            System.out.println("File releases.json scritto.\n");

            // --- 3) Recupero TUTTI i Bug Fixed/Closed/Resolved (tutte le release) ---
            System.out.println("Recupero tutti i ticket Bug risolti...");
            List<JiraTicket> bugs = ji.fetchFixedBugs();
            System.out.printf("Trovati %d ticket%n%n", bugs.size());

            // --- 4) Salvo in jira_bugs_fixed.json ---
            try (PrintWriter pw = new PrintWriter("jira_bugs_fixed.json")) {
                pw.println("[");
                for (int i = 0; i < bugs.size(); i++) {
                    String obj = jsonb.toJson(bugs.get(i));
                    String indented = Arrays.stream(obj.split("\n"))
                            .map(line -> "  " + line)
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse(obj);
                    pw.println(indented);
                    if (i < bugs.size() - 1) {
                        pw.println(",");
                        pw.println();
                    }
                }
                pw.println("]");
            }
            System.out.println("File jira_bugs_fixed.json scritto.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
