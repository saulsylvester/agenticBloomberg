package analysis;

import java.util.List;
import knowledge.Event;

public record AnalysisReport(Event event, List<ImpactResult> rankedImpacts, String formattedExplanation) {
}
