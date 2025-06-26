// src/main/java/metrics/FeatureExtractor.java
package metrics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.RuleSetFactory;
import net.sourceforge.pmd.RuleSets;
import net.sourceforge.pmd.SourceCodeProcessor;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FeatureExtractor {

    private final Path repoRoot;

    public FeatureExtractor(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    public static class MethodFeatures {
        public int loc;
        public int cyclomatic;
        public int cognitive;
        public int parameterCount;
        public int nestingDepth;
        public int returnCount;
        public int tryCount;
        public int catchCount;
        public int smellsDensity;
        public int manyCatches;
        public int assignmentCount;
        public int invocationCount;
        public int methodHistories;
        public int churn;
        public int method_gt_100_loc;
        public int codeSmells;
    }

    public Map<String, MethodFeatures> extractFromFile(File javaFile) throws Exception {
        // sopprime i log di PMD
        Logger.getLogger("net.sourceforge.pmd").setLevel(Level.SEVERE);

        // 0) Code Smells via PMD 6.55.0
        PMDConfiguration cfg = new PMDConfiguration();
        cfg.setDefaultLanguageVersion(
                LanguageRegistry.getLanguage(JavaLanguageModule.NAME).getDefaultVersion()
        );
        RuleSetFactory rsf = new RuleSetFactory();
        RuleSets ruleSets = rsf.createRuleSets("category/java/bestpractices.xml");
        RuleContext ctx = new RuleContext();
        ctx.setSourceCodeFile(javaFile);
        Report report = new Report();
        ctx.setReport(report);
        new SourceCodeProcessor(cfg)
                .processSourceCode(new FileInputStream(javaFile), ruleSets, ctx);
        int codeSmellsCount = report.getViolations().size();

        // 1) AST analysis con JavaParser
        String src = Files.readString(javaFile.toPath());
        CompilationUnit cu = StaticJavaParser.parse(src);
        cu.getAllComments().forEach(Comment::remove);

        Map<String, MethodFeatures> result = new HashMap<>();
        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
            MethodFeatures f = new MethodFeatures();
            String code = md.toString();

            // LOC
            f.loc = (int) code.lines().filter(l -> !l.isBlank()).count();

            // Cyclomatic + Cognitive + NestingDepth
            ComplexityVisitor cv = new ComplexityVisitor();
            cv.visit(md, 0);
            f.cyclomatic = cv.decisionPoints + 1;
            f.cognitive  = cv.decisionPoints;
            f.nestingDepth = cv.maxDepth;

            // Parameter Count
            f.parameterCount = md.getParameters().size();

            // Code Smells
            f.codeSmells = codeSmellsCount;

            // Return Count
            f.returnCount = md.findAll(ReturnStmt.class).size();

            // Try/Catch Count
            f.tryCount   = md.findAll(TryStmt.class).size();
            f.catchCount = md.findAll(CatchClause.class).size();

            // Smells density per 100 LOC
            f.smellsDensity = f.loc > 0 ? (codeSmellsCount * 100) / f.loc : 0;

            // Many catches actionable
            f.manyCatches = f.catchCount > 2 ? 1 : 0;

            // Nuove metriche:
            f.assignmentCount   = md.findAll(AssignExpr.class).size();
            f.invocationCount   = md.findAll(MethodCallExpr.class).size();

            // Evolution placeholders
            f.methodHistories = 0;
            f.churn           = 0;

            // Existing actionable: troppo lungo
            f.method_gt_100_loc = f.loc > 100 ? 1 : 0;

            // Chiave = relPath#signature
            Path rel = repoRoot.relativize(javaFile.toPath());
            String filePath = rel.toString();
            String sig      = md.getDeclarationAsString(false, false, false);
            result.put(filePath + "#" + sig, f);
        }
        return result;
    }

    private static class ComplexityVisitor extends VoidVisitorAdapter<Integer> {
        int decisionPoints = 0, maxDepth = 0;

        @Override public void visit(IfStmt n, Integer d) {
            decisionPoints++; int nd = (d == null ? 1 : d + 1);
            maxDepth = Math.max(maxDepth, nd);
            super.visit(n, nd);
        }
        @Override public void visit(ForStmt n, Integer d) {
            decisionPoints++; int nd = (d == null ? 1 : d + 1);
            maxDepth = Math.max(maxDepth, nd);
            super.visit(n, nd);
        }
        @Override public void visit(WhileStmt n, Integer d) {
            decisionPoints++; int nd = (d == null ? 1 : d + 1);
            maxDepth = Math.max(maxDepth, nd);
            super.visit(n, nd);
        }
        @Override public void visit(DoStmt n, Integer d) {
            decisionPoints++; int nd = (d == null ? 1 : d + 1);
            maxDepth = Math.max(maxDepth, nd);
            super.visit(n, nd);
        }
        @Override public void visit(SwitchEntry n, Integer d) {
            if (!n.getLabels().isEmpty()) decisionPoints++;
            int nd = (d == null ? 1 : d + 1);
            maxDepth = Math.max(maxDepth, nd);
            super.visit(n, nd);
        }
    }
}
