// src/main/java/utils/CsvGenerator.java
package utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import metrics.FeatureExtractor;

import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

public class CsvGenerator {

    /**
     * @param featuresPerMethod <version → (path#sig → features)>
     * @param buggyMethods      <version#path#sig>
     */
    public void generateCsv(
            Map<String, Map<String, FeatureExtractor.MethodFeatures>> featuresPerMethod,
            Set<String> buggyMethods,
            String outputCsv
    ) throws Exception {
        try (CSVPrinter printer = new CSVPrinter(
                new FileWriter(outputCsv),
                CSVFormat.DEFAULT.withHeader(
                        "Version",
                        "File Name",
                        "Method Name",
                        "LOC",
                        "CyclomaticComplexity",
                        "CognitiveComplexity",
                        "ParameterCount",
                        "NestingDepth",
                        "ReturnCount",
                        "TryCount",
                        "CatchCount",
                        "SmellsDensity",
                        "ManyCatches",
                        "AssignmentCount",
                        "InvocationCount",
                        "methodHistories",
                        "Churn",
                        "method_gt_100_loc",
                        "CodeSmellsCount",
                        "Buggy"
                )
        )) {
            for (var relEntry : featuresPerMethod.entrySet()) {
                String version = relEntry.getKey();
                for (var me : relEntry.getValue().entrySet()) {
                    String[] parts   = me.getKey().split("#", 2);
                    String relPath   = parts[0];
                    String fileName  = Paths.get(relPath).getFileName().toString();
                    String methodSig = parts[1];

                    var f = me.getValue();
                    String buggy = buggyMethods.contains(version + "#" + me.getKey()) ? "Yes" : "No";

                    printer.printRecord(
                            version,
                            fileName,
                            methodSig,
                            f.loc,
                            f.cyclomatic,
                            f.cognitive,
                            f.parameterCount,
                            f.nestingDepth,
                            f.returnCount,
                            f.tryCount,
                            f.catchCount,
                            f.smellsDensity,
                            f.manyCatches,
                            f.assignmentCount,
                            f.invocationCount,
                            f.methodHistories,
                            f.churn,
                            f.method_gt_100_loc,
                            f.codeSmells,
                            buggy
                    );
                }
            }
        }
    }
}
