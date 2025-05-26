package analyzers;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

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
                for (ClassOrInterfaceDeclaration clazz : classes) {
                    // Attributs (fields)
                    List<FieldDeclaration> fields = clazz.getFields();
                    int totalFields = fields.size();
                    int privateFields = 0, protectedFields = 0, publicFields = 0, defaultFields = 0;
                    for (FieldDeclaration f : fields) {
                        if (f.isPrivate()) privateFields++;
                        else if (f.isProtected()) protectedFields++;
                        else if (f.isPublic()) publicFields++;
                        else defaultFields++;
                    }

                    // Méthodes
                    List<MethodDeclaration> methods = clazz.getMethods();
                    int totalMethods = methods.size();
                    int privateMethods = 0, protectedMethods = 0, publicMethods = 0, defaultMethods = 0;
                    for (MethodDeclaration m : methods) {
                        if (m.isPrivate()) privateMethods++;
                        else if (m.isProtected()) protectedMethods++;
                        else if (m.isPublic()) publicMethods++;
                        else defaultMethods++;
                    }

                    // Formule 1 : Encapsulation méthodes (protégées + privées)
                    double encapMethods = (totalMethods == 0) ? 1.0 : (double) (privateMethods + protectedMethods) / totalMethods;
                    // Formule 2 : Encapsulation attributs (protégés + privés)
                    double encapFields = (totalFields == 0) ? 1.0 : (double) (privateFields + protectedFields) / totalFields;
                    // Formule 3 : Encapsulation totale
                    int totalMembers = totalFields + totalMethods;
                    double encapTotal = (totalMembers == 0) ? 1.0
                            : (double) ((privateFields + protectedFields) + (privateMethods + protectedMethods)) / totalMembers;

                    metrics.put("encapsulation_rate_methods", String.format(Locale.US, "%.4f", encapMethods));
                    metrics.put("encapsulation_rate_fields", String.format(Locale.US, "%.4f", encapFields));
                    metrics.put("encapsulation_rate_total", String.format(Locale.US, "%.4f", encapTotal));
                    result.put(clazz.getNameAsString(), metrics);
                }
            }
        }
        return result;
    }
}