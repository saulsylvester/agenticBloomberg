package ingestion;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class IngestionScheduler {
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final RssIngestionService rssIngestionService;

    public IngestionScheduler(RssIngestionService rssIngestionService) {
        this.rssIngestionService = rssIngestionService;
    }

    public void scheduleRssPolling(List<URI> sources, Path outputPath, int intervalMinutes) {
        executorService.scheduleAtFixedRate(() -> {
            List<RawEventRecord> records = rssIngestionService.poll(sources);
            rssIngestionService.persist(outputPath, records);
        }, 0, intervalMinutes, TimeUnit.MINUTES);
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
