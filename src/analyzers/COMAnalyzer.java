package analyzers;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class COMAnalyzer {
    private final Path projectPath;

    public COMAnalyzer(Path projectPath) {
        this.projectPath = projectPath;
    }

    // Retourne un Map<ClassName, Map<metric, value>>
    public Map<String, Map<String, String>> analyze() throws IOException {
        Map<String, Map<String, String>> result = new HashMap<>();

        SourceRoot sourceRoot = new SourceRoot(projectPath.resolve("src"));
        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse();

        for (ParseResult<CompilationUnit> parseResult : parseResults) {
            if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) continue;
            CompilationUnit cu = parseResult.getResult().get();

            List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
            for (ClassOrInterfaceDeclaration clazz : classes) {
                if (clazz.isInterface()) continue; // Ignore interfaces

                double cohesionRate = computeCohesionRate(clazz);

                Map<String, String> metrics = new HashMap<>();
                metrics.put("CohesionRate", String.format(Locale.US, "%.4f", cohesionRate));
                result.put(clazz.getNameAsString(), metrics);
            }
        }
        return result;
    }

    // Calcule cohesionRate = (nb méthodes avec au moins un attribut en commun avec une autre) / (nb total de méthodes)
    private double computeCohesionRate(ClassOrInterfaceDeclaration clazz) {
        List<String> fieldNames = new ArrayList<>();
        for (FieldDeclaration field : clazz.getFields()) {
            field.getVariables().forEach(var -> fieldNames.add(var.getNameAsString()));
        }

        List<MethodDeclaration> methods = clazz.getMethods();
        // On ignore les méthodes sans corps (abstract, interface, etc.)
        List<MethodDeclaration> realMethods = new ArrayList<>();
        for (MethodDeclaration m : methods) {
            if (m.getBody().isPresent()) realMethods.add(m);
        }
        int n = realMethods.size();
        if (n == 0) return 1.0; // convention : classe sans méthode = 100% cohésive

        // Pour chaque méthode, liste des attributs accédés
        List<Set<String>> methodFields = new ArrayList<>();
        for (MethodDeclaration m : realMethods) {
            Set<String> accessed = new HashSet<>();
            for (String field : fieldNames) {
                if (m.toString().contains(field)) { // naïf mais efficace
                    accessed.add(field);
                }
            }
            methodFields.add(accessed);
        }

        // Compte le nombre de méthodes qui partagent au moins un attribut avec au moins une autre
        int methodsWithCommonField = 0;
        for (int i = 0; i < n; i++) {
            boolean sharesField = false;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                Set<String> intersection = new HashSet<>(methodFields.get(i));
                intersection.retainAll(methodFields.get(j));
                if (!intersection.isEmpty()) {
                    sharesField = true;
                    break;
                }
            }
            if (sharesField) methodsWithCommonField++;
        }
        return (double) methodsWithCommonField / n;
    }
}