package com.adus.pdf_highlights_extractor;

import org.apache.commons.cli.*;

import java.io.File;
import java.util.Arrays;

public class Main {

    public static void main(String[] args) {
        String[] ioDirPaths = parseCLIArgs(args);
        File inDir = new File(ioDirPaths[0]);
        File outDir = new File(ioDirPaths[1]);

        File[] files = inDir.listFiles(pathname -> pathname.getName().toLowerCase().endsWith(".pdf"));
        if (files == null || files.length == 0) {
            System.out.println("No pdf files found in the specified input directory.");
            return;
        }
        Arrays.stream(files)
                .forEach(file -> {
                    System.out.println("Started processing file: " + file.getName());

                    File outputFile = new File(outDir, file.getName().split("\\.")[0] + "_out.pdf");
                    new HighLightsExtractor(file, outputFile).process();

                    System.out.println("Finished processing file: " + file.getName());
                });
    }

    private static String[] parseCLIArgs(String[] args) {
        Options options = new Options();

        Option input = new Option("i", "input", true, "input directory path");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option("o", "output", true, "output directory path");
        output.setRequired(true);
        options.addOption(output);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);

            String inputFilePath = cmd.getOptionValue("input");
            String outputFilePath = cmd.getOptionValue("output");
            return new String[]{inputFilePath, outputFilePath};

        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("java -jar pdf-highlights-extractor-final.jar -i <input_dir> -o <output_dir>", options);

            System.exit(1);
        }
        return null;
    }
}
