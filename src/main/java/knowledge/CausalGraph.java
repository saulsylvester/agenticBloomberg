package knowledge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CausalGraph {
    private final Map<UUID, Entity> entitiesById = new HashMap<>();
    private final Map<String, Entity> entitiesByNormalizedName = new HashMap<>();
    private final Map<UUID, List<CausalLink>> outgoingLinks = new HashMap<>();

    public void addEntity(Entity entity) {
        entitiesById.put(entity.getId(), entity);
        entitiesByNormalizedName.put(normalize(entity.getCanonicalName()), entity);
        for (String alias : entity.getAliases()) {
            entitiesByNormalizedName.put(normalize(alias), entity);
        }
        outgoingLinks.computeIfAbsent(entity.getId(), ignored -> new ArrayList<>());
    }

    public void addLink(Entity source, Entity target, ImpactDirection direction, double strength, String rationale) {
        CausalLink link = new CausalLink(source, target, direction, strength, rationale);
        outgoingLinks.computeIfAbsent(source.getId(), ignored -> new ArrayList<>()).add(link);
    }

    public List<CausalLink> getOutgoingLinks(Entity source) {
        return Collections.unmodifiableList(outgoingLinks.getOrDefault(source.getId(), List.of()));
    }

    public Optional<Entity> findByCanonicalName(String canonicalName) {
        return Optional.ofNullable(entitiesByNormalizedName.get(normalize(canonicalName)));
    }

    public Set<Entity> findEntitiesInText(String rawText) {
        String normalizedText = normalize(rawText);
        Set<Entity> result = new LinkedHashSet<>();
        for (Map.Entry<String, Entity> entry : entitiesByNormalizedName.entrySet()) {
            String token = entry.getKey();
            if (!token.isBlank() && normalizedText.contains(token)) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    public Collection<Entity> getEntities() {
        return Collections.unmodifiableCollection(entitiesById.values());
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).trim();
    }
}
