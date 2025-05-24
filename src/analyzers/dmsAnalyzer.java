
package analyzers;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class dmsAnalyzer {
    private final Path projectPath;

    // Poids de chaque composant du score
    private static final double OOMR_WEIGHT = 2.0;
    private static final double CYCLO_WEIGHT = 1.5;
    private static final double DANGER_WEIGHT = 1.0;

    // Maximums approximatifs pour normalisation
    private static final int MAX_OOMR = 50;
    private static final int MAX_CYCLO = 30;
    private static final int MAX_DANGER = 10;

    public dmsAnalyzer(Path projectPath) {
        this.projectPath = projectPath;
    }

    public Map<String, Map<String, String>> analyze() throws IOException {
        JavaParser parser = new JavaParser();
        Map<String, Map<String, String>> result = new HashMap<>();

        try (Stream<Path> paths = Files.walk(projectPath)) {
            List<Path> javaFiles = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                try {
                    ParseResult<CompilationUnit> resultCU = parser.parse(javaFile);
                    if (!resultCU.isSuccessful() || resultCU.getResult().isEmpty()) continue;

                    CompilationUnit cu = resultCU.getResult().get();

                    List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
                    for (ClassOrInterfaceDeclaration clazz : classes) {
                        String className = clazz.getNameAsString();

                        List<MethodDeclaration> methods = clazz.findAll(MethodDeclaration.class);

                        double cumulativeScore = 0.0;
                        int methodCount = 0;

                        for (MethodDeclaration method : methods) {
                            int oomrTotal = method.findAll(MethodCallExpr.class).size();

                            int cycloTotal = method.findAll(IfStmt.class).size()
                                    + method.findAll(ForStmt.class).size()
                                    + method.findAll(ForEachStmt.class).size()
                                    + method.findAll(WhileStmt.class).size()
                                    + method.findAll(DoStmt.class).size()
                                    + method.findAll(SwitchStmt.class).size();

                            int dangerCalls = 0;
                            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                                if (isCriticalMethod(call)) dangerCalls++;
                            }

                            double rawScore = (oomrTotal * OOMR_WEIGHT)
                                    + (cycloTotal * CYCLO_WEIGHT)
                                    + (dangerCalls * DANGER_WEIGHT);

                            double normalizedScore = rawScore / (
                                    (MAX_OOMR * OOMR_WEIGHT) +
                                            (MAX_CYCLO * CYCLO_WEIGHT) +
                                            (MAX_DANGER * DANGER_WEIGHT)
                            );

                            cumulativeScore += Math.min(normalizedScore, 1.0);
                            methodCount++;
                        }

                        double finalScore = (methodCount > 0) ? cumulativeScore / methodCount : 0.0;

                        Map<String, String> metrics = new HashMap<>();
                        metrics.put("dms_score", String.format(Locale.US, "%.4f", finalScore));
                        result.put(className, metrics);
                    }

                } catch (Exception e) {
                    System.err.println("Erreur lors de l'analyse du fichier: " + javaFile);
                    e.printStackTrace();
                }
            }
        }

        return result;
    }

    private boolean isCriticalMethod(MethodCallExpr call) {
        String callStr = call.toString();
        return callStr.contains("System.exit")
                || callStr.contains("Thread.sleep")
                || callStr.contains("Runtime.getRuntime")
                || callStr.contains("exec");
    }
}
