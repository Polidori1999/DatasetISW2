import fetcher.BookkeeperFetcher;
import fetcher.model.JiraTicket;

import java.io.File;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("Avvio del processo di esportazione di tutti i ticket JIRA del progetto BOOKKEEPER");

        // Nome del file di output
        String outputFileName = "bookkeeper_jira_tickets.json";
        File outputFile = new File(outputFileName);

        // Controllo se il file esiste già
        if (outputFile.exists()) {
            System.out.println("✓ Il file " + outputFileName + " esiste già. Nessuna azione eseguita.");
            return;
        }

        // 0. Credenziali JIRA (se necessario)
        String jiraUser = System.getenv("JIRA_USER");
        String jiraPass = System.getenv("JIRA_PASS");
        System.out.printf("Credenziali JIRA presenti: user=%b pass=%b%n",
                jiraUser != null, jiraPass != null);

        // 1. Inizializza il fetcher
        BookkeeperFetcher fetcher = new BookkeeperFetcher();

        // 2. Recupera tutti i ticket JIRA del progetto, cronometrando il download
        System.out.println("Fase 1: recupero di tutti i ticket JIRA...");
        long start = System.nanoTime();
        List<JiraTicket> tickets = fetcher.fetchAllJiraTickets(jiraUser, jiraPass);
        long elapsedNs = System.nanoTime() - start;
        double elapsedSec = elapsedNs / 1_000_000_000.0;
        System.out.printf("✔ Download completato: scaricati %d ticket in %.2f secondi%n",
                tickets.size(), elapsedSec);

        // 3. Scrittura su file JSON
        System.out.printf("Fase 2: scrittura dei ticket su %s...%n", outputFileName);
        fetcher.writeTicketsToJsonFile(tickets, outputFileName);
        System.out.println("✓ File JSON scritto correttamente: " + outputFileName);

        System.out.println("✓ Esportazione completata. File disponibile: " + outputFileName);
    }
}
