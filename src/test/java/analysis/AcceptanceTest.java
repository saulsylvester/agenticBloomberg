package analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Collectors;
import knowledge.CausalGraph;
import knowledge.CausalGraphLoader;
import knowledge.ImpactDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AcceptanceTest {
    private HeliosAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        CausalGraph graph = CausalGraphLoader.loadFromResource("/causal_graph.json");
        RuleBasedEventClassifier classifier = RuleBasedEventClassifier.fromResources(graph, "/entity_aliases.json");
        CausalPropagationEngine propagationEngine = new CausalPropagationEngine(graph);
        ExplanationSynthesizer synthesizer = new ExplanationSynthesizer();
        analyzer = new HeliosAnalyzer(classifier, propagationEngine, synthesizer);
    }

    @Test
    void testRatesHeadlineImpactsBanksAndHousebuilders() {
        AnalysisReport report = analyzer.analyze("Bank of England raises rates by 25bps");

        Map<String, ImpactResult> impactsByEntity = report.rankedImpacts().stream()
            .collect(Collectors.toMap(impact -> impact.getEntity().getCanonicalName(), impact -> impact));

        ImpactResult banks = impactsByEntity.get("Banks");
        ImpactResult housebuilders = impactsByEntity.get("Housebuilders");

        assertNotNull(banks, "Expected Banks impact");
        assertNotNull(housebuilders, "Expected Housebuilders impact");
        assertEquals(ImpactDirection.POSITIVE, banks.getDirection());
        assertEquals(ImpactDirection.NEGATIVE, housebuilders.getDirection());
        assertTrue(report.formattedExplanation().contains("Banks"));
        assertTrue(report.formattedExplanation().contains("Housebuilders"));
    }

    @Test
    void testOilHeadlineImpactsAirlinesAndEnergyProducers() {
        AnalysisReport report = analyzer.analyze("Oil prices surge amid Middle East tensions");

        Map<String, ImpactResult> impactsByEntity = report.rankedImpacts().stream()
            .collect(Collectors.toMap(impact -> impact.getEntity().getCanonicalName(), impact -> impact));

        ImpactResult airlines = impactsByEntity.get("Airlines");
        ImpactResult energy = impactsByEntity.get("Energy Producers");

        assertNotNull(airlines, "Expected Airlines impact");
        assertNotNull(energy, "Expected Energy Producers impact");
        assertEquals(ImpactDirection.NEGATIVE, airlines.getDirection());
        assertEquals(ImpactDirection.POSITIVE, energy.getDirection());
        assertTrue(report.formattedExplanation().contains("Airlines"));
        assertTrue(report.formattedExplanation().contains("Energy Producers"));
    }
}
