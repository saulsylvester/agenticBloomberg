package analysis;

import knowledge.Entity;
import knowledge.ImpactDirection;

public final class ImpactResult {
    private final Entity entity;
    private final ImpactDirection direction;
    private final double score;
    private final String rationale;

    public ImpactResult(Entity entity, ImpactDirection direction, double score, String rationale) {
        this.entity = entity;
        this.direction = direction;
        this.score = score;
        this.rationale = rationale;
    }

    public Entity getEntity() {
        return entity;
    }

    public ImpactDirection getDirection() {
        return direction;
    }

    public double getScore() {
        return score;
    }

    public String getRationale() {
        return rationale;
    }
}
