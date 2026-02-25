package ingestion;

import java.time.Instant;

public record RawEventRecord(String source, String title, String rawText, Instant timestamp) {
}
