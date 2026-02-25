package analysis;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import knowledge.Entity;
import knowledge.Event;
import knowledge.EventType;

public final class HeliosAnalyzer {
    private final EventClassifier classifier;
    private final CausalPropagationEngine propagationEngine;
    private final ExplanationSynthesizer synthesizer;

    public HeliosAnalyzer(
        EventClassifier classifier,
        CausalPropagationEngine propagationEngine,
        ExplanationSynthesizer synthesizer
    ) {
        this.classifier = classifier;
        this.propagationEngine = propagationEngine;
        this.synthesizer = synthesizer;
    }

    public AnalysisReport analyze(String rawText) {
        EventType type = classifier.classify(rawText);
        Event event = new Event(UUID.randomUUID(), type, Instant.now(), rawText);
        Set<Entity> extracted = classifier.extractEntities(rawText);
        List<ImpactResult> impacts = propagationEngine.propagate(event, extracted);
        String explanation = synthesizer.synthesize(event, impacts);
        return new AnalysisReport(event, impacts, explanation);
    }
}
