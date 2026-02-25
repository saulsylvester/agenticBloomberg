package analysis;

import java.util.Set;
import knowledge.Entity;
import knowledge.EventType;

public interface EventClassifier {
    EventType classify(String rawText);

    Set<Entity> extractEntities(String rawText);
}
