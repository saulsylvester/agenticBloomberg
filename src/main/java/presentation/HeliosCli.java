package presentation;

import analysis.AnalysisReport;
import analysis.CausalPropagationEngine;
import analysis.ExplanationSynthesizer;
import analysis.HeliosAnalyzer;
import analysis.RuleBasedEventClassifier;
import knowledge.CausalGraph;
import knowledge.CausalGraphLoader;

public final class HeliosCli {
    private static final int DEFAULT_PORT = 8080;

    private HeliosCli() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (command) {
            case "explain" -> runExplain(args);
            case "serve" -> runServer(args);
            default -> printUsage();
        }
    }

    private static void runExplain(String[] args) {
        if (args.length < 2) {
            printUsage();
            return;
        }
        String headline = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (headline.isBlank()) {
            printUsage();
            return;
        }
        CausalGraph causalGraph = CausalGraphLoader.loadFromResource("/causal_graph.json");
        RuleBasedEventClassifier classifier = RuleBasedEventClassifier.fromResources(causalGraph, "/entity_aliases.json");
        CausalPropagationEngine propagationEngine = new CausalPropagationEngine(causalGraph);
        ExplanationSynthesizer synthesizer = new ExplanationSynthesizer();
        HeliosAnalyzer analyzer = new HeliosAnalyzer(classifier, propagationEngine, synthesizer);

        AnalysisReport report = analyzer.analyze(headline);
        System.out.print(report.formattedExplanation());
    }

    private static void runServer(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                System.err.println("Invalid port: " + args[1] + ". Falling back to " + DEFAULT_PORT + ".");
            }
        }
        HeliosWebServer.start(port);
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  helios explain \"<headline>\"");
        System.out.println("  helios serve [port]");
    }
}
