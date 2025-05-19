import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Classe qui implémente la métrique du taux d'encapsulation.
 * Le taux d'encapsulation mesure la proportion d'attributs et de méthodes
 * protégés de l'accès direct (private ou protected).
 */
public class EncapsulationRateMetric {

    // Modèles regex pour l'analyse des fichiers Java
    private static final Pattern CLASS_PATTERN = Pattern.compile("(?:public|private|protected|\\s)\\s+(?:abstract\\s+)?class\\s+(\\w+)");
    private static final Pattern FIELD_PATTERN = Pattern.compile("\\s+(private|protected|public)\\s+(?:static\\s+|final\\s+)*(?:\\w+(?:<[^>]+>)?)\\s+\\w+\\s*(?:=|;)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("\\s+(private|protected|public)\\s+(?:<[^>]+>\\s+)?(?:static\\s+|final\\s+)*(?:\\w+(?:<[^>]+>)?)\\s+\\w+\\s*\\([^)]*\\)");

    /**
     * Analyse un fichier Java et calcule les métriques d'encapsulation
     *
     * @param file Le fichier Java à analyser
     * @return Map contenant les métriques calculées pour chaque classe dans le fichier
     */
    public static Map<String, ClassMetrics> analyzeFile(File file) throws IOException {
        Map<String, ClassMetrics> results = new HashMap<>();
        String content = new String(Files.readAllBytes(file.toPath()));

        // Trouver les classes dans le fichier
        Matcher classMatcher = CLASS_PATTERN.matcher(content);

        while (classMatcher.find()) {
            String className = classMatcher.group(1);

            // Compter les attributs et leurs modificateurs d'accès
            int privateFields = 0;
            int protectedFields = 0;
            int publicFields = 0;
            int totalFields = 0;

            Matcher fieldMatcher = FIELD_PATTERN.matcher(content);
            while (fieldMatcher.find()) {
                String modifier = fieldMatcher.group(1);
                totalFields++;

                if ("private".equals(modifier)) {
                    privateFields++;
                } else if ("protected".equals(modifier)) {
                    protectedFields++;
                } else if ("public".equals(modifier)) {
                    publicFields++;
                }
            }

            // Compter les méthodes et leurs modificateurs d'accès
            int privateMethods = 0;
            int protectedMethods = 0;
            int publicMethods = 0;
            int totalMethods = 0;

            Matcher methodMatcher = METHOD_PATTERN.matcher(content);
            while (methodMatcher.find()) {
                String modifier = methodMatcher.group(1);
                totalMethods++;

                if ("private".equals(modifier)) {
                    privateMethods++;
                } else if ("protected".equals(modifier)) {
                    protectedMethods++;
                } else if ("public".equals(modifier)) {
                    publicMethods++;
                }
            }

            // Calculer les taux d'encapsulation
            double fieldEncapsulationRate = (totalFields == 0) ? 1.0 :
                    (double) (privateFields + protectedFields) / totalFields;

            double methodEncapsulationRate = (totalMethods == 0) ? 1.0 :
                    (double) (privateMethods + protectedMethods) / totalMethods;

            int totalMembers = totalFields + totalMethods;
            double globalEncapsulationRate = (totalMembers == 0) ? 1.0 :
                    (double) (privateFields + protectedFields + privateMethods + protectedMethods) / totalMembers;

            // Créer un objet ClassMetrics pour stocker les résultats
            ClassMetrics metrics = new ClassMetrics(
                    className,
                    file.getPath(),
                    globalEncapsulationRate,
                    fieldEncapsulationRate,
                    methodEncapsulationRate,
                    totalMembers,
                    totalFields,
                    totalMethods
            );

            results.put(className, metrics);
        }

        return results;
    }

    /**
     * Calcule le taux d'encapsulation global pour une classe spécifique
     *
     * @param privateMembers Nombre de membres private
     * @param protectedMembers Nombre de membres protected
     * @param totalMembers Nombre total de membres
     * @return Le taux d'encapsulation entre 0.0 et 1.0
     */
    public static double calculateEncapsulationRate(int privateMembers, int protectedMembers, int totalMembers) {
        if (totalMembers == 0) {
            return 1.0; // Par convention, une classe sans membres est considérée comme parfaitement encapsulée
        }
        return (double) (privateMembers + protectedMembers) / totalMembers;
    }

    /**
     * Classe interne pour stocker les métriques d'une classe
     */
    public static class ClassMetrics {
        private String className;
        private String filePath;
        private double globalEncapsulationRate;
        private double fieldEncapsulationRate;
        private double methodEncapsulationRate;
        private int totalMembers;
        private int totalFields;
        private int totalMethods;

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

        @Override
        public String toString() {
            return String.format(
                    "Class: %s\n" +
                            "File: %s\n" +
                            "Global Encapsulation Rate: %.2f%%\n" +
                            "Field Encapsulation Rate: %.2f%%\n" +
                            "Method Encapsulation Rate: %.2f%%\n" +
                            "Total Members: %d (Fields: %d, Methods: %d)",
                    className, filePath,
                    globalEncapsulationRate * 100,
                    fieldEncapsulationRate * 100,
                    methodEncapsulationRate * 100,
                    totalMembers, totalFields, totalMethods
            );
        }
    }
}