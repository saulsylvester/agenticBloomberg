package analysis;

public record TradeRecommendation(
    String entity,
    String action,
    String suggestedSymbol,
    double confidence,
    String rationale
) {
}
