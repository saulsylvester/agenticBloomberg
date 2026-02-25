package ingestion;

public record NewsStory(
    String id,
    String title,
    String summary,
    String url,
    String publishedAt,
    String source,
    String suggestedSymbol
) {
}
