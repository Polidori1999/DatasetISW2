package utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import metrics.FeatureExtractor;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Genera il CSV per Milestone1 con le metriche:
 *  project, methodFQN, release,
 *  loc, cyclomatic, cognitive, halsteadVolume,
 *  nestingDepth, codeSmells, methodHistories, churn, authors,
 *  method_gt_100_loc, buggy
 */
public class CsvGenerator {

    private final int version;

    public CsvGenerator(int version) {
        this.version = version;
    }

    /**
     * @param featuresPerMethod mappa (release -> (methodFQN -> MethodFeatures))
     * @param buggyMethods      insieme di "release#methodFQN" considerati buggy
     * @param outputCsv         path del file di output
     */
    public void generateCsv(
            Map<String, Map<String, FeatureExtractor.MethodFeatures>> featuresPerMethod,
            Set<String> buggyMethods,
            String outputCsv
    ) throws Exception {
        try (CSVPrinter printer = new CSVPrinter(
                new FileWriter(outputCsv),
                CSVFormat.DEFAULT.withHeader(
                        "project",
                        "method",
                        "release",
                        "loc",
                        "cyclomatic",
                        "cognitive",
                        "halsteadVolume",
                        "nestingDepth",
                        "codeSmells",
                        "methodHistories",
                        "churn",
                        "authors",
                        "method_gt_100_loc",
                        "buggy"
                )
        )) {
            for (Map.Entry<String, Map<String, FeatureExtractor.MethodFeatures>> relEntry : featuresPerMethod.entrySet()) {
                String release = relEntry.getKey();
                for (Map.Entry<String, FeatureExtractor.MethodFeatures> me : relEntry.getValue().entrySet()) {
                    String methodFqn = me.getKey();
                    FeatureExtractor.MethodFeatures f = me.getValue();

                    String buggyKey = release + "#" + methodFqn;
                    String buggy = buggyMethods.contains(buggyKey) ? "Yes" : "No";

                    printer.printRecord(
                            "bookkeeper",
                            methodFqn,
                            release,
                            f.loc,
                            f.cyclomatic,
                            f.cognitive,
                            f.halsteadVolume,
                            f.nestingDepth,
                            f.codeSmells,
                            f.methodHistories,
                            f.churn,
                            f.authors,
                            f.method_gt_100_loc,
                            buggy
                    );
                }
            }
        }
    }
}
