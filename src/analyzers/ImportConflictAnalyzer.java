package analyzers;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ImportConflictAnalyzer {
    private final Path projectPath;

    public ImportConflictAnalyzer(Path projectPath) {
        this.projectPath = projectPath;
    }

    public Map<String, Map<String, String>> analyze() throws IOException {
        Map<String, Map<String, String>> result = new HashMap<>();
        SourceRoot sourceRoot = new SourceRoot(projectPath.resolve("src"));
        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse();

        for (ParseResult<CompilationUnit> parseResult : parseResults) {
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) continue;

            CompilationUnit cu = parseResult.getResult().get();
            String className = cu.getPrimaryTypeName().orElse("UnknownClass");
            Map<String, String> metrics = new HashMap<>();

            int starImports = 0;
            int totalConflicts = 0;
            Map<String, Set<String>> importMap = new HashMap<>();

            for (ImportDeclaration importDecl : cu.getImports()) {
                if (importDecl.isAsterisk()) {
                    starImports++;
                    continue;
                }

                String fullName = importDecl.getNameAsString();
                String classShortName = getClassName(fullName);
                String packageName = getPackageName(fullName);

                importMap.putIfAbsent(classShortName, new HashSet<>());
                importMap.get(classShortName).add(packageName);
            }

            for (Set<String> packages : importMap.values()) {
                if (packages.size() > 1) {
                    totalConflicts += packages.size() - 1;
                }
            }

            metrics.put("import_conflicts", String.valueOf(totalConflicts));
            metrics.put("star_imports", String.valueOf(starImports));
            result.put(className, metrics);
        }

        return result;
    }

    private String getClassName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot == -1 ? fullName : fullName.substring(lastDot + 1);
    }

    private String getPackageName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot == -1 ? "" : fullName.substring(0, lastDot);
    }
}

