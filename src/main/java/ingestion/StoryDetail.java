package ingestion;

public record StoryDetail(
    String id,
    String title,
    String body,
    String url,
    String publishedAt,
    String source,
    String suggestedSymbol
) {
}
