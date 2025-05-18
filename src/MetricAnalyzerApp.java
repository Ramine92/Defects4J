import analyzers.*;
import com.opencsv.CSVWriter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class MetricAnalyzerApp {
    private static final String DEFECTS4J_PATH = "/home/ramine001/defects4j/framework/bin/defects4j";
    private static final String PROJECTS_PATH = "/home/ramine001/defects4j/framework/projects"; // where Lang, Math, etc. will be checked out
    private static final String[] PROJECTS = {"Lang", "Math"};
    private static final String[] VERSIONS = {"b", "f"}; // buggy and fixed

    public static void main(String[] args) throws Exception {
        File outputFile = new File("output/results.csv");
        outputFile.getParentFile().mkdirs();

        try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile))) {
            // Header
            String[] header = {
                    "project", "version","classes",
                    "lignes_code", "lignes_comm", "nb_methodes", "nb_interfaces",
                    "nb_sous_classes", "nb_classes_abstract", "jax_nb_methodes_abstraites",
                    "exceptions_try_catch", "exceptions_checked", "exceptions_unchecked", "exceptions_declared"
            };
            writer.writeNext(header);

            for (String project : PROJECTS) {
                for (int i = 1; i <= 1; i++) { // example with versions 1 to 5
                    for (String v : VERSIONS) {
                        String pid = project + "-" + i;
                        String version = v.equals("b") ? "buggy" : "fixed";
                        System.out.println("Checking out " + pid + " (" + version + ")...");

                        Path checkoutPath = Paths.get(PROJECTS_PATH, pid + "-" + version);
                        if (!checkoutProject(pid, v, checkoutPath)) {
                            System.err.println("Failed to checkout " + pid + "-" + version + ", skipping.");
                            continue;
                        }
                        Map<String, String> metrics = new HashMap<>();
                        metrics.put("project", pid);
                        metrics.put("version", version);

                        JAXAnalyzer jax = new JAXAnalyzer(checkoutPath);
                        ExceptionAnalyzer ex = new ExceptionAnalyzer(checkoutPath);

                        metrics.putAll(jax.analyze());
                        metrics.putAll(ex.analyze());

                        writer.writeNext(toCSVRow(header, metrics));
                    }
                }
            }
        }
    }

    private static boolean checkoutProject(String pid, String version, Path path) throws IOException, InterruptedException {
        if (Files.exists(path)) return true;

        Process process = new ProcessBuilder(
                DEFECTS4J_PATH, "checkout",
                "-p", pid.split("-")[0],
                "-v", pid.split("-")[1] + version,
                "-w", path.toString()
        ).inheritIO().start();

        int exitCode = process.waitFor();
        return exitCode == 0;
    }


    private static String[] toCSVRow(String[] header, Map<String, String> metrics) {
        String[] row = new String[header.length];
        for (int i = 0; i < header.length; i++) {
            row[i] = metrics.getOrDefault(header[i], "0");
        }
        return row;
    }
}

