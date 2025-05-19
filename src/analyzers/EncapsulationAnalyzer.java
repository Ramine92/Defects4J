package analyzers;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Analyzer that calculates encapsulation metrics for Java classes.
 */
public class EncapsulationAnalyzer {

    private final Path projectPath;

    public EncapsulationAnalyzer(Path projectPath) {
        this.projectPath = projectPath;
    }

    public Map<String, String> analyze() throws IOException {
        List<ClassMetrics> allMetrics = new ArrayList<>();

        Files.walk(projectPath.resolve("src"))
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> {
                    try {
                        Map<String, ClassMetrics> classData = analyzeFile(file.toFile());
                        allMetrics.addAll(classData.values());
                    } catch (IOException e) {
                        System.err.println("Error analyzing file: " + file + " - " + e.getMessage());
                    }
                });

        // Calculate averages
        double avgGlobal = allMetrics.stream()
                .mapToDouble(ClassMetrics::getGlobalEncapsulationRate)
                .average().orElse(1.0);

        double avgField = allMetrics.stream()
                .mapToDouble(ClassMetrics::getFieldEncapsulationRate)
                .average().orElse(1.0);

        double avgMethod = allMetrics.stream()
                .mapToDouble(ClassMetrics::getMethodEncapsulationRate)
                .average().orElse(1.0);

        Map<String, String> result = new HashMap<>();
        result.put("encapsulation_global_avg", String.format(Locale.US, "%.4f", avgGlobal));
        //result.put("encapsulation_field_avg", String.format(Locale.US, "%.4f", avgField));
        //result.put("encapsulation_method_avg", String.format(Locale.US, "%.4f", avgMethod));

        return result;
    }

    private static final Pattern CLASS_PATTERN = Pattern.compile("(?:public|private|protected|\\s)\\s+(?:abstract\\s+)?class\\s+(\\w+)");
    private static final Pattern FIELD_PATTERN = Pattern.compile("\\s+(private|protected|public)\\s+(?:static\\s+|final\\s+)*(?:\\w+(?:<[^>]+>)?)\\s+\\w+\\s*(?:=|;)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("\\s+(private|protected|public)\\s+(?:<[^>]+>\\s+)?(?:static\\s+|final\\s+)*(?:\\w+(?:<[^>]+>)?)\\s+\\w+\\s*\\([^)]*\\)");

    public static Map<String, ClassMetrics> analyzeFile(File file) throws IOException {
        Map<String, ClassMetrics> results = new HashMap<>();
        String content = new String(Files.readAllBytes(file.toPath()));

        Matcher classMatcher = CLASS_PATTERN.matcher(content);

        while (classMatcher.find()) {
            String className = classMatcher.group(1);

            int privateFields = 0, protectedFields = 0, publicFields = 0, totalFields = 0;
            int privateMethods = 0, protectedMethods = 0, publicMethods = 0, totalMethods = 0;

            Matcher fieldMatcher = FIELD_PATTERN.matcher(content);
            while (fieldMatcher.find()) {
                totalFields++;
                String modifier = fieldMatcher.group(1);
                if ("private".equals(modifier)) {
                    privateFields++;
                } else if ("protected".equals(modifier)) {
                    protectedFields++;
                } else if ("public".equals(modifier)) {
                    publicFields++;
                }
            }

            Matcher methodMatcher = METHOD_PATTERN.matcher(content);
            while (methodMatcher.find()) {
                totalMethods++;
                String modifier = methodMatcher.group(1);
                if ("private".equals(modifier)) {
                    privateMethods++;
                } else if ("protected".equals(modifier)) {
                    protectedMethods++;
                } else if ("public".equals(modifier)) {
                    publicMethods++;
                }
            }

            int totalMembers = totalFields + totalMethods;

            double fieldEncapsulationRate = (totalFields == 0) ? 1.0 :
                    (double) (privateFields + protectedFields) / totalFields;

            double methodEncapsulationRate = (totalMethods == 0) ? 1.0 :
                    (double) (privateMethods + protectedMethods) / totalMethods;

            double globalEncapsulationRate = (totalMembers == 0) ? 1.0 :
                    (double) (privateFields + protectedFields + privateMethods + protectedMethods) / totalMembers;

            ClassMetrics metrics = new ClassMetrics(
                    className, file.getPath(),
                    globalEncapsulationRate, fieldEncapsulationRate, methodEncapsulationRate,
                    totalMembers, totalFields, totalMethods
            );

            results.put(className, metrics);
        }

        return results;
    }

    public static class ClassMetrics {
        private final String className;
        private final String filePath;
        private final double globalEncapsulationRate;
        private final double fieldEncapsulationRate;
        private final double methodEncapsulationRate;
        private final int totalMembers;
        private final int totalFields;
        private final int totalMethods;

        public ClassMetrics(String className, String filePath, double globalEncapsulationRate,
                            double fieldEncapsulationRate, double methodEncapsulationRate,
                            int totalMembers, int totalFields, int totalMethods) {
            this.className = className;
            this.filePath = filePath;
            this.globalEncapsulationRate = globalEncapsulationRate;
            this.fieldEncapsulationRate = fieldEncapsulationRate;
            this.methodEncapsulationRate = methodEncapsulationRate;
            this.totalMembers = totalMembers;
            this.totalFields = totalFields;
            this.totalMethods = totalMethods;
        }

        public String getClassName() { return className; }
        public String getFilePath() { return filePath; }
        public double getGlobalEncapsulationRate() { return globalEncapsulationRate; }
        public double getFieldEncapsulationRate() { return fieldEncapsulationRate; }
        public double getMethodEncapsulationRate() { return methodEncapsulationRate; }
        public int getTotalMembers() { return totalMembers; }
        public int getTotalFields() { return totalFields; }
        public int getTotalMethods() { return totalMethods; }
    }
}
