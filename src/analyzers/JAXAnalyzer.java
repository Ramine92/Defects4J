package analyzers;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JAXAnalyzer {
    private final Path projectPath;

    public JAXAnalyzer(Path projectPath) {
        this.projectPath = projectPath;
    }

    public Map<String, Map<String, String>> analyze() throws IOException {
        Map<String, Map<String, String>> result = new HashMap<>();
        JavaParser parser = new JavaParser();

        try (Stream<Path> paths = Files.walk(projectPath)) {
            List<Path> javaFiles = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                ParseResult<CompilationUnit> resultCU = parser.parse(javaFile);
                if (!resultCU.isSuccessful() || resultCU.getResult().isEmpty()) {
                    continue;
                }

                CompilationUnit cu = resultCU.getResult().get();
                String className = javaFile.getFileName().toString().replace(".java", "");
                Map<String, String> metrics = new HashMap<>();

                int totalLines = cu.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0);
                int commentLines = cu.getAllComments().size();
                int methodCount = cu.findAll(MethodDeclaration.class).size();

                int interfaceCount = 0;
                int subclassCount = 0;
                int abstractClassCount = 0;
                int abstractMethodCount = 0;

                List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
                for (ClassOrInterfaceDeclaration clazz : classes) {
                    if (clazz.isInterface()) {
                        interfaceCount++;
                    } else {
                        if (clazz.isAbstract()) abstractClassCount++;
                        if (!clazz.getExtendedTypes().isEmpty()) subclassCount++;
                    }
                }

                for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                    if (method.isAbstract()) abstractMethodCount++;
                }

                metrics.put("lignes_code", String.valueOf(totalLines));
                metrics.put("lignes_comm", String.valueOf(commentLines));
                metrics.put("nb_methodes", String.valueOf(methodCount));
                metrics.put("nb_interfaces", String.valueOf(interfaceCount));
                metrics.put("nb_sous_classes", String.valueOf(subclassCount));
                metrics.put("nb_classes_abstract", String.valueOf(abstractClassCount));
                metrics.put("jax_nb_methodes_abstraites", String.valueOf(abstractMethodCount));

                result.put(className, metrics);
            }
        }

        return result;
    }
}
