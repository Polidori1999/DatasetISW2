package utils;

import jakarta.json.bind.adapter.JsonbAdapter;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Converte   "yyyy-MM-dd"  oppure  "yyyy-MM-ddTHH:mm:ss.SSSZ"
 * in LocalDate e viceversa.
 */
public class LocalDateAdapter implements JsonbAdapter<LocalDate, String> {

    // formatter per i timestamp completi
    private static final DateTimeFormatter ISO_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @Override
    public String adaptToJson(LocalDate obj) {
        return obj.toString();                // sempre "yyyy-MM-dd"
    }

    @Override
    public LocalDate adaptFromJson(String str) {
        try {
            if (str.contains("T")) {          // timestamp completo
                return OffsetDateTime.parse(str, ISO_TS)
                        .toLocalDate();
            }
            return LocalDate.parse(str);      // solo data
        } catch (DateTimeParseException ex) {
            // Ultimo tentativo: taglia ai primi 10 caratteri
            return LocalDate.parse(str.substring(0, 10));
        }
    }
}
