package ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import knowledge.Event;
import knowledge.EventType;

public final class EconomicCalendarIngestionService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public List<Event> parseCsv(Path csvPath) {
        try {
            List<String> lines = Files.readAllLines(csvPath);
            List<Event> events = new ArrayList<>();
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("timestamp")) {
                    continue;
                }

                String[] parts = line.split(",", 2);
                if (parts.length != 2) {
                    continue;
                }

                LocalDateTime dateTime = LocalDateTime.parse(parts[0].trim(), FORMATTER);
                Instant timestamp = dateTime.toInstant(ZoneOffset.UTC);
                events.add(new Event(java.util.UUID.randomUUID(), EventType.MACRO, timestamp, parts[1].trim()));
            }
            return events;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse economic calendar CSV at " + csvPath, exception);
        }
    }
}
