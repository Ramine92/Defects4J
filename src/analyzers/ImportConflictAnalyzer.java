package analyzers;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
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

            // Collect all import declarations
            List<ImportDeclaration> imports = cu.getImports();

            // Map for import conflicts: classSimpleName -> set of packages
            Map<String, Set<String>> importMap = new HashMap<>();
            // Count duplicate imports
            Map<String, Integer> importCount = new HashMap<>();
            // Set for all imported qualified names
            Set<String> importQualifiedNames = new HashSet<>();
            // Set for all simple imported class names
            Set<String> importedClassNames = new HashSet<>();

            int icd = 0; // Importations en double
            int icc = 0; // Importations conflictuelles

            for (ImportDeclaration importDecl : imports) {
                if (importDecl.isAsterisk()) continue; // ignore star imports for this metric

                String fullName = importDecl.getNameAsString();
                String classShortName = getClassName(fullName);
                String packageName = getPackageName(fullName);
                String importKey = fullName + (importDecl.isStatic() ? " static" : "");

                // For double imports (ICD)
                if (importQualifiedNames.contains(importKey)) {
                    icd++;
                } else {
                    importQualifiedNames.add(importKey);
                }

                // For conflict (ICC)
                importMap.putIfAbsent(classShortName, new HashSet<>());
                importMap.get(classShortName).add(packageName);

                // For usage
                importedClassNames.add(classShortName);

                // For counting all imports
                importCount.put(importKey, importCount.getOrDefault(importKey, 0) + 1);
            }

            // ICC: count how many class names are imported from more than one package
            for (Set<String> pkgs : importMap.values()) {
                if (pkgs.size() > 1) icc++;
            }

            // ICU/ICNU: check if imported class name is used in the code
            String cuAsString = cu.toString();
            int icu = 0;
            int icnu = 0;
            for (String classNameShort : importedClassNames) {
                // Check if simple class name is used in the code (naive: search for class name in source)
                if (cuAsString.contains(classNameShort)) {
                    icu++;
                } else {
                    icnu++;
                }
            }

            metrics.put("ICU", String.valueOf(icu));
            metrics.put("ICNU", String.valueOf(icnu));
            metrics.put("ICD", String.valueOf(icd));
            metrics.put("ICC", String.valueOf(icc));
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