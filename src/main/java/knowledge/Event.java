package knowledge;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Event {
    private final UUID id;
    private final EventType type;
    private final Instant timestamp;
    private final String rawText;

    public Event(UUID id, EventType type, Instant timestamp, String rawText) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.rawText = Objects.requireNonNull(rawText, "rawText");
    }

    public UUID getId() {
        return id;
    }

    public EventType getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getRawText() {
        return rawText;
    }
}
