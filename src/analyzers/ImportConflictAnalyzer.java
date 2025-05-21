package analyzers;


import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.utils.SourceRoot;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImportConflictAnalyzer {
    private final Path projectPath;

    public ImportConflictAnalyzer(Path projectPath) {
        this.projectPath = projectPath;
    }

    public Map<String, String> analyze() throws IOException {
        Map<String, String> result = new HashMap<>();
        int totalConflicts = 0;
        int starImports = 0;

        SourceRoot sourceRoot = new SourceRoot(projectPath.resolve("src"));
        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse();

        for (ParseResult<CompilationUnit> parseResult : parseResults) {
            if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                continue;
            }

            CompilationUnit cu = parseResult.getResult().get();
            Map<String, Set<String>> importMap = new HashMap<>();

            // Analyser toutes les déclarations d'import
            for (ImportDeclaration importDecl : cu.getImports()) {
                if (importDecl.isAsterisk()) {
                    starImports++;
                    continue;
                }

                String fullName = importDecl.getNameAsString();
                String className = getClassName(fullName);
                String packageName = getPackageName(fullName);

                importMap.putIfAbsent(className, new HashSet<>());
                importMap.get(className).add(packageName);
            }

            // Compter les conflits pour cette unité de compilation
            for (Set<String> packages : importMap.values()) {
                if (packages.size() > 1) {
                    totalConflicts += packages.size() - 1;
                }
            }
        }

        result.put("import_conflicts", String.valueOf(totalConflicts));
        result.put("star_imports", String.valueOf(starImports));
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
