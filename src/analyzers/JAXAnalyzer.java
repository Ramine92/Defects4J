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

    public Map<String, String> analyze() throws IOException {
        Map<String, String> result = new HashMap<>();
        List<String> analyzedClasses = new ArrayList<>();
        int totalLines = 0;
        int commentLines = 0;
        int methodCount = 0;
        int interfaceCount = 0;
        int subclassCount = 0;
        int abstractClassCount = 0;
        int abstractMethodCount = 0;

        JavaParser parser = new JavaParser();

        try (Stream<Path> paths = Files.walk(projectPath)) {
            List<Path> javaFiles = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            for (Path javaFile : javaFiles) {
                analyzedClasses.add(javaFile.getFileName().toString());
                try {
                    ParseResult<CompilationUnit> resultCU = parser.parse(javaFile);
                    if (!resultCU.isSuccessful() || resultCU.getResult().isEmpty()) {
                        continue;
                    }

                    CompilationUnit cu = resultCU.getResult().get();

                    totalLines += cu.getRange()
                            .map(r -> r.end.line - r.begin.line + 1)
                            .orElse(0);

                    commentLines += cu.getAllComments().size();

                    List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
                    methodCount += methods.size();

                    List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
                    for (ClassOrInterfaceDeclaration clazz : classes) {
                        if (clazz.isInterface()) {
                            interfaceCount++;
                        } else {
                            if (clazz.isAbstract()) {
                                abstractClassCount++;
                            }
                            if (!clazz.getExtendedTypes().isEmpty()) {
                                subclassCount++;
                            }
                        }
                    }

                    for (MethodDeclaration method : methods) {
                        if (method.isAbstract()) {
                            abstractMethodCount++;
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        result.put("lignes_code", String.valueOf(totalLines));
        result.put("lignes_comm", String.valueOf(commentLines));
        result.put("classes",String.valueOf(analyzedClasses));
        result.put("nb_methodes", String.valueOf(methodCount));
        result.put("nb_interfaces", String.valueOf(interfaceCount));
        result.put("nb_sous_classes", String.valueOf(subclassCount));
        result.put("nb_classes_abstract", String.valueOf(abstractClassCount));
        result.put("jax_nb_methodes_abstraites", String.valueOf(abstractMethodCount));

        return result;
    }
}
