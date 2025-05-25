
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

public class LCOMAnalyzer {
    private final Path projectPath;

    public LCOMAnalyzer(Path projectPath) {
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

                int lcom4 = computeLCOM4(clazz);

                Map<String, String> metrics = new HashMap<>();
                metrics.put("Lcom", String.valueOf(lcom4));
                result.put(clazz.getNameAsString(), metrics);
            }
        }
        return result;
    }

    // Calcule LCOM4 pour une classe donnée
    private int computeLCOM4(ClassOrInterfaceDeclaration clazz) {
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

        // Construction du graphe : chaque méthode = un nœud
        int n = realMethods.size();
        boolean[][] connected = new boolean[n][n];

        // Pour chaque méthode, liste des attributs accédés
        List<Set<String>> methodFields = new ArrayList<>();
        for (MethodDeclaration m : realMethods) {
            Set<String> accessed = new HashSet<>();
            for (String field : fieldNames) {
                if (m.toString().contains(field)) { // Simple, mais efficace pour un premier jet
                    accessed.add(field);
                }
            }
            methodFields.add(accessed);
        }

        // Deux méthodes sont liées si elles accèdent à au moins un attribut commun
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                Set<String> intersection = new HashSet<>(methodFields.get(i));
                intersection.retainAll(methodFields.get(j));
                if (!intersection.isEmpty()) {
                    connected[i][j] = true;
                    connected[j][i] = true;
                }
            }
        }

        // Calcul du nombre de composantes connexes (BFS)
        boolean[] visited = new boolean[n];
        int components = 0;
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                components++;
                Queue<Integer> queue = new LinkedList<>();
                queue.add(i);
                visited[i] = true;
                while (!queue.isEmpty()) {
                    int curr = queue.poll();
                    for (int j = 0; j < n; j++) {
                        if (connected[curr][j] && !visited[j]) {
                            visited[j] = true;
                            queue.add(j);
                        }
                    }
                }
            }
        }
        return n == 0 ? 0 : components;
    }
}