
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

            int tryCatchBlocks = cu.findAll(TryStmt.class).size();
            int declaredExceptions = 0;
            int checkedExceptions = 0;
            int uncheckedExceptions = 0;

            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            for (MethodDeclaration method : methods) {
                for (ReferenceType thrown : method.getThrownExceptions()) {
                    declaredExceptions++;
                    String exceptionName = thrown.toString();
                    if (isUnchecked(exceptionName)) uncheckedExceptions++;
                    else checkedExceptions++;
                }
            }

            metrics.put("exceptions_try_catch", String.valueOf(tryCatchBlocks));
            metrics.put("exceptions_checked", String.valueOf(checkedExceptions));
            metrics.put("exceptions_unchecked", String.valueOf(uncheckedExceptions));
            metrics.put("exceptions_declared", String.valueOf(declaredExceptions));

            result.put(className, metrics);
        }

        return result;
    }

    private boolean isUnchecked(String exceptionName) {
        return exceptionName.contains("RuntimeException") ||
                exceptionName.contains("NullPointerException") ||
                exceptionName.contains("IllegalArgumentException") ||
                exceptionName.contains("IndexOutOfBoundsException");
    }
}
