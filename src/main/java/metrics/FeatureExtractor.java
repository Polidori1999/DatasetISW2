package metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;


import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class FeatureExtractor {

    public static class MethodFeatures {
        public int loc;
        public int cyclomatic;
        public int cognitive;
        public double halsteadVolume;
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
        Map<String, MethodFeatures> result = new HashMap<>();

        // remove comments
        cu.getAllComments().forEach(Comment::remove);

        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
            MethodFeatures f = new MethodFeatures();
            String code = md.toString();

            // LOC
            f.loc = (int) code.lines().filter(l -> !l.isBlank()).count();

            // complexity
            ComplexityVisitor cv = new ComplexityVisitor();
            cv.visit(md, 0);
            f.cyclomatic = cv.decisionPoints + 1;
            f.cognitive = cv.decisionPoints;

            // placeholders
            f.halsteadVolume = 0;
            f.nestingDepth = cv.maxDepth;
            f.codeSmells = 0;
            f.methodHistories = 0;
            f.churn = 0;
            f.authors = 0;
            f.method_gt_100_loc = f.loc > 100 ? 1 : 0;

            String sig = md.getDeclarationAsString(false, false, false);
            result.put(sig, f);
        }
        return result;
    }

    private static class ComplexityVisitor extends VoidVisitorAdapter<Integer> {
        int decisionPoints = 0;
        int maxDepth = 0;

        @Override
        public void visit(IfStmt n, Integer depth) {
            decisionPoints++;
            super.visit(n, depth == null ? 1 : depth + 1);
        }

        @Override
        public void visit(ForStmt n, Integer depth) {
            decisionPoints++;
            super.visit(n, depth == null ? 1 : depth + 1);
        }

        @Override
        public void visit(WhileStmt n, Integer depth) {
            decisionPoints++;
            super.visit(n, depth == null ? 1 : depth + 1);
        }

        @Override
        public void visit(DoStmt n, Integer depth) {
            decisionPoints++;
            super.visit(n, depth == null ? 1 : depth + 1);
        }

        @Override
        public void visit(SwitchEntry n, Integer depth) {
            if (!n.getLabels().isEmpty()) decisionPoints++;
            super.visit(n, depth == null ? 1 : depth + 1);
        }
    }
}
