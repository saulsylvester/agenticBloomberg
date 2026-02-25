package knowledge;

import java.util.Objects;

public final class CausalLink {
    private final Entity source;
    private final Entity target;
    private final ImpactDirection direction;
    private final double strength;
    private final String rationale;

    public CausalLink(Entity source, Entity target, ImpactDirection direction, double strength, String rationale) {
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        this.direction = Objects.requireNonNull(direction, "direction");
        if (strength < 0.0 || strength > 1.0) {
            throw new IllegalArgumentException("strength must be in range [0, 1]");
        }
        this.strength = strength;
        this.rationale = Objects.requireNonNull(rationale, "rationale");
    }

    public Entity getSource() {
        return source;
    }

    public Entity getTarget() {
        return target;
    }

    public ImpactDirection getDirection() {
        return direction;
    }

    public double getStrength() {
        return strength;
    }

    public String getRationale() {
        return rationale;
    }
}
