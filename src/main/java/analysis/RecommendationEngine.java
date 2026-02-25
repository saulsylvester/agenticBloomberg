package analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import knowledge.ImpactDirection;

public final class RecommendationEngine {
    private final HeliosAnalyzer analyzer;
    private final Map<String, String> entitySymbolMap;

    public RecommendationEngine(HeliosAnalyzer analyzer) {
        this.analyzer = analyzer;
        this.entitySymbolMap = defaultSymbolMap();
    }

    public List<TradeRecommendation> recommend(String storyText) {
        return recommend(storyText, "");
    }

    public List<TradeRecommendation> recommend(String storyText, String storySymbolHint) {
        AnalysisReport report = analyzer.analyze(storyText);
        List<TradeRecommendation> recommendations = new ArrayList<>();
        Set<String> usedSymbols = new HashSet<>();

        for (ImpactResult impact : report.rankedImpacts()) {
            String symbol = entitySymbolMap.getOrDefault(
                impact.getEntity().getCanonicalName(),
                syntheticTicker(impact.getEntity().getCanonicalName())
            );
            recommendations.add(new TradeRecommendation(
                impact.getEntity().getCanonicalName(),
                actionFor(impact.getDirection()),
                symbol,
                impact.getScore(),
                impact.getRationale()
            ));
            usedSymbols.add(symbol);

            if (recommendations.size() >= 6) {
                break;
            }
        }

        String normalizedHint = normalizeSymbol(storySymbolHint);
        if (!normalizedHint.isBlank() && !usedSymbols.contains(normalizedHint)) {
            if (recommendations.size() < 6) {
                recommendations.add(buildStoryRecommendation(storyText, normalizedHint));
            }
        }

        return recommendations;
    }

    private static String normalizeSymbol(String symbolHint) {
        if (symbolHint == null) {
            return "";
        }
        String normalized = symbolHint.trim().toUpperCase(Locale.ROOT);
        if ("TSCO".equals(normalized)) {
            return "TSCO.L";
        }
        return normalized;
    }

    private static String actionFor(ImpactDirection direction) {
        return switch (direction) {
            case POSITIVE -> "BUY";
            case NEGATIVE -> "SELL";
            case MIXED -> "WATCH";
        };
    }

    private TradeRecommendation buildStoryRecommendation(String text, String symbol) {
        String action = sentimentFor(text);
        String entityName = resolveEntityFromSymbol(symbol);

        return new TradeRecommendation(
            entityName,
            action,
            symbol,
            0.71,
            "Automatic story-level recommendation based on detected ticker context."
        );
    }

    private String sentimentFor(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        if (containsAny(
            normalized,
            List.of("pulls out", "pull out", "falls", "fall", "cuts", "weak", "slumps", "slump", "loss", "down", "drag", "collapse", "warning")
        )) {
            return "SELL";
        }
        if (containsAny(
            normalized,
            List.of("surge", "jump", "rises", "rise", "beat", "beats", "strong", "gain", "gains", "higher", "up", "lift")
        )) {
            return "BUY";
        }
        return "WATCH";
    }

    private static boolean containsAny(String text, List<String> tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String resolveEntityFromSymbol(String symbol) {
        for (Map.Entry<String, String> entry : entitySymbolMap.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(symbol)) {
                return entry.getKey();
            }
        }
        if (symbol.equals("45GD")) {
            return "John Lewis";
        }
        if (symbol.equals("TSCO.L")) {
            return "Tesco";
        }
        return symbol;
    }

    private static String syntheticTicker(String entityName) {
        return entityName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
    }

    private static Map<String, String> defaultSymbolMap() {
        Map<String, String> map = new HashMap<>();
        map.put("Banks", "BARC.L");
        map.put("Housebuilders", "BDEV.L");
        map.put("Airlines", "IAG.L");
        map.put("Energy Producers", "SHEL.L");
        map.put("Exporters", "ULVR.L");
        map.put("Interest Rates", "UK10Y");
        map.put("Oil", "BRN");
        map.put("Strong GBP", "GBPUSD");
        return map;
    }
}
