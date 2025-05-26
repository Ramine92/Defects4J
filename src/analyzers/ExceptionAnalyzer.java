package analyzers;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ExceptionAnalyzer {
    private final Path projectPath;

    public ExceptionAnalyzer(Path projectPath) {
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

            int DJEA = 0;   // Default Java Exception Amount
            int NDJEA = 0;  // Not Default Java Exception Amount

            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            for (MethodDeclaration method : methods) {
                for (ReferenceType thrown : method.getThrownExceptions()) {
                    String exceptionName = thrown.toString();
                    if (isDefaultJavaException(exceptionName)) {
                        DJEA++;
                    } else {
                        NDJEA++;
                    }
                }
            }

            // Tu peux garder les anciennes métriques si tu veux
            metrics.put("DJEA", String.valueOf(DJEA));
            metrics.put("NDJEA", String.valueOf(NDJEA));

            result.put(className, metrics);
        }

        return result;
    }

    // On considère default si le nom commence par java. ou javax.
    private boolean isDefaultJavaException(String exceptionName) {
        // Nettoie les génériques éventuels (Exception<T> -> Exception)
        int genericPos = exceptionName.indexOf('<');
        if (genericPos >= 0) {
            exceptionName = exceptionName.substring(0, genericPos);
        }
        exceptionName = exceptionName.trim();
        // Si fully qualified
        if (exceptionName.startsWith("java.") || exceptionName.startsWith("javax.")) {
            return true;
        }
        // Beaucoup de code n'utilise que le nom simple, alors on teste les principaux noms par défaut
        Set<String> defaultExceptions = Set.of(
                "Exception", "RuntimeException", "NullPointerException", "IllegalArgumentException",
                "IndexOutOfBoundsException", "IllegalStateException", "ClassCastException",
                "ArithmeticException", "ArrayIndexOutOfBoundsException", "ArrayStoreException",
                "UnsupportedOperationException", "NumberFormatException", "IOException", "FileNotFoundException",
                "SQLException", "NoSuchElementException", "IllegalAccessException", "IllegalMonitorStateException",
                "InterruptedException", "CloneNotSupportedException", "InstantiationException"
        );
        return defaultExceptions.contains(exceptionName);
    }
}
