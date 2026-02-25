package analysis;

public record ExecutedTrade(
    String tradeId,
    String timestamp,
    String symbol,
    TradeSide side,
    int quantity,
    double price,
    double notional,
    String note,
    String storyTitle,
    double equityAfterTrade,
    double totalPnlAfterTrade
) {
}
