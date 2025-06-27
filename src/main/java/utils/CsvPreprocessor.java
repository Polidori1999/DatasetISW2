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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CsvPreprocessor {

    /**
     * Rimuove le righe duplicate (basate su tutte le colonne).
     */
    public static void removeDuplicateRows(Path inputCsv, Path outputCsv) throws IOException {
        try (
                Reader in     = Files.newBufferedReader(inputCsv);
                CSVParser parser = new CSVParser(in, CSVFormat.DEFAULT.withFirstRecordAsHeader())
        ) {
            List<String> headers = parser.getHeaderNames();

            Set<String> seen = new HashSet<>();
            List<CSVRecord> uniqueRecords = new ArrayList<>();

            for (CSVRecord record : parser) {
                // costruisco manualmente la chiave unica
                StringBuilder keyBuilder = new StringBuilder();
                for (String h : headers) {
                    String v = record.get(h);
                    keyBuilder.append(v == null ? "" : v.trim());
                    keyBuilder.append("|");
                }
                String key = keyBuilder.toString();
                if (!seen.contains(key)) {
                    seen.add(key);
                    uniqueRecords.add(record);
                }
            }

            // riscrivo solo i record unici
            try (
                    Writer out     = Files.newBufferedWriter(outputCsv);
                    CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0])))
            ) {
                for (CSVRecord record : uniqueRecords) {
                    List<String> row = new ArrayList<>();
                    for (String h : headers) {
                        row.add(record.get(h));
                    }
                    printer.printRecord(row);
                }
            }
        }
    }
}
