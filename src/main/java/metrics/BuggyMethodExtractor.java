// src/main/java/metrics/BuggyMethodExtractor.java
package metrics;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class BuggyMethodExtractor {
    private final Repository repo;
    private final Git git;

    public BuggyMethodExtractor(Repository repo) {
        this.repo = repo;
        this.git  = new Git(repo);
    }

    /**
     * Restituisce per ogni commit di bug-fix la lista delle signature modificate.
     */
    public Map<RevCommit, List<String>> extractChangedMethods(List<RevCommit> commits) throws Exception {
        Map<RevCommit, List<String>> result = new HashMap<>();
        try (DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())) {
            df.setRepository(repo);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            ObjectReader reader = repo.newObjectReader();

            for (RevCommit fix : commits) {
                if (fix.getParentCount() == 0) continue;
                RevCommit parent = fix.getParent(0);

                CanonicalTreeParser pIter = new CanonicalTreeParser();
                pIter.reset(reader, parent.getTree());
                CanonicalTreeParser fIter = new CanonicalTreeParser();
                fIter.reset(reader, fix.getTree());

                List<String> methods = new ArrayList<>();
                for (DiffEntry d : df.scan(pIter, fIter)) {
                    if (d.getChangeType() == DiffEntry.ChangeType.MODIFY
                            && d.getOldPath().endsWith(".java")) {
                        String before = new String(reader.open(d.getOldId().toObjectId()).getBytes());
                        String after  = new String(reader.open(d.getNewId().toObjectId()).getBytes());
                        methods.addAll(changedInSource(before, after));
                    }
                }
                result.put(fix, methods);
            }
        }
        return result;
    }

    /**
     * Conta quante volte ciascun metodo è stato toccato da un bug-fix.
     */
    public Map<String,Integer> calculateMethodHistories(List<RevCommit> commits) throws Exception {
        Map<String,Integer> histories = new HashMap<>();
        for (List<String> sigs : extractChangedMethods(commits).values()) {
            for (String sig : sigs) {
                histories.merge(sig, 1, Integer::sum);
            }
        }
        return histories;
    }

    /**
     * Calcola churn = linee aggiunte + rimosse per metodo, su tutti i bug-fix.
     */
    public Map<String,Integer> calculateMethodChurn(List<RevCommit> commits) throws Exception {
        Map<String,Integer> churnMap = new HashMap<>();
        try (DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())) {
            df.setRepository(repo);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            ObjectReader reader = repo.newObjectReader();

            for (RevCommit fix : commits) {
                if (fix.getParentCount() == 0) continue;
                RevCommit parent = fix.getParent(0);

                CanonicalTreeParser pIter = new CanonicalTreeParser();
                pIter.reset(reader, parent.getTree());
                CanonicalTreeParser fIter = new CanonicalTreeParser();
                fIter.reset(reader, fix.getTree());

                for (DiffEntry d : df.scan(pIter, fIter)) {
                    if (d.getChangeType() == DiffEntry.ChangeType.MODIFY
                            && d.getOldPath().endsWith(".java")) {
                        // per ogni edit
                        df.toFileHeader(d).toEditList().forEach(e -> {
                            int added   = e.getEndB() - e.getBeginB();
                            int deleted = e.getEndA() - e.getBeginA();
                            int delta   = added + deleted;
                            // parsed after-version
                            String afterSrc = null;
                            try {
                                afterSrc = new String(reader.open(d.getNewId().toObjectId()).getBytes());
                            } catch (Exception ex) { /* ignore */ }
                            if (afterSrc != null) {
                                // per ogni metodo che copre questa area, aggiungo churn
                                for (MethodDeclaration md : StaticJavaParser.parse(afterSrc)
                                        .findAll(MethodDeclaration.class)) {
                                    md.getRange().ifPresent(r -> {
                                        if (e.getBeginB()+1 <= r.end.line && e.getEndB() >= r.begin.line) {
                                            String sig = md.getDeclarationAsString(false,false,false);
                                            churnMap.merge(sig, delta, Integer::sum);
                                        }
                                    });
                                }
                            }
                        });
                    }
                }
            }
        }
        return churnMap;
    }

    // ——— Helpers privati ———

    private List<String> changedInSource(String b, String a) {
        var cuB = StaticJavaParser.parse(b);
        var cuA = StaticJavaParser.parse(a);
        Map<String,MethodDeclaration> mB = parseMethods(cuB);
        Map<String,MethodDeclaration> mA = parseMethods(cuA);
        List<String> changed = new ArrayList<>();
        for (String sig : mB.keySet()) {
            var before = mB.get(sig);
            var after  = mA.get(sig);
            if (after!=null && !before.toString().equals(after.toString())) {
                changed.add(sig);
            }
        }
        return changed;
    }

    private Map<String,MethodDeclaration> parseMethods(CompilationUnit cu) {
        Map<String,MethodDeclaration> map = new HashMap<>();
        cu.findAll(MethodDeclaration.class).forEach(md ->
                map.put(md.getDeclarationAsString(false,false,false), md)
        );
        return map;
    }
}
