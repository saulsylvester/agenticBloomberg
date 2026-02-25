package knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CausalGraphLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CausalGraphLoader() {
    }

    public static CausalGraph loadFromResource(String resourcePath) {
        try (InputStream inputStream = CausalGraphLoader.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            GraphDocument document = OBJECT_MAPPER.readValue(inputStream, GraphDocument.class);
            return buildGraph(document);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load causal graph from " + resourcePath, exception);
        }
    }

    private static CausalGraph buildGraph(GraphDocument document) {
        CausalGraph graph = new CausalGraph();

        for (EntityDocument entityDocument : document.entities) {
            Set<String> aliases = new HashSet<>();
            if (entityDocument.aliases != null) {
                aliases.addAll(entityDocument.aliases);
            }
            Entity entity = new Entity(
                stableId(entityDocument.canonicalName),
                EntityType.valueOf(entityDocument.type),
                entityDocument.canonicalName,
                aliases
            );
            graph.addEntity(entity);
        }

        for (LinkDocument linkDocument : document.links) {
            Entity source = graph.findByCanonicalName(linkDocument.source)
                .orElseThrow(() -> new IllegalStateException("Unknown source entity: " + linkDocument.source));
            Entity target = graph.findByCanonicalName(linkDocument.target)
                .orElseThrow(() -> new IllegalStateException("Unknown target entity: " + linkDocument.target));

            graph.addLink(
                source,
                target,
                ImpactDirection.valueOf(linkDocument.direction),
                linkDocument.strength,
                linkDocument.rationale
            );
        }

        return graph;
    }

    private static UUID stableId(String canonicalName) {
        return UUID.nameUUIDFromBytes(canonicalName.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    static final class GraphDocument {
        public List<EntityDocument> entities = new ArrayList<>();
        public List<LinkDocument> links = new ArrayList<>();
    }

    static final class EntityDocument {
        public String canonicalName;
        public String type;
        public List<String> aliases;
    }

    static final class LinkDocument {
        public String source;
        public String target;
        public String direction;
        public double strength;
        public String rationale;
    }
}
