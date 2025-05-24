
package analyzers;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EncapsulationAnalyzer {
    private final Path projectPath;

    public EncapsulationAnalyzer(Path projectPath) {
        this.projectPath = projectPath;
    }

    public Map<String, Map<String, String>> analyze() throws IOException {
        Map<String, Map<String, String>> result = new HashMap<>();
        JavaParser parser = new JavaParser();

        try (Stream<Path> paths = Files.walk(projectPath)) {
            List<Path> javaFiles = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                ParseResult<CompilationUnit> parseResult = parser.parse(file);
                if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) continue;

                CompilationUnit cu = parseResult.getResult().get();
                String className = cu.getPrimaryTypeName().orElse("UnknownClass");
                Map<String, String> metrics = new HashMap<>();

                List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
                int totalFields = 0;
                int privateFields = 0;

                for (ClassOrInterfaceDeclaration clazz : classes) {
                    List<FieldDeclaration> fields = clazz.getFields();
                    totalFields += fields.size();
                    privateFields += (int) fields.stream().filter(f -> f.isPrivate()).count();
                }

                double avg = (totalFields == 0) ? 1.0 : (double) privateFields / totalFields;
                metrics.put("encapsulation_global_avg", String.format(Locale.US, "%.2f", avg));

                result.put(className, metrics);
            }
        }

        return result;
    }
}
