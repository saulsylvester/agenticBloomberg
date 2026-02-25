package analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import knowledge.CausalGraph;
import knowledge.CausalLink;
import knowledge.Entity;
import knowledge.Event;
import knowledge.ImpactDirection;

public final class CausalPropagationEngine {
    private static final int MAX_DEPTH = 2;

    private final CausalGraph graph;

    public CausalPropagationEngine(CausalGraph graph) {
        this.graph = graph;
    }

    public List<ImpactResult> propagate(Event event, Set<Entity> extractedEntities) {
        Map<String, ScoreAccumulator> accumulators = new HashMap<>();

        for (Entity seed : extractedEntities) {
            traverse(seed, 1, 1.0, accumulators);
        }

        List<ImpactResult> results = new ArrayList<>();
        for (ScoreAccumulator accumulator : accumulators.values()) {
            if (accumulator.netScore == 0.0) {
                continue;
            }
            ImpactDirection direction = directionFor(accumulator.positiveScore, accumulator.negativeScore);
            double score = Math.min(1.0, Math.abs(accumulator.netScore));
            results.add(new ImpactResult(accumulator.entity, direction, score, accumulator.primaryRationale));
        }

        results.sort(Comparator.comparingDouble(ImpactResult::getScore).reversed());
        return results;
    }

    private void traverse(Entity source, int depth, double pathStrength, Map<String, ScoreAccumulator> accumulators) {
        if (depth > MAX_DEPTH) {
            return;
        }

        for (CausalLink link : graph.getOutgoingLinks(source)) {
            double confidenceDecay = depth == 1 ? 1.0 : 0.6;
            double confidence = pathStrength * link.getStrength() * confidenceDecay;
            double signedContribution = signedContribution(confidence, link.getDirection());

            ScoreAccumulator accumulator = accumulators.computeIfAbsent(
                link.getTarget().getCanonicalName(),
                ignored -> new ScoreAccumulator(link.getTarget(), link.getRationale())
            );
            accumulator.record(signedContribution, link.getRationale());

            double nextPathStrength = pathStrength * link.getStrength();
            traverse(link.getTarget(), depth + 1, nextPathStrength, accumulators);
        }
    }

    private static double signedContribution(double confidence, ImpactDirection direction) {
        return switch (direction) {
            case POSITIVE -> confidence;
            case NEGATIVE -> -confidence;
            case MIXED -> 0.0;
        };
    }

    private static ImpactDirection directionFor(double positive, double negative) {
        if (positive > 0.0 && negative > 0.0) {
            return ImpactDirection.MIXED;
        }
        if (positive >= negative) {
            return ImpactDirection.POSITIVE;
        }
        return ImpactDirection.NEGATIVE;
    }

    private static final class ScoreAccumulator {
        private final Entity entity;
        private String primaryRationale;
        private double positiveScore;
        private double negativeScore;
        private double netScore;

        private ScoreAccumulator(Entity entity, String rationale) {
            this.entity = entity;
            this.primaryRationale = rationale;
        }

        private void record(double contribution, String rationale) {
            if (Math.abs(contribution) > Math.abs(netScore) && rationale != null && !rationale.isBlank()) {
                primaryRationale = rationale;
            }
            if (contribution >= 0.0) {
                positiveScore += contribution;
            } else {
                negativeScore += -contribution;
            }
            netScore += contribution;
        }
    }
}
