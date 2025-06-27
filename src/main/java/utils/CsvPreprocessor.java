package utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CsvPreprocessor {

    /**
     * Raggruppa le righe per tutte le colonne tranne "Version",
     * e per ogni gruppo tiene solo la riga con la versione più vecchia.
     */
    public static void removeDuplicateRows(Path inputCsv, Path outputCsv) throws IOException {
        try (
                Reader in     = Files.newBufferedReader(inputCsv);
                CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT.withFirstRecordAsHeader())
        ) {
            List<String> headers = parser.getHeaderNames();
            int versionIdx = headers.indexOf("Version");
            if (versionIdx < 0) {
                throw new IllegalStateException("Header 'Version' non trovato");
            }

            // key → record con la versione più vecchia
            LinkedHashMap<String,CSVRecord> oldest = new LinkedHashMap<>();

            for (CSVRecord record : parser) {
                // costruisco la chiave escludendo la colonna Version
                StringBuilder keyB = new StringBuilder();
                for (int i = 0; i < headers.size(); i++) {
                    if (i == versionIdx) continue;
                    keyB.append(record.get(i).trim()).append("|");
                }
                String key = keyB.toString();
                String ver = record.get(versionIdx).trim();

                if (!oldest.containsKey(key)) {
                    oldest.put(key, record);
                } else {
                    String prevVer = oldest.get(key).get(versionIdx).trim();
                    // se questa è più vecchia della precedente, la sostituisco
                    if (compareVersion(ver, prevVer) < 0) {
                        oldest.put(key, record);
                    }
                }
            }

            // riscrivo l’output
            try (
                    Writer out     = Files.newBufferedWriter(outputCsv);
                    CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0])))
            ) {
                for (CSVRecord r : oldest.values()) {
                    List<String> row = new ArrayList<>();
                    for (String h : headers) row.add(r.get(h));
                    printer.printRecord(row);
                }
            }
        }
    }

    /**
     * Confronta semanticamente due stringhe di versione del tipo
     * "release-4.2.1", "v4.10.0" o "HEAD" (che consideriamo sempre minima).
     * Restituisce >0 se a>b, <0 se a<b, 0 se uguali.
     */
    private static int compareVersion(String a, String b) {
        // HEAD la consideriamo sempre la più vecchia
        if ("HEAD".equals(a) && !"HEAD".equals(b)) return -1;
        if (!"HEAD".equals(a) && "HEAD".equals(b)) return  1;
        if ("HEAD".equals(a) && "HEAD".equals(b)) return 0;

        // rimuovo prefissi
        a = a.replaceFirst("^(?:v|release-)", "");
        b = b.replaceFirst("^(?:v|release-)", "");
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int vb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }
}
