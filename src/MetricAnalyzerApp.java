import analyzers.*;
import com.opencsv.CSVWriter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class MetricAnalyzerApp {
    private static final String DEFECTS4J_PATH = "/home/ramine001/defects4j/framework/bin/defects4j";
    private static final String PROJECTS_PATH = "/home/ramine001/defects4j/framework/projects";
    private static final String[] PROJECTS = {"Math"};
    private static final String[] VERSIONS = {"b", "f"}; // buggy and fixed

    public static void main(String[] args) throws Exception {
        File outputFile = new File("output/results.csv");
        outputFile.getParentFile().mkdirs();

        try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile))) {
            String[] header = {
                    "project", "version", "class",
                    "lignes_code", "lignes_comm", "nb_methodes", "nb_interfaces",
                    "nb_sous_classes", "nb_classes_abstract", "jax_nb_methodes_abstraites",
                    "exceptions_try_catch", "exceptions_checked", "exceptions_unchecked", "exceptions_declared",
                    "ICU", "ICNU", "ICD", "ICC",
                    "encapsulation_global_avg", "dms_score", "Lcom"
            };
            writer.writeNext(header);

            for (String project : PROJECTS) {
                for (int i = 1; i <= 2; i++) {
                    for (String v : VERSIONS) {
                        String pid = project + "-" + i;
                        String version = v.equals("b") ? "buggy" : "fixed";
                        System.out.println("Checking out " + pid + " (" + version + ")...");

                        Path checkoutPath = Paths.get(PROJECTS_PATH, pid + "-" + version);
                        if (!checkoutProject(pid, v, checkoutPath)) {
                            System.err.println("Failed to checkout " + pid + "-" + version + ", skipping.");
                            continue;
                        }
                        System.out.println("Analyse de " + pid + " (" + version + ") dans " + checkoutPath);

                        // Instancie chaque analyseur avec le checkoutPath courant
                        JAXAnalyzer jax = new JAXAnalyzer(checkoutPath);
                        ExceptionAnalyzer ex = new ExceptionAnalyzer(checkoutPath);
                        EncapsulationAnalyzer en = new EncapsulationAnalyzer(checkoutPath);
                        dmsAnalyzer dms = new dmsAnalyzer(checkoutPath);
                        ImportConflictAnalyzer imp = new ImportConflictAnalyzer(checkoutPath);
                        LCOMAnalyzer lcom = new LCOMAnalyzer(checkoutPath);
                        // Merge des métriques pour CETTE version uniquement
                        Map<String, Map<String, String>> allMetrics = mergeClassMetrics(
                                dms.analyze(),
                                jax.analyze(),
                                ex.analyze(),
                                en.analyze(),
                                imp.analyze(),
                                lcom.analyze()
                        );

                        List<Map<String, String>> rows = new ArrayList<>();

                        for (Map.Entry<String, Map<String, String>> entry : allMetrics.entrySet()) {
                            String className = entry.getKey();
                            Map<String, String> metrics = entry.getValue();
                            metrics.put("project", pid);
                            metrics.put("version", version);
                            metrics.put("class", className);
                            rows.add(metrics);
                            writer.writeNext(toCSVRow(header, metrics));
                        }

                        // --- Calcul AVG pour cette version ---
                        Map<String, Double> sums = new HashMap<>();
                        Map<String, Integer> counts = new HashMap<>();

                        for (Map<String, String> row : rows) {
                            for (String col : header) {
                                if (List.of("project", "version", "class").contains(col)) continue;
                                String valStr = row.get(col);
                                if (valStr != null && !valStr.isBlank()) {
                                    try {
                                        // Remplacer la virgule décimale par un point si besoin
                                        double val = Double.parseDouble(valStr.replace(',', '.'));
                                        sums.put(col, sums.getOrDefault(col, 0.0) + val);
                                        counts.put(col, counts.getOrDefault(col, 0) + 1);
                                    } catch (NumberFormatException e) {
                                        // Log pour debug si valeur non numérique
                                        System.out.println("Impossible de parser la valeur [" + valStr + "] pour " + col + " (classe " + row.get("class") + ")");
                                    }
                                }
                            }
                        }

                        // Log debug : combien de classes et combien de valeurs par métrique
                        System.out.println("Calcul AVG pour " + pid + " - " + version
                                + " (" + rows.size() + " classes)");
                        for (String col : header) {
                            if (List.of("project", "version", "class").contains(col)) continue;
                            System.out.println("  " + col + " : " + counts.getOrDefault(col, 0) + " valeurs");
                        }

                        Map<String, String> avgRow = new HashMap<>();
                        avgRow.put("project", pid);
                        avgRow.put("version", version);
                        avgRow.put("class", "AVG");

                        for (String col : header) {
                            if (List.of("project", "version", "class").contains(col)) continue;
                            int count = counts.getOrDefault(col, 0);
                            if (count > 0) {
                                double avg = sums.get(col) / count;
                                avgRow.put(col, String.format(Locale.US, "%.2f", avg));
                            } else {
                                avgRow.put(col, "");
                            }
                        }

                        writer.writeNext(toCSVRow(header, avgRow));
                        writer.flush();
                    }
                }
            }
        }
    }

    /**
     * Checkout du projet/version dans le dossier spécifié
     */
    private static boolean checkoutProject(String pid, String version, Path path) throws IOException, InterruptedException {
        if (Files.exists(path)) return true;
        System.out.println("Checkout dans " + path);
        Process process = new ProcessBuilder(
                DEFECTS4J_PATH, "checkout",
                "-p", pid.split("-")[0],
                "-v", pid.split("-")[1] + version,
                "-w", path.toString()
        ).inheritIO().start();
        return process.waitFor() == 0;
    }

    /**
     * Retourne un tableau de String pour remplir une ligne CSV
     */
    private static String[] toCSVRow(String[] header, Map<String, String> metrics) {
        String[] row = new String[header.length];
        for (int i = 0; i < header.length; i++) {
            row[i] = metrics.getOrDefault(header[i], "");
        }
        return row;
    }

    /**
     * Fusionne les métriques de plusieurs analyseurs par classe
     */
    private static Map<String, Map<String, String>> mergeClassMetrics(Map<String, Map<String, String>>... maps) {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (Map<String, Map<String, String>> map : maps) {
            for (Map.Entry<String, Map<String, String>> entry : map.entrySet()) {
                result.computeIfAbsent(entry.getKey(), k -> new HashMap<>()).putAll(entry.getValue());
            }
        }
        return result;
    }
}