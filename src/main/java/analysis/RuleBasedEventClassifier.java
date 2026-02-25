package analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import knowledge.CausalGraph;
import knowledge.Entity;
import knowledge.EventType;

public final class RuleBasedEventClassifier implements EventClassifier {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final List<String> MACRO_KEYWORDS = List.of(
        "cpi", "inflation", "gdp", "unemployment", "pmi", "retail sales"
    );
    private static final List<String> POLICY_KEYWORDS = List.of(
        "bank of england", "federal reserve", "ecb", "rate hike", "rates by", "cuts rates"
    );
    private static final List<String> EARNINGS_KEYWORDS = List.of(
        "earnings", "guidance", "eps", "revenue beat", "quarter"
    );
    private static final List<String> GEOPOLITICAL_KEYWORDS = List.of(
        "tensions", "war", "sanctions", "conflict", "military"
    );

    private final CausalGraph graph;
    private final Map<String, String> aliasToCanonical;

    public RuleBasedEventClassifier(CausalGraph graph, Map<String, String> aliasToCanonical) {
        this.graph = graph;
        this.aliasToCanonical = aliasToCanonical;
    }

    public static RuleBasedEventClassifier fromResources(CausalGraph graph, String aliasResourcePath) {
        try (InputStream inputStream = RuleBasedEventClassifier.class.getResourceAsStream(aliasResourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Alias resource not found: " + aliasResourcePath);
            }
            Map<String, String> rawMap = OBJECT_MAPPER.readValue(inputStream, new TypeReference<>() {
            });

            return new RuleBasedEventClassifier(
                graph,
                rawMap.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        entry -> normalize(entry.getKey()),
                        Map.Entry::getValue
                    ))
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load alias map", exception);
        }
    }

    @Override
    public EventType classify(String rawText) {
        String text = normalize(rawText);

        if (containsAny(text, POLICY_KEYWORDS)) {
            return EventType.POLICY;
        }
        if (containsAny(text, EARNINGS_KEYWORDS)) {
            return EventType.EARNINGS;
        }
        if (containsAny(text, GEOPOLITICAL_KEYWORDS)) {
            return EventType.GEOPOLITICAL;
        }
        if (containsAny(text, MACRO_KEYWORDS)) {
            return EventType.MACRO;
        }
        return EventType.MACRO;
    }

    @Override
    public Set<Entity> extractEntities(String rawText) {
        String normalizedText = normalize(rawText);
        LinkedHashSet<Entity> entities = new LinkedHashSet<>(graph.findEntitiesInText(rawText));

        for (Map.Entry<String, String> entry : aliasToCanonical.entrySet()) {
            if (normalizedText.contains(entry.getKey())) {
                graph.findByCanonicalName(entry.getValue()).ifPresent(entities::add);
            }
        }

        if (normalizedText.contains("cpi") || normalizedText.contains("inflation")) {
            graph.findByCanonicalName("Interest Rates").ifPresent(entities::add);
        }

        return Set.copyOf(entities);
    }

    private static boolean containsAny(String text, List<String> tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).trim();
    }
}
