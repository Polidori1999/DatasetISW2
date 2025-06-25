package metrics;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Estrae i metodi modificati per ogni commit di bug-fix.
 */
public class BuggyMethodExtractor {
    private final Repository repo;
    private final Git git;

    public BuggyMethodExtractor(Repository repo) {
        this.repo = repo;
        this.git = new Git(repo);
    }

    /**
     * @param commits lista di commit di bug-fix
     * @return mappa commit -> lista di sigle metodi modificati
     */
    public Map<RevCommit, List<String>> extractChangedMethods(List<RevCommit> commits) throws Exception {
        Map<RevCommit, List<String>> result = new HashMap<>();

        try (DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())) {
            df.setRepository(repo);
            ObjectReader reader = repo.newObjectReader();

            for (RevCommit fix : commits) {
                if (fix.getParentCount() == 0) continue;
                RevCommit parent = fix.getParent(0);

                CanonicalTreeParser pIter = new CanonicalTreeParser();
                pIter.reset(reader, parent.getTree());
                CanonicalTreeParser fIter = new CanonicalTreeParser();
                fIter.reset(reader, fix.getTree());

                List<DiffEntry> diffs = df.scan(pIter, fIter);
                List<String> methods = new ArrayList<>();
                for (DiffEntry d : diffs) {
                    if (d.getChangeType() == DiffEntry.ChangeType.MODIFY && d.getOldPath().endsWith(".java")) {
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

    private List<String> changedInSource(String srcBefore, String srcAfter) {
        CompilationUnit cuB = StaticJavaParser.parse(srcBefore);
        CompilationUnit cuA = StaticJavaParser.parse(srcAfter);
        Map<String, MethodDeclaration> mB = parseMethods(cuB);
        Map<String, MethodDeclaration> mA = parseMethods(cuA);
        List<String> changed = new ArrayList<>();
        for (String sig : mB.keySet()) {
            MethodDeclaration b = mB.get(sig), a = mA.get(sig);
            if (a != null && !b.toString().equals(a.toString())) changed.add(sig);
        }
        return changed;
    }

    private Map<String, MethodDeclaration> parseMethods(CompilationUnit cu) {
        Map<String, MethodDeclaration> map = new HashMap<>();
        cu.findAll(MethodDeclaration.class).forEach(md -> {
            // usa signature come identificatore univoco
            String sig = md.getDeclarationAsString(false, false, false);
            map.put(sig, md);
        });
        return map;
    }
}
