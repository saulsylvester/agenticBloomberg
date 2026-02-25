package knowledge;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Entity {
    private final UUID id;
    private final EntityType type;
    private final String canonicalName;
    private final Set<String> aliases;

    public Entity(UUID id, EntityType type, String canonicalName, Set<String> aliases) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.canonicalName = Objects.requireNonNull(canonicalName, "canonicalName");
        this.aliases = Collections.unmodifiableSet(Objects.requireNonNull(aliases, "aliases"));
    }

    public UUID getId() {
        return id;
    }

    public EntityType getType() {
        return type;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public Set<String> getAliases() {
        return aliases;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Entity entity)) {
            return false;
        }
        return id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return canonicalName;
    }
}
