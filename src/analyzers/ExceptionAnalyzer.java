package analyzers;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExceptionAnalyzer {
    private final Path projectPath;

    public ExceptionAnalyzer(Path projectPath) {
        this.projectPath = projectPath;
    }

    public Map<String, String> analyze() throws IOException {
        Map<String, String> result = new HashMap<>();
        int tryCatchBlocks = 0;
        int checkedExceptions = 0;
        int uncheckedExceptions = 0;
        int declaredExceptions = 0;

        SourceRoot sourceRoot = new SourceRoot(projectPath.resolve("src"));
        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse();

        for (ParseResult<CompilationUnit> parseResult : parseResults) {
            if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                continue;
            }

            CompilationUnit cu = parseResult.getResult().get();

            // Nombre de blocs try/catch
            List<TryStmt> tryStmts = cu.findAll(TryStmt.class);
            tryCatchBlocks += tryStmts.size();

            // Parcourir les méthodes pour analyser exceptions déclarées
            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            for (MethodDeclaration method : methods) {
                List<ReferenceType> thrownExceptions = method.getThrownExceptions();
                declaredExceptions += thrownExceptions.size();

                for (ReferenceType thrown : thrownExceptions) {
                    String exceptionName = thrown.toString();
                    if (isUnchecked(exceptionName)) {
                        uncheckedExceptions++;
                    } else {
                        checkedExceptions++;
                    }
                }
            }
        }

        result.put("exceptions_try_catch", String.valueOf(tryCatchBlocks));
        result.put("exceptions_checked", String.valueOf(checkedExceptions));
        result.put("exceptions_unchecked", String.valueOf(uncheckedExceptions));
        result.put("exceptions_declared", String.valueOf(declaredExceptions));

        return result;
    }

    // Méthode simple de classification par nom
    private boolean isUnchecked(String exceptionName) {
        return exceptionName.contains("RuntimeException") ||
                exceptionName.contains("NullPointerException") ||
                exceptionName.contains("IllegalArgumentException") ||
                exceptionName.contains("IndexOutOfBoundsException");
    }
}

