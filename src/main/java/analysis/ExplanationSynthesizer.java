package analysis;

import java.util.List;
import java.util.Locale;
import knowledge.Event;
import knowledge.ImpactDirection;

public final class ExplanationSynthesizer {
    public String synthesize(Event event, List<ImpactResult> rankedImpacts) {
        StringBuilder builder = new StringBuilder();
        builder.append("Event: ").append(event.getRawText()).append('\n');
        builder.append("Classified as: ").append(event.getType()).append('\n');

        if (rankedImpacts.isEmpty()) {
            builder.append("No impacted entities were identified from the current causal map.\n");
            return builder.toString();
        }

        builder.append("\nRanked impacts:\n");
        int rank = 1;
        for (ImpactResult impact : rankedImpacts) {
            builder.append(rank)
                .append(". ")
                .append(impact.getEntity().getCanonicalName())
                .append(": ")
                .append(directionText(impact.getDirection()))
                .append(" (confidence ")
                .append(formatScore(impact.getScore()))
                .append(")")
                .append('\n');
            builder.append("   Channel: ").append(impact.getRationale()).append('\n');
            rank++;
        }

        return builder.toString();
    }

    private static String directionText(ImpactDirection direction) {
        return switch (direction) {
            case POSITIVE -> "positive";
            case NEGATIVE -> "negative";
            case MIXED -> "mixed";
        };
    }

    private static String formatScore(double score) {
        return String.format(Locale.ROOT, "%.2f", score);
    }
}
