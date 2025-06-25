// src/main/java/metrics/FeatureExtractor.java
package metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FeatureExtractor {

    private final Path repoRoot;

    public FeatureExtractor(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    public static class MethodFeatures {
        public int loc;
        public int cyclomatic;
        public int cognitive;
        public int parameterCount;      // NEW
        public int nestingDepth;
        public int codeSmells;
        public int methodHistories;
        public int churn;
        public int authors;
        public int method_gt_100_loc;
    }

    public Map<String, MethodFeatures> extractFromFile(File javaFile) throws Exception {
        String src = Files.readString(javaFile.toPath());
        CompilationUnit cu = StaticJavaParser.parse(src);
        cu.getAllComments().forEach(Comment::remove);

        Map<String, MethodFeatures> result = new HashMap<>();
        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
            MethodFeatures f = new MethodFeatures();
            String code = md.toString();

            // 1) LOC
            f.loc = (int) code.lines().filter(l -> !l.isBlank()).count();

            // 2) Cyclomatic & Cognitive
            ComplexityVisitor cv = new ComplexityVisitor();
            cv.visit(md, 0);
            f.cyclomatic   = cv.decisionPoints + 1;
            f.cognitive    = cv.decisionPoints;

            // 3) Parameter Count (replacement for Halstead)
            f.parameterCount = md.getParameters().size();

            // 4) Nesting Depth
            f.nestingDepth = cv.maxDepth;

            // 5) Code Smells placeholder
            f.codeSmells = 0;

            // 6) Evolution placeholders
            f.methodHistories = 0;
            f.churn           = 0;
            f.authors         = 0;

            // 7) Actionable flag
            f.method_gt_100_loc = f.loc > 100 ? 1 : 0;

            // build key = relPath#signature
            Path rel = repoRoot.relativize(javaFile.toPath());
            String filePath = rel.toString();
            String sig = md.getDeclarationAsString(false, false, false);
            result.put(filePath + "#" + sig, f);
        }
        return result;
    }

    private static class ComplexityVisitor extends VoidVisitorAdapter<Integer> {
        int decisionPoints = 0, maxDepth = 0;

        @Override public void visit(IfStmt n, Integer d) {
            decisionPoints++;
            int nd = d == null ? 1 : d + 1;
            maxDepth = Math.max(maxDepth, nd);
            super.visit(n, nd);
        }
        @Override public void visit(ForStmt n, Integer d) {
            decisionPoints++;
            int nd = d == null ? 1 : d + 1;
            maxDepth = Math.max(maxDepth, nd);
            super.visit(n, nd);
        }
        @Override public void visit(WhileStmt n, Integer d) {
            decisionPoints++;
            int nd = d == null ? 1 : d + 1;
            maxDepth = Math.max(maxDepth, nd);
            super.visit(n, nd);
        }
        @Override public void visit(DoStmt n, Integer d) {
            decisionPoints++;
            int nd = d == null ? 1 : d + 1;
            maxDepth = Math.max(maxDepth, nd);
            super.visit(n, nd);
        }
        @Override public void visit(SwitchEntry n, Integer d) {
            if (!n.getLabels().isEmpty()) decisionPoints++;
            int nd = d == null ? 1 : d + 1;
            maxDepth = Math.max(maxDepth, nd);
            super.visit(n, nd);
        }
    }
}
